package com.hypergym.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hypergym.data.Exercise
import com.hypergym.data.ExerciseLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val imageCache = LruCache<String, Bitmap>(200)

/** 动作库页：单页下拉浏览（搜索 + 肌群筛选 + 列表），点动作进详情 */
@Composable
fun ExerciseScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val library = remember { ExerciseLibrary.load(context) }
    var query by remember { mutableStateOf("") }
    var group by remember { mutableStateOf("全部") }
    var selected by remember { mutableStateOf<Exercise?>(null) }

    val current = selected
    if (current != null) {
        ExerciseDetail(current, onBack = { selected = null }, modifier)
    } else {
        val filtered = remember(library, query, group) {
            library.filter { ex ->
                (group == "全部" || ExerciseLibrary.groupOf(ex.category) == group) &&
                    (query.isBlank() || ex.name.contains(query, ignoreCase = true))
            }
        }
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { PageHeader("动作库", "共 ${library.size} 个动作 · 点击查看教学") }
            item { SearchField(query) { query = it } }
            item { GroupChips(group) { group = it } }
            items(filtered, key = { it.id }) { ex ->
                ExerciseItem(ex) { selected = ex }
            }
        }
    }
}

// ---------------- 搜索 / 筛选 ----------------

@Composable
private fun SearchField(query: String, onChange: (String) -> Unit) {
    TextField(
        value = query,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("搜索动作…", color = HColors.TextSecondary, fontSize = 14.sp) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = HColors.TextSecondary) },
        singleLine = true,
        shape = RoundedCornerShape(50),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = HColors.Card,
            unfocusedContainerColor = HColors.Card,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = HColors.Primary,
            focusedTextColor = HColors.TextPrimary,
            unfocusedTextColor = HColors.TextPrimary,
        ),
    )
}

@Composable
private fun GroupChips(selected: String, onSelect: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ExerciseLibrary.GROUPS.forEach { g ->
            val sel = g == selected
            Surface(
                shape = CircleShape,
                color = if (sel) HColors.Primary else HColors.Card,
                shadowElevation = if (sel) 0.dp else 1.dp,
                onClick = { onSelect(g) },
            ) {
                Text(
                    g,
                    Modifier.padding(horizontal = 16.dp, vertical = 7.dp),
                    color = if (sel) HColors.TextPrimary else HColors.TextSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

// ---------------- 动作列表项 ----------------

@Composable
private fun ExerciseItem(ex: Exercise, onClick: () -> Unit) {
    Surface(shape = CardRadius, color = HColors.Card, shadowElevation = 1.dp, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ExerciseThumb(ex.image)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(ex.name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = HColors.TextPrimary, maxLines = 1)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${ExerciseLibrary.groupOf(ex.category)} · ${ExerciseLibrary.zhMuscle(ex.target)} · ${ExerciseLibrary.zhEquipment(ex.equipment)}",
                    fontSize = 12.sp,
                    color = HColors.TextSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ExerciseThumb(imagePath: String) {
    val bmp = rememberAssetBitmap("exercises/$imagePath")
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop,
        )
    } else {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)).background(HColors.Background))
    }
}

// ---------------- 详情 ----------------

@Composable
private fun ExerciseDetail(ex: Exercise, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "‹",
                Modifier.clip(CircleShape).clickable { onBack() }.padding(horizontal = 14.dp, vertical = 2.dp),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = HColors.Primary,
            )
            Text(
                ex.name,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = HColors.TextPrimary,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            BlockCard { AssetVideoPlayer("exercises/${ex.video}", Modifier.fillMaxWidth().aspectRatio(1f)) }
            BlockCard {
                CardTitle("训练信息")
                Spacer(Modifier.height(10.dp))
                MetaRow("部位", ExerciseLibrary.groupOf(ex.category))
                MetaRow("目标肌群", ExerciseLibrary.zhMuscle(ex.target))
                MetaRow("主肌群", ExerciseLibrary.zhMuscle(ex.muscleGroup))
                MetaRow("次要肌群", ex.secondary.joinToString("、") { ExerciseLibrary.zhMuscle(it) }.ifBlank { "—" })
                MetaRow("器材", ExerciseLibrary.zhEquipment(ex.equipment))
            }
            BlockCard {
                CardTitle("分步教学")
                Spacer(Modifier.height(6.dp))
                ex.steps.forEachIndexed { i, step ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                        Surface(shape = CircleShape, color = HColors.PrimaryContainer) {
                            Text(
                                "${i + 1}",
                                Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = HColors.TextPrimary,
                            )
                        }
                        Text(
                            step,
                            Modifier.padding(start = 10.dp),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = HColors.TextPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(72.dp), fontSize = 13.sp, color = HColors.TextSecondary)
        Text(value, Modifier.weight(1f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = HColors.TextPrimary)
    }
}

// ---------------- 资源加载 ----------------

@Composable
private fun rememberAssetBitmap(path: String): Bitmap? {
    val context = LocalContext.current
    var bmp by remember(path) { mutableStateOf<Bitmap?>(imageCache.get(path)) }
    LaunchedEffect(path) {
        if (bmp != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching { context.assets.open(path).use { BitmapFactory.decodeStream(it) } }.getOrNull()
        }
        if (loaded != null) {
            imageCache.put(path, loaded)
            bmp = loaded
        }
    }
    return bmp
}

@Composable
private fun AssetVideoPlayer(assetPath: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(assetPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri("asset:///$assetPath"))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    AndroidView(
        factory = { PlayerView(it).apply { useController = false; this.player = player } },
        modifier = modifier.clip(RoundedCornerShape(14.dp)).background(HColors.Background),
    )
}
