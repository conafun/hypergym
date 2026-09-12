#Requires -Version 5.1
# ⚠️ 本脚本涉及手环 rpk 的签名方式。签名已定死，禁止修改 —— 详见 SIGNING.md
<#
.SYNOPSIS
    一键产出 HyperGym 双端 debug 包（手环 rpk + 手机 apk）。

.DESCRIPTION
    使用本机 .research/ 内的构建链（该目录被 .gitignore 排除，换机器需重新准备）：
      JDK 21         .research/downloads/jdk21/jdk-21.0.4+7
      Gradle home    .research/gradle-home   （内含 wrapper 需要的 gradle-8.2-bin）
      Android SDK    %LOCALAPPDATA%\Android\Sdk
      手环工具链      miband10pro-trainer\node_modules（aiot-toolkit 2.0.5）

    产物统一复制到 demo-package/ 并固定命名，便于每次都在同一位置取包：
      HyperGym-release.rpk       手环端（aiot release → 正式签名 rpk）
      HyperGym-Phone-debug.apk   手机端（assembleDebug）

    手机端构建前会先跑 tools/sync-band-exercises.js，把手环 src/common/data.js 的
    动作表同步成 BandExercises.kt —— 改了手环动作表，手机端分类会自动跟着变。

.PARAMETER Bump
    构建前先把两端版本号各递增一位：
      手环  miband10pro-trainer/src/manifest.json
              versionName 1.0.64 → 1.0.65，versionCode 64 → 65
      手机  phone-app/version.properties
              3.1.2 → 3.1.3，versionCode 9 → 10

    注意：phone-app/app/build.gradle 在 assembleRelease 时会自己再递增一次，
    所以本脚本只对 debug 构建负责；若之后跑 release，版本会再前进一位。

.PARAMETER NoCopy
    只构建，不复制到 demo-package/。

.PARAMETER ShowLog
    打印完整构建日志（默认只打印末尾若干行，完整日志写入 .research/build-logs/）。

.EXAMPLE
    .\build-debug.ps1
    用当前版本号构建两个 debug 包。

.EXAMPLE
    .\build-debug.ps1 -Bump
    先递增两端版本号，再构建。
