package com.superman.lianji.feature

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.superman.lianji.core.data.AppContainer
import com.superman.lianji.core.data.UserSettings
import com.superman.lianji.core.database.SetEntity
import com.superman.lianji.core.domain.WeightUnit
import com.superman.lianji.core.domain.formatNumber
import com.superman.lianji.core.domain.gramsToInput
import com.superman.lianji.core.ui.ExerciseArt
import com.superman.lianji.core.ui.LocalSnackbarHostState
import com.superman.lianji.core.ui.StatTile
import com.superman.lianji.core.ui.longDate
import com.superman.lianji.core.ui.showInfo
import com.superman.lianji.core.ui.showUndo
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    container: AppContainer,
    sessionId: String,
    onBack: () -> Unit,
    onRepeat: (String) -> Unit,
) {
    val details by remember(sessionId) { container.training.observeWorkout(sessionId) }.collectAsState(initial = null)
    val settings by container.settings.settings.collectAsState(initial = UserSettings())
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var deleteConfirm by remember { mutableStateOf(false) }
    val data = details
    if (data == null) {
        Column(Modifier.padding(20.dp)) { Text("正在读取训练记录") }
        return
    }
    val unit = settings.weightUnit
    val workingSets = data.exercises.sumOf { (_, sets) -> sets.count { it.completed && it.setType != "warmup" } }
    val volume = data.exercises.sumOf { (exercise, sets) ->
        sets.filter { it.completed && it.setType != "warmup" }.sumOf { set ->
            if (exercise.trackingType == "assisted_bodyweight") 0.0
            else (set.weightGrams ?: 0) / 1000.0 * (set.reps ?: 0)
        }
    }
    val minutes = (((data.session.endedAtEpochMs ?: data.session.startedAtEpochMs) - data.session.startedAtEpochMs).coerceAtLeast(0) / 60000).toInt()

    fun remove() {
        scope.launch {
            container.training.deleteSession(sessionId)
            onBack()
            snackbar?.showUndo("已删除这次训练记录") { container.training.restoreSession(data) }
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                title = { Text("训练详情") },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton({ deleteConfirm = true }) { Icon(Icons.Filled.Delete, "删除这次训练") }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton({ deleteConfirm = true }, Modifier.weight(1f)) { Text("删除记录") }
                    Button(
                        {
                            scope.launch {
                                val id = container.training.startFromSession(sessionId)
                                if (id == null) snackbar?.showInfo("没有找到这次训练") else onRepeat(id)
                            }
                        },
                        Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Replay, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("再来一次")
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(data.session.title, style = MaterialTheme.typography.titleLarge)
                        Text(
                            longDate(data.session.localEpochDay) + " · " +
                                Instant.ofEpochMilli(data.session.startedAtEpochMs)
                                    .atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA)),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("时长", "$minutes 分钟", Modifier.weight(1f))
                            StatTile("完成组", "$workingSets", Modifier.weight(1f))
                            StatTile("容量", "${formatNumber(volume)} ${if (unit == WeightUnit.KG) "公斤" else "磅"}", Modifier.weight(1f))
                        }
                    }
                }
            }
            if (data.session.notes.isNotBlank()) {
                item { Text("备注：${data.session.notes}", style = MaterialTheme.typography.bodyMedium) }
            }
            if (data.exercises.isEmpty()) {
                item {
                    Text(
                        "这次训练没有动作记录。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            items(data.exercises, key = { it.first.id }) { (exercise, sets) ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ExerciseArt(listOf(exercise.frameName), "${exercise.exerciseName} 动作图", Modifier.size(44.dp))
                            Column(Modifier.weight(1f)) {
                                Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${exercise.muscleZh} · ${sets.count { it.completed }} 组完成",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        sets.forEachIndexed { index, set ->
                            SetLine(index + 1, set, exercise.trackingType, unit)
                        }
                    }
                }
            }
        }
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("删除这次训练？") },
            text = { Text("删除后可以用底部的「撤销」恢复。") },
            confirmButton = {
                TextButton({ deleteConfirm = false; remove() }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton({ deleteConfirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SetLine(index: Int, set: SetEntity, tracking: String, unit: WeightUnit) {
    val kind = when (set.setType) {
        "warmup" -> "热身"
        "drop" -> "递减"
        "failure" -> "力竭"
        else -> null
    }
    val weight = set.weightGrams?.takeIf { it > 0 }?.let {
        gramsToInput(it, unit) + if (unit == WeightUnit.KG) " 公斤" else " 磅"
    }
    val detail = when {
        weight != null && set.reps != null -> "$weight × ${set.reps}"
        set.reps != null -> "${set.reps} 次"
        set.durationMs != null && set.distanceMm != null ->
            "${set.durationMs / 1000} 秒 · ${formatNumber(set.distanceMm / 1_000_000.0)} 公里"
        set.durationMs != null -> "${set.durationMs / 1000} 秒"
        set.distanceMm != null -> "${formatNumber(set.distanceMm / 1_000_000.0)} 公里"
        else -> "—"
    }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("$index", Modifier.width(20.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(detail, style = MaterialTheme.typography.bodyLarge)
        kind?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        if (set.rpe != null) Text("RPE ${set.rpe}", style = MaterialTheme.typography.labelSmall)
        if (!set.completed) Text("未完成", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        if (set.note.isNotBlank()) Text("备注：${set.note}", style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
