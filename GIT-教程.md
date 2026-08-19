# Git 常用操作教程

> 面向不常用 Git 的开发者，以实际场景举例，覆盖**从建库到推送、拉取、分支、回滚**的完整流程。

---

## 目录

1. [核心概念（30 秒看懂）](#一核心概念30-秒看懂)
2. [首次配置](#二首次配置)
3. [新建仓库并关联远程](#三新建仓库并关联远程)
4. [日常开发：暂存 → 提交 → 推送](#四日常开发暂存--提交--推送)
5. [拉取远程代码](#五拉取远程代码)
6. [查看提交历史](#六查看提交历史)
7. [分支操作](#七分支操作)
8. [代码回滚](#八代码回滚)
9. [.gitignore 文件](#九gitignore-文件)
10. [常见问题排查](#十常见问题排查)

---

## 一、核心概念（30 秒看懂）

Git 管理你的文件有 **4 个区域**，代码从左往右流动：

```
工作区（你编辑的文件）
   ↓  git add
暂存区（准备提交的快照）
   ↓  git commit
本地仓库（你的版本历史）
   ↓  git push
远程仓库（GitHub 上的仓库）
```

**记住这个流程：改文件 → add → commit → push，90% 的场景够用了。**

---

## 二、首次配置

装完 Git 后**只需配置一次**，告诉 Git 你是谁：

```bash
# 设置你的用户名（显示在提交记录里）
git config --global user.name "你的名字"

# 设置你的邮箱（和 GitHub 账号一致）
git config --global user.email "你的邮箱@example.com"
```

验证是否配好：

```bash
git config --global user.name
git config --global user.email
```

> `--global` = 对这台电脑所有仓库生效。如果某个仓库想用不同名字，去掉 `--global` 即可，只影响当前仓库。

---

## 三、新建仓库并关联远程

### 方式一：从零开始（本地先有代码）

```bash
# 1. 进入你的项目文件夹
cd /你的项目路径/your-project

# 2. 初始化 Git 仓库（会生成一个 .git 隐藏目录）
git init

# 3. 在 GitHub 上新建一个空仓库（不要勾选 Add README）

# 4. 关联远程仓库（换成你自己的地址）
git remote add origin https://github.com/你的用户名/仓库名.git

# 5. 验证关联是否成功
git remote -v
# 应该看到：
# origin  https://github.com/你的用户名/仓库名.git (fetch)
# origin  https://github.com/你的用户名/仓库名.git (push)
```

### 方式二：先有 GitHub 仓库，再克隆到本地

```bash
# 直接把远程仓库下载到本地（会自动关联远程）
git clone https://github.com/你的用户名/仓库名.git

# 进入项目文件夹
cd 仓库名
```

> 用 `clone` 的好处：远程地址已自动关联，不需要手动 `git remote add`。

---

## 四、日常开发：暂存 → 提交 → 推送

这是你用 Git 最频繁的操作，三步走：

### 第 1 步：查看当前状态

```bash
git status
```

输出示例：
```
Changes not staged for commit:    ← 有文件被修改了，还没加入暂存区
  modified:   index.html
  modified:   style.css

Untracked files:                  ← 有新文件，还没被 Git 追踪
  logo.png
```

### 第 2 步：把文件加入暂存区

```bash
# 加入所有修改和新文件（最常用）
git add .

# 只加指定文件
git add index.html style.css

# 只加某个文件夹
git add src/
```

### 第 3 步：提交（创建一个版本快照）

```bash
git commit -m "简要描述你做了什么改动"
```

示例：
```bash
git commit -m "添加了首页轮播图功能"
git commit -m "修复登录页面的样式错位"
git commit -m "删除了无用的测试文件"
```

> **提交信息的建议：用中文简洁描述做了什么，不要写 "update"、"fix" 这种没意义的词。**

### 第 4 步：推送到 GitHub

```bash
git push -u origin main
```

- `-u` = 首次推送时设置"跟踪"关系，以后直接 `git push` 就行，不用写全。
- `main` = 分支名，现在 GitHub 默认叫 `main`。
- 如果你本地分支叫 `master`：`git push -u origin master`

> **首次推送可能需要在浏览器里登录 GitHub 授权，按提示操作即可。**

**以后改了代码，只需要重复这三步：**

```bash
git add .
git commit -m "这次改了什么"
git push        ← 已经设置过 -u，直接 push 就行
```

---

## 五、拉取远程代码

如果别人（或你在另一台电脑）往 GitHub 推了代码，你需要拉取更新：

### 拉取并合并（最常用）

```bash
git pull origin main
```

> 这一条 = `git fetch`（下载）+ `git merge`（合并），直接把远程的最新代码拉到你本地。

### 只下载，不合并（先看看有没有变化）

```bash
git fetch origin
```

拉下来后可以看看有什么新内容：
```bash
git log HEAD..origin/main --oneline
```

---

## 六、查看提交历史

```bash
# 简洁模式（一行一个提交）
git log --oneline

# 带图形的分支线（很直观）
git log --oneline --graph --all

# 查看最近 5 条
git log --oneline -5

# 看某次提交具体改了什么
git show abc1234
```

输出示例：
```
f3a2b1c  添加了训练记录删除功能
a1b2c3d  优化日记页面布局
d4e5f6g  初始提交：HyperGym 手环训练助手
```

---

## 七、分支操作

分支 = 复制一份代码，在独立的"平行宇宙"里开发，不影响主干。

### 查看分支

```bash
git branch          # 查看本地分支（* 号表示当前分支）
git branch -r       # 查看远程分支
git branch -a       # 查看全部分支（本地 + 远程）
```

### 创建并切换到新分支

```bash
git checkout -b 新分支名

# 示例：开发一个新功能
git checkout -b feature-新功能
```

> 这一条等于两条命令的合体：`git branch 新分支名` + `git checkout 新分支名`

### 在新分支上开发、提交、推送

```bash
# 正常开发、add、commit
git add .
git commit -m "开发了新功能"

# 推送到远程（远程会自动创建同名分支）
git push -u origin feature-新功能
```

### 合并分支（功能开发完，合回主干）

```bash
# 1. 先切换回主分支
git checkout main

# 2. 把新分支的代码合并进来
git merge feature-新功能

# 3. 推送合并后的主分支
git push
```

### 删除分支（合并完就没用了）

```bash
# 删除本地分支
git branch -d feature-新功能

# 删除远程分支
git push origin --delete feature-新功能
```

---

## 八、代码回滚

**这是最需要小心的操作，请仔细看。**

### 场景 1：提交了，还没 push，想撤回这次提交

```bash
# 软撤回：代码保留，只是撤销 commit（改动留在暂存区）
git reset --soft HEAD~1

# 中等撤回：代码保留，撤销 commit + 取消暂存（改动留在工作区）
git reset --hard HEAD~1
```

- `HEAD~1` = 上一次提交，`HEAD~2` = 上两次，以此类推。

### 场景 2：已经 push 了，想把远程也撤回

**⚠️ 危险操作，会影响其他人！**

```bash
# 方法一： revert（推荐，安全）
# 生成一个新的"反向提交"，把刚才的改动撤销掉
git revert HEAD

# 推送到远程
git push

# 方法二： reset（危险，可能丢失代码）
# 先本地回滚
git reset --hard HEAD~1

# 强制推送（覆盖远程历史）
git push --force
```

> **团队协作时永远用 `revert`，不要用 `force`。** `force` 会覆盖别人的提交，容易出事。

### 场景 3：想回到某个特定提交

```bash
# 查看历史，找到目标 commit 的哈希值（前 7 位）
git log --oneline

# 回到那个提交（代码会回滚到那个状态，commit 历史保留）
git reset --hard abc1234
```

### 对比三种 reset 的区别

| 命令 | 工作区代码 | 暂存区 | 提交历史 |
|---|---|---|---|
| `git reset --soft HEAD~1` | ✅ 保留 | ✅ 保留 | ❌ 撤销上一次 commit |
| `git reset --mixed HEAD~1` | ✅ 保留 | ❌ 取消暂存 | ❌ 撤销上一次 commit |
| `git reset --hard HEAD~1` | ❌ 全部删除 | ❌ 全部清空 | ❌ 撤销上一次 commit |

> 不确定用哪个？**默认用 `--soft`**，最安全。

---

## 九、.gitignore 文件

`.gitignore` 告诉 Git "哪些文件/文件夹不要追踪"。

### 创建 .gitignore

在项目根目录创建一个 `.gitignore` 文件，写入规则：

```gitignore
# 编译产物
build/
dist/
*.apk
*.aab

# 缓存
.gradle/
.cache/
node_modules/

# 系统文件
.DS_Store
Thumbs.db
*.log

# 本地配置（含密码的不要提交）
local.properties
.env
```

### 规则语法速查

| 写法 | 含义 | 示例 |
|---|---|---|
| `build/` | 忽略任何 `build` 目录 | `phone-app/app/build/` 被忽略 |
| `*.apk` | 忽略所有 .apk 文件 | 所有 APK 不会被提交 |
| `!important.apk` | 排除上面的规则 | `important.apk` 仍然会被追踪 |
| `docs/` | 忽略 docs 目录下所有内容 | |
| `/temp` | 只忽略根目录的 temp | 子目录的 temp 不受影响 |
| `*.log` | 忽略所有 .log 文件 | |

> **修改 `.gitignore` 后，已经被 Git 追踪的文件不会自动取消追踪**，需要先执行：
> ```bash
> git rm --cached 被忽略的文件
> ```

---

## 十、常见问题排查

### Q1：push 被拒绝，报 "rejected - non-fast-forward"

```bash
# 原因：远程有你本地没有的提交
# 解决：先 pull 再 push
git pull origin main
git push
```

### Q2：merge 冲突（两个人改了同一个文件）

```bash
git pull origin main
# Git 会提示 "CONFLICT (content): Merge conflict in xxx.kt"
# 打开冲突文件，你会看到类似这样的标记：
```

```
<<<<<<< HEAD
你写的代码
=======
别人写的代码
>>>>>>> origin/main
```

> 手动编辑：**保留你需要的，删掉 `<<<<<<<`、`=======`、`>>>>>>>` 标记**，然后：
> ```bash
> git add .
> git commit -m "解决合并冲突"
> git push
> ```

### Q3：不小心提交了大文件 / 敏感文件

```bash
# 把文件从追踪中移除（但本地文件还在）
git rm --cached 你不小心提交的文件

# 加入 .gitignore 防止下次再提交
echo "那个文件" >> .gitignore

git add .
git commit -m "移除不应提交的文件"
git push
```

### Q4：git status 是中文乱码

```bash
git config --global core.quotepath false
```

### Q5：我改了很多文件，只想查看某个文件改了什么

```bash
git diff 文件名
```

### Q6：看某个文件是谁改的、什么时候改的

```bash
git log --oneline -- 文件名
```

### Q7：我想暂存当前的修改，先去修个紧急 bug

```bash
# 保存当前修改（暂存到一个"储藏"里）
git stash

# 去修 bug，改完提交后，再把修改恢复回来
git stash pop
```

---

## 速查卡片（打印贴墙上）

```
git status              查看状态
git add .               暂存所有修改
git commit -m "说明"     提交
git push                推送到远程
git pull origin main    拉取远程更新
git log --oneline -5    查看最近5条提交
git checkout -b 新分支   创建并切换分支
git merge 分支名        合并分支
git reset --soft HEAD~1 撤销上次提交（代码保留）
git revert HEAD         安全撤销（生成反向提交）
```