#>
[CmdletBinding()]
param(
    [switch]$Bump,
    [switch]$NoCopy,
    [switch]$ShowLog,
    [ValidateSet('both', 'band', 'phone')]
    [string]$Only = 'both'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ============================================================================
# ⚠️ 禁止修改：已登记的小米平台签名证书指纹。
#    手环 rpk 与手机 APK 必须都用这张证书签名，否则 system.interconnect 无法通信
#    （手环与手机会彻底不通，且源码 diff 查不出任何痕迹）。
#    签名相关的文件与设置已经定死，任何版本修改都不要动它。详见 SIGNING.md
# ============================================================================
$EXPECTED_CERT_SHA256 = 'EA:B2:34:44:C0:31:B8:14:AC:DF:37:2E:D0:4C:BC:13:0F:E7:16:C1:31:B1:E7:75:7B:BE:76:32:0C:9A:24:2F'

$root     = $PSScriptRoot
$bandDir  = Join-Path $root 'miband10pro-trainer'
$phoneDir = Join-Path $root 'phone-app'
$pkgDir   = Join-Path $root 'demo-package'
$logDir   = Join-Path $root '.research\build-logs'
$manifestPath    = Join-Path $bandDir 'src\manifest.json'
$versionPropPath = Join-Path $phoneDir 'version.properties'
$jdk        = Join-Path $root '.research\downloads\jdk21\jdk-21.0.4+7'
$gradleHome = Join-Path $root '.research\gradle-home'
$sdk        = Join-Path $env:LOCALAPPDATA 'Android\Sdk'

# ---------------- 工具函数 ----------------

# 写文件不带 BOM：manifest.json / version.properties 都不能被 BOM 污染
function Write-TextNoBom([string]$Path, [string]$Text) {
    [System.IO.File]::WriteAllText($Path, $Text, (New-Object System.Text.UTF8Encoding($false)))
}

function Write-Step([string]$Msg) {
    Write-Host ''
    Write-Host "== $Msg ==" -ForegroundColor Cyan
}

# ---------- 签名证书校验（关键）----------
# interconnect.md §2.1：手环 rpk 必须与手机 APK 用同一张证书签名，否则 system.interconnect
# 无法握手（两个设备直接通不了）。aiot 的 development 模式在找不到 sign/debug、sign/private.pem
# 时会静默回退到 toolkit 自带的调试证书，签名就此不匹配 —— 这个坑必须靠构建前校验堵死。
function Get-PemFingerprint([string]$PemPath) {
    $keytool = Join-Path $jdk 'bin\keytool.exe'
    $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try { $out = (& $keytool -printcert -file $PemPath 2>&1 | Out-String) } finally { $ErrorActionPreference = $prev }
    if ($out -match 'SHA256:\s*([0-9A-Fa-f:]{95})') { return $Matches[1].ToUpperInvariant() }
    return $null
}

function Get-KeystoreFingerprint([string]$Store, [string]$Pass, [string]$Alias) {
    $keytool = Join-Path $jdk 'bin\keytool.exe'
    $prev = $ErrorActionPreference; $ErrorActionPreference = 'Continue'
    try { $out = (& $keytool -list -v -keystore $Store -storepass $Pass -alias $Alias 2>&1 | Out-String) } finally { $ErrorActionPreference = $prev }
    if ($out -match 'SHA256:\s*([0-9A-Fa-f:]{95})') { return $Matches[1].ToUpperInvariant() }
    return $null
}

$script:SigningChecked = $false

function Assert-SigningMatches {
    if ($script:SigningChecked) { return }
    $bandCert = Join-Path $bandDir 'sign\release\certificate.pem'
    if (-not (Test-Path $bandCert)) {
        throw "缺少手环 release 签名证书：$bandCert`n手环 rpk 必须用它签名，否则与手机 APK 签名不一致、无法通信。"
    }
    $ksPropsPath = Join-Path $phoneDir 'keystore.properties'
    if (-not (Test-Path $ksPropsPath)) { throw "缺少 $ksPropsPath，无法校验签名一致性。" }
    $ks = @{}
    Get-Content $ksPropsPath | Where-Object { $_ -match '^\s*[^#\s].*=' } | ForEach-Object {
        $kv = $_ -split '=', 2
        $ks[$kv[0].Trim()] = $kv[1].Trim()
    }
    $storePath = [System.IO.Path]::GetFullPath((Join-Path (Join-Path $phoneDir 'app') $ks['release.store.file']))
    $bandFp  = Get-PemFingerprint $bandCert
    $phoneFp = Get-KeystoreFingerprint $storePath $ks['release.store.password'] $ks['release.key.alias']
    Write-Host "  手环 rpk 证书 : $bandFp" -ForegroundColor DarkGray
    Write-Host "  手机 APK 证书 : $phoneFp" -ForegroundColor DarkGray
    if (-not $bandFp -or -not $phoneFp) { throw '证书指纹读取失败，无法确认签名一致性，已中止构建。' }
    # 白名单校验：即使有人把两边的证书一起换掉，这里也会拦住
    foreach ($p in @(@('手环 rpk', $bandFp), @('手机 APK', $phoneFp))) {
        if ($p[1] -ne $EXPECTED_CERT_SHA256) {
            throw ("签名证书不是约定的那一张，构建已中止！详见 SIGNING.md`n" +
                   "  期望: $EXPECTED_CERT_SHA256`n  $($p[0]): $($p[1])`n" +
                   "签名相关的文件与设置已经定死，请勿修改。")
        }
    }
    if ($bandFp -ne $phoneFp) {
        throw ("签名证书不一致，构建已中止！`n  手环: $bandFp`n  手机: $phoneFp`n" +
               "手环 rpk 与手机 APK 必须用同一张证书签名（interconnect.md §2.1），否则两个设备无法通信。")
    }
    Write-Host '  签名一致性校验通过 ✅' -ForegroundColor Green
    $script:SigningChecked = $true
}

<#
  运行原生命令。
  Windows PowerShell 5.1 的陷阱：$ErrorActionPreference='Stop' 时，原生命令只要往
  stderr 写一行就会被升级成 NativeCommandError 并中止脚本（java -version / gradle /
  npm 都会写 stderr）。这里临时降级为 Continue，改用 $LASTEXITCODE 判断成败。
#>
function Invoke-Native {
    param(
        [Parameter(Mandatory)][string]$Exe,
        [string[]]$Arguments = @(),
        [Parameter(Mandatory)][string]$What,
        [string]$LogFile
    )
    $prev = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    try {
        if ($LogFile) {
            & $Exe @Arguments *>&1 | Tee-Object -FilePath $LogFile | Out-Null
        } else {
            & $Exe @Arguments
        }
        $code = $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }
    if ($null -eq $code) { $code = 0 }
    if ($code -ne 0) { throw "$What 失败，exit=$code" + $(if ($LogFile) { "（完整日志：$LogFile）" } else { '' }) }
}

function Show-Tail([string]$LogFile) {
    if (-not (Test-Path $LogFile)) { return }
    $n = if ($ShowLog) { [int]::MaxValue } else { 12 }
    Get-Content $LogFile -Tail $n | ForEach-Object { Write-Host "    $_" -ForegroundColor DarkGray }
    if (-not $ShowLog) { Write-Host "    …（完整日志：$LogFile）" -ForegroundColor DarkGray }
}

# ---------------- 1. 环境自检 ----------------

Write-Step '环境自检'
$required = [ordered]@{
    'JDK 21'       = $jdk
    'Gradle home'  = $gradleHome
    'Android SDK'  = $sdk
    '手环端目录'     = $bandDir
    '手机端目录'     = $phoneDir
    'aiot-toolkit' = (Join-Path $bandDir 'node_modules\@aiot-toolkit')
}
foreach ($kv in $required.GetEnumerator()) {
    if (-not (Test-Path $kv.Value)) {
        throw "缺少构建依赖 [$($kv.Key)]：$($kv.Value)`n若 .research/ 丢失，需重新准备本机构建链。"
    }
    Write-Host ("  [OK] {0,-12} {1}" -f $kv.Key, $kv.Value) -ForegroundColor DarkGray
}

$env:JAVA_HOME        = $jdk
$env:GRADLE_USER_HOME = $gradleHome
$env:ANDROID_HOME     = $sdk
$env:ANDROID_SDK_ROOT = $sdk

$prevEap = $ErrorActionPreference
$ErrorActionPreference = 'Continue'
$javaVer = (& (Join-Path $jdk 'bin\java.exe') -version 2>&1 | Select-Object -First 1)
$ErrorActionPreference = $prevEap
Write-Host "  java: $javaVer" -ForegroundColor DarkGray

# ---------------- 2. 版本号 ----------------

function Get-BandVersion {
    param([string]$Path = $manifestPath)
    $raw = Get-Content $Path -Raw
    $name = [regex]::Match($raw, '"versionName"\s*:\s*"(\d+)\.(\d+)\.(\d+)"')
    $code = [regex]::Match($raw, '"versionCode"\s*:\s*(\d+)')
    if (-not $name.Success -or -not $code.Success) { throw "无法从 manifest.json 解析版本号" }
    [pscustomobject]@{
        Major = [int]$name.Groups[1].Value
        Minor = [int]$name.Groups[2].Value
        Patch = [int]$name.Groups[3].Value
        Code  = [int]$code.Groups[1].Value
    }
}

function Step-BandVersion {
    $v = Get-BandVersion
    $oldName = "$($v.Major).$($v.Minor).$($v.Patch)"
    $newName = "$($v.Major).$($v.Minor).$($v.Patch + 1)"
    $newCode = $v.Code + 1
    $raw = Get-Content $manifestPath -Raw
    $raw = [regex]::Replace($raw, '("versionName"\s*:\s*")\d+\.\d+\.\d+(")', "`${1}$newName`${2}")
    $raw = [regex]::Replace($raw, '("versionCode"\s*:\s*)\d+', "`${1}$newCode")
    Write-TextNoBom $manifestPath $raw
    Write-Host "  手环 manifest.json : $oldName($($v.Code)) → $newName($newCode)" -ForegroundColor Yellow
}

function Get-PhoneVersion {
    param([string]$Path = $versionPropPath)
    $raw = Get-Content $Path -Raw
    $code = [regex]::Match($raw, 'versionCode=(\d+)')
    $maj  = [regex]::Match($raw, 'versionMajor=(\d+)')
    $min  = [regex]::Match($raw, 'versionMinor=(\d+)')
    $pat  = [regex]::Match($raw, 'versionPatch=(\d+)')
    if (-not ($code.Success -and $maj.Success -and $min.Success -and $pat.Success)) {
        throw "无法从 version.properties 解析版本号"
    }
    [pscustomobject]@{
        Major = [int]$maj.Groups[1].Value
        Minor = [int]$min.Groups[1].Value
        Patch = [int]$pat.Groups[1].Value
        Code  = [int]$code.Groups[1].Value
    }
}

function Step-PhoneVersion {
    $v = Get-PhoneVersion
    $oldName = "$($v.Major).$($v.Minor).$($v.Patch)"
    $newName = "$($v.Major).$($v.Minor).$($v.Patch + 1)"
    $newCode = $v.Code + 1
    # 与 Gradle Properties.store 的格式保持一致，整体重写
    $stamp = (Get-Date).ToString('ddd MMM dd HH:mm:ss') + ' CST ' + (Get-Date).ToString('yyyy')
    $content = @(
        '#Auto-incremented by release build'
        "#$stamp"
        "versionCode=$newCode"
        "versionMajor=$($v.Major)"
        "versionMinor=$($v.Minor)"
        "versionPatch=$($v.Patch + 1)"
        ''
    ) -join "`n"
    Write-TextNoBom $versionPropPath $content
    Write-Host "  手机 version.properties : $oldName($($v.Code)) → $newName($newCode)" -ForegroundColor Yellow
}

Write-Step "版本号（-Only $Only）"
$doBand  = ($Only -ne 'phone')
$doPhone = ($Only -ne 'band')
if ($Bump) {
    if ($doBand)  { Step-BandVersion }
    if ($doPhone) { Step-PhoneVersion }
} else {
    $b = Get-BandVersion
    $p = Get-PhoneVersion
    if ($doBand)  { Write-Host "  手环 : $($b.Major).$($b.Minor).$($b.Patch)  (versionCode $($b.Code))" -ForegroundColor DarkGray }
    if ($doPhone) { Write-Host "  手机 : $($p.Major).$($p.Minor).$($p.Patch)  (versionCode $($p.Code))" -ForegroundColor DarkGray }
    Write-Host '  (未加 -Bump，沿用当前版本号)' -ForegroundColor DarkGray
}
$bandVer  = Get-BandVersion
$phoneVer = Get-PhoneVersion

# ---------------- 3. 手环端构建 ----------------

if (-not (Test-Path $logDir)) { New-Item -ItemType Directory -Path $logDir -Force | Out-Null }
$stamp = Get-Date -Format 'yyyyMMdd-HHmmss'

$bandRpk = $null
$phoneApk = $null

if ($doBand) {
    Write-Step '手环端构建 (aiot release → 正式签名 rpk)'
    Assert-SigningMatches
    $bandLog = Join-Path $logDir "$stamp-band.log"
    Push-Location $bandDir
    try {
        # 必须用 release（production 模式）：它才会走 sign/release/ 的正式证书。
        # 用 build（development 模式）会在找不到 sign/debug、sign/private.pem 时
        # 静默回退到 toolkit 自带的调试证书，导致与手机 APK 签名不一致、通信中断。
        Invoke-Native -Exe 'npm' -Arguments @('run', 'release') -What '手环端构建' -LogFile $bandLog
    } finally {
        Pop-Location
    }
    Show-Tail $bandLog
    # 再从日志确认签名用的是项目证书，而不是 toolkit 内置那张
    if (Select-String -Path $bandLog -Pattern 'node_modules.*signature' -Quiet) {
        throw "手环端仍在用 aiot-toolkit 内置调试证书签名，通信会失败。完整日志：$bandLog"
    }
    $bandRpk = Join-Path $bandDir "dist\com.hypergym.release.$($bandVer.Major).$($bandVer.Minor).$($bandVer.Patch).rpk"
    if (-not (Test-Path $bandRpk)) { throw "未找到手环端产物：$bandRpk" }
    Write-Host "  产物: $bandRpk" -ForegroundColor Green
} else {
    Write-Step '手环端构建（已跳过：-Only phone）'
}

# ---------------- 4. 手机端构建 ----------------

if ($doPhone) {
    Write-Step '同步手环动作表 → 手机端'
    # 手环 src/common/data.js 是动作分类的唯一权威来源。这里每次构建前重新生成
    # BandExercises.kt，保证「改了手环动作表，手机 APK 跟着变」，不会两边口径不一致。
    # 生成失败（data.js 结构变了/动作重复/分组非法）会直接中止构建，不会把错数据打进包里。
    $syncScript = Join-Path $root 'tools\sync-band-exercises.js'
    if (-not (Test-Path $syncScript)) { throw "缺少动作表同步脚本：$syncScript" }
    $syncLog = Join-Path $logDir "$stamp-sync.log"
    Invoke-Native -Exe 'node' -Arguments @($syncScript) -What '手环动作表同步' -LogFile $syncLog
    Get-Content $syncLog | Where-Object { $_.Trim() } | ForEach-Object { Write-Host "  $_" -ForegroundColor DarkGray }

    Write-Step '手机端构建 (assembleDebug)'
    $phoneLog = Join-Path $logDir "$stamp-phone.log"
    Push-Location $phoneDir
    try {
        Invoke-Native -Exe '.\gradlew.bat' -Arguments @('assembleDebug', '--console=plain') -What '手机端构建' -LogFile $phoneLog
    } finally {
        Pop-Location
    }
    Show-Tail $phoneLog
    $phoneApk = Join-Path $phoneDir 'app\build\outputs\apk\debug\app-debug.apk'
    if (-not (Test-Path $phoneApk)) { throw "未找到手机端产物：$phoneApk" }
    Write-Host "  产物: $phoneApk" -ForegroundColor Green
} else {
    Write-Step '手机端构建（已跳过：-Only band）'
}

# ---------------- 5. 汇总输出 ----------------

Write-Step '复制到 demo-package/'
if ($NoCopy) {
    Write-Host '  已指定 -NoCopy，跳过复制' -ForegroundColor DarkGray
} else {
    if (-not (Test-Path $pkgDir)) { New-Item -ItemType Directory -Path $pkgDir -Force | Out-Null }
    if ($bandRpk) {
        # 命名沿用原来的 HyperGym-release.rpk：它现在确实是 release 签名包，
        # 再叫 -debug 会误导（这个命名混乱正是上次出错的成因之一）。
        $destRpk = Join-Path $pkgDir 'HyperGym-release.rpk'
        $bak = Join-Path $pkgDir 'HyperGym-release-backup.rpk'
        # 只存档一次、绝不覆盖：滚动备份会把上一次的包冲掉，等于丢掉可回退的旧版本
        if ((Test-Path $destRpk) -and -not (Test-Path $bak)) {
            Copy-Item $destRpk $bak -Force
            Write-Host '  （原有 rpk 已存档为 HyperGym-release-backup.rpk，之后不再覆盖）' -ForegroundColor DarkGray
        }
        Copy-Item $bandRpk $destRpk -Force
        Write-Host ("  {0}  ({1:N1} KB)" -f $destRpk, ((Get-Item $destRpk).Length / 1KB)) -ForegroundColor Green
    }
    if ($phoneApk) {
        $destApk = Join-Path $pkgDir 'HyperGym-Phone-debug.apk'
        Copy-Item $phoneApk $destApk -Force
        Write-Host ("  {0}  ({1:N1} MB)" -f $destApk, ((Get-Item $destApk).Length / 1MB)) -ForegroundColor Green
    }
}

Write-Step '完成'
if ($doBand)  { Write-Host "  手环 release rpk : $($bandVer.Major).$($bandVer.Minor).$($bandVer.Patch) (versionCode $($bandVer.Code))" -ForegroundColor White }
if ($doPhone) { Write-Host "  手机 debug apk   : $($phoneVer.Major).$($phoneVer.Minor).$($phoneVer.Patch) (versionCode $($phoneVer.Code))" -ForegroundColor White }
