package com.superman.lianji.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

fun chineseDate(date: LocalDate = LocalDate.now()): String =
    date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))

fun shortDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))

fun longDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA))

fun monthLabel(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA))

fun compactDate(epochDay: Long): String =
    LocalDate.ofEpochDay(epochDay).format(DateTimeFormatter.ofPattern("M/d", Locale.CHINA))

private fun drawableIds(context: android.content.Context, frames: List<String>): List<Int> =
    frames.map { context.resources.getIdentifier(it, "drawable", context.packageName) }.filter { it != 0 }

@Composable
fun ExerciseArt(frames: List<String>, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ids = remember(frames) { drawableIds(context, frames) }
    if (ids.isEmpty()) {
        Surface(modifier, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {}
        return
    }
    Image(
        painter = painterResource(ids.first()),
        contentDescription = description,
        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
        contentScale = ContentScale.Fit,
        modifier = modifier,
    )
}

/**
 * 详情页的三帧播放器：自动循环播放，点圆点手动切帧，点按钮暂停。
 * 动画由 Compose 明确驱动，不依赖平台 VectorDrawable 的动画能力。
 */
@Composable
fun ExerciseArtPlayer(frames: List<String>, description: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val ids = remember(frames) { drawableIds(context, frames) }
    var playing by rememberSaveable { mutableStateOf(ids.size > 1) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    LaunchedEffect(playing, ids.size) {
        while (playing && ids.size > 1) {
            delay(1100)
            index = (index + 1) % ids.size
        }
    }
    val shown = if (ids.isEmpty()) 0 else index.coerceIn(0, ids.lastIndex)
    Box(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (ids.isEmpty()) {
            Text(
                "动作图缺失",
                Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Crossfade(targetState = shown, animationSpec = tween(380), label = "动作帧") { frame ->
                ids.getOrNull(frame)?.let { id ->
                    Image(
                        painter = painterResource(id),
                        contentDescription = "$description 第 ${frame + 1} 帧",
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                    )
                }
            }
            Row(
                Modifier.align(Alignment.BottomCenter).padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f)) {
                    Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (ids.size > 1) {
                            ids.indices.forEach { dot ->
                                val current = dot == shown
                                Box(
                                    Modifier
                                        .size(if (current) 10.dp else 7.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (current) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                        )
                                        .clickable {
                                            index = dot
                                            playing = false
                                        },
                                )
                            }
                            Spacer(Modifier.width(2.dp))
                        }
                        if (ids.size > 1) {
                            IconButton(onClick = { playing = !playing }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                    if (playing) "暂停动作动画" else "播放动作动画",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                        Text(
                            "${shown + 1}/${ids.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatTile(label: String, value: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    Card(modifier) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                color = if (highlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun BarChart(
    values: List<Float>,
    summary: String,
    modifier: Modifier = Modifier,
    labels: List<String> = emptyList(),
    minMax: Float = 1f,
) {
    val color = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            val max = maxOf(values.maxOrNull() ?: 0f, minMax, 1f)
            val gap = 10.dp.toPx()
            val width = ((size.width - gap * (values.size - 1).coerceAtLeast(0)) / values.size.coerceAtLeast(1)).coerceAtLeast(1f)
            drawLine(track, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
            values.forEachIndexed { index, value ->
                val bar = size.height * (value / max)
                if (bar > 0f) {
                    drawRoundRect(color, Offset(index * (width + gap), size.height - bar), Size(width, bar), CornerRadius(12f, 12f))
                }
            }
        }
        if (labels.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                labels.forEach { label ->
                    Text(
                        label,
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LineChart(values: List<Float>, summary: String, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.fillMaxWidth().height(150.dp)) {
            if (values.size < 2) return@Canvas
            val max = values.maxOrNull()?.coerceAtLeast(1f) ?: 1f
            val path = Path()
            values.forEachIndexed { index, value ->
                val x = size.width * index / (values.size - 1)
                val y = size.height - size.height * (value / max)
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color, style = Stroke(width = 6f, cap = StrokeCap.Round))
        }
        Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        trailing?.invoke()
    }
}
