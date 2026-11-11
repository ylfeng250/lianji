package com.superman.lianji.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.superman.lianji.core.data.AppContainer
import com.superman.lianji.core.data.UserSettings
import com.superman.lianji.core.data.WorkoutDetails
import com.superman.lianji.core.database.SessionEntity
import com.superman.lianji.core.domain.MuscleShare
import com.superman.lianji.core.domain.SessionMarker
import com.superman.lianji.core.domain.StatSet
import com.superman.lianji.core.domain.WeightUnit
import com.superman.lianji.core.domain.countsTowardTraining
import com.superman.lianji.core.domain.exerciseTrend
import com.superman.lianji.core.domain.formatNumber
import com.superman.lianji.core.domain.muscleShares
import com.superman.lianji.core.domain.rangeSummary
import com.superman.lianji.core.domain.volumeKg
import com.superman.lianji.core.domain.weekStart
import com.superman.lianji.core.domain.weeklyPoints
import com.superman.lianji.core.ui.BarChart
import com.superman.lianji.core.ui.EmptyHint
import com.superman.lianji.core.ui.LineChart
import com.superman.lianji.core.ui.LocalSnackbarHostState
import com.superman.lianji.core.ui.SectionTitle
import com.superman.lianji.core.ui.compactDate
import com.superman.lianji.core.ui.SelectionTopBar
import com.superman.lianji.core.ui.StatTile
import com.superman.lianji.core.ui.SwipeAction
import com.superman.lianji.core.ui.SwipeActionRow
import com.superman.lianji.core.ui.longDate
import com.superman.lianji.core.ui.monthLabel
import com.superman.lianji.core.ui.rememberSelectionController
import com.superman.lianji.core.ui.showUndo
import com.superman.lianji.core.ui.shortDate
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

class DataViewModel(private val container: AppContainer) : ViewModel() {
    val history = container.training.observeHistory()
    val markers = container.training.observeSessionMarkers()
    val sets = container.training.observeFlat()
    val bundle = combine(markers, sets) { marker, rows -> marker to rows }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<SessionMarker>() to emptyList<StatSet>())

    fun deleteSession(session: SessionEntity, onDeleted: (WorkoutDetails) -> Unit) = viewModelScope.launch {
        val details = container.training.observeWorkout(session.id).first() ?: return@launch
        container.training.deleteSession(session.id)
        onDeleted(details)
    }

    fun deleteSessions(items: List<SessionEntity>, onDeleted: (List<WorkoutDetails>) -> Unit) = viewModelScope.launch {
        val captured = items.mapNotNull { container.training.observeWorkout(it.id).first() }
        container.training.deleteSessions(items.map { it.id })
        onDeleted(captured)
    }

    fun restore(details: List<WorkoutDetails>) = viewModelScope.launch {
        details.forEach { container.training.restoreSession(it) }
    }
}

private data class SessionStat(val sets: Int, val volumeKg: Double, val minutes: Int)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DataScreen(container: AppContainer, onOpenSession: (String) -> Unit) {
    val model: DataViewModel = viewModel(factory = simpleFactory { DataViewModel(container) })
    val history by model.history.collectAsState(emptyList())
    val bundle by model.bundle.collectAsState()
    val settings by container.settings.settings.collectAsState(initial = UserSettings())
    var tab by rememberSaveable { mutableStateOf(0) }
    var days by rememberSaveable { mutableStateOf(28) }
    var selectedDay by rememberSaveable { mutableStateOf(LocalDate.now().toEpochDay()) }
    var exercise by rememberSaveable { mutableStateOf("") }
    var exerciseMenu by remember { mutableStateOf(false) }
    val selection = rememberSelectionController()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val unitLabel = if (settings.weightUnit == WeightUnit.KG) "公斤" else "磅"
    fun shown(kg: Double) = if (settings.weightUnit == WeightUnit.KG) kg else kg * 2.2046226218

    val (markers, sets) = bundle
    val today = LocalDate.now().toEpochDay()
    val filteredMarkers = markers.filter { days == 0 || it.epochDay >= today - days }
    val filteredSets = sets.filter { days == 0 || it.epochDay >= today - days }
    val summary = rangeSummary(filteredMarkers, filteredSets)
    val weeks = weeklyPoints(filteredMarkers, filteredSets, today, 8)
    val muscles = muscleShares(filteredSets)
    val names = filteredSets.map { it.exerciseId to it.exerciseName }.distinct()
    val chosen = exercise.ifBlank { names.firstOrNull()?.first.orEmpty() }
    val trend = exerciseTrend(filteredSets, chosen)
    val sessionStats = remember(sets) {
        sets.groupBy { it.sessionId }.mapValues { (_, rows) ->
            SessionStat(
                sets = rows.count { countsTowardTraining(it) },
                volumeKg = rows.filter { countsTowardTraining(it) }.sumOf { volumeKg(it) },
                minutes = 0,
            )
        }
    }
    val allHistoryIds = remember(history) { history.map { it.id } }
    BackHandler(selection.active) { selection.clear() }

    fun remove(session: SessionEntity) {
        model.deleteSession(session) { details ->
            scope.launch { snackbar?.showUndo("已删除 ${shortDate(session.localEpochDay)} 的训练") { model.restore(listOf(details)) } }
        }
    }

    fun removeSelected() {
        val items = history.filter { it.id in selection.selected }
        if (items.isEmpty()) return
        model.deleteSessions(items) { details ->
            scope.launch { snackbar?.showUndo("已删除 ${items.size} 条训练记录") { model.restore(details) } }
        }
        selection.clear()
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selection.active) {
                SelectionTopBar(selection, allHistoryIds, onClose = { selection.clear() }) {}
            } else {
                TopAppBar(
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    title = { Text("数据") },
                )
            }
        },
        bottomBar = {
            if (selection.active) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        androidx.compose.material3.Button({ removeSelected() }, Modifier.weight(1f)) {
                            Text("删除 ${selection.count} 条记录")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!selection.active) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(tab == 0, { tab = 0 }, { Text("概览") })
                    FilterChip(tab == 1, { tab = 1 }, { Text("历史") })
                    Spacer(Modifier.weight(1f))
                    Text(
                        if (tab == 1) "长按记录可多选" else "",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (tab == 0) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(7 to "7天", 28 to "28天", 84 to "12周", 0 to "全部").forEach { (value, label) ->
                                FilterChip(days == value, { days = value }, { Text(label) })
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("训练", "${summary.sessions} 次", Modifier.weight(1f))
                            StatTile("完成", "${summary.sets} 组", Modifier.weight(1f))
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            StatTile("容量", "${formatNumber(shown(summary.volumeKg))} $unitLabel", Modifier.weight(1f))
                            StatTile("时长", "${summary.minutes} 分钟", Modifier.weight(1f))
                        }
                    }
                    item {
                        SectionTitle("每周训练次数")
                        BarChart(
                            values = weeks.map { it.sessions.toFloat() },
                            summary = weeksSummary(weeks),
                            labels = weeks.map { compactDate(it.startEpochDay) },
                            minMax = 4f,
                        )
                    }
                    item {
                        SectionTitle("每周训练容量")
                        BarChart(
                            values = weeks.map { shown(it.volumeKg).toFloat() },
                            summary = "容量按完成的非热身组、重量乘以次数计算，单位为$unitLabel。辅助重量不计入。",
                            labels = weeks.map { compactDate(it.startEpochDay) },
                        )
                    }
                    item { SectionTitle("肌群组数"); MuscleBars(muscles) }
                    item {
                        SectionTitle("训练日历")
                        MonthCalendar(history.map { it.localEpochDay }.toSet(), selectedDay) { selectedDay = it }
                    }
                    item { SectionTitle(longDate(selectedDay)) }
                    val daySessions = history.filter { it.localEpochDay == selectedDay }
                    if (daySessions.isEmpty()) {
                        item { Text("这一天没有训练记录。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        items(daySessions, key = { "day-${it.id}" }) { session ->
                            Card(Modifier.fillMaxWidth().clickable { onOpenSession(session.id) }) {
                                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(session.title, style = MaterialTheme.typography.titleMedium)
                                        val stat = sessionStats[session.id]
                                        Text(
                                            "${sessionMinutes(session)} 分钟 · ${stat?.sets ?: 0} 组 · ${formatNumber(shown(stat?.volumeKg ?: 0.0))} $unitLabel",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    Text("详情", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                    if (names.isNotEmpty()) {
                        item {
                            SectionTitle("动作趋势") {
                                Box {
                                    TextButton({ exerciseMenu = true }) {
                                        Text(names.firstOrNull { it.first == chosen }?.second ?: "选择动作")
                                        Icon(Icons.Filled.ArrowDropDown, null)
                                    }
                                    DropdownMenu(exerciseMenu, { exerciseMenu = false }) {
                                        names.forEach { (id, name) ->
                                            DropdownMenuItem(text = { Text(name) }, onClick = { exercise = id; exerciseMenu = false })
                                        }
                                    }
                                }
                            }
                            LineChart(
                                trend.map { shown(it.weightKg ?: 0.0).toFloat() },
                                "折线是该动作每天的最大重量，单位$unitLabel。估算 1RM 使用 Epley 公式，只在 1 到 10 次时显示。最近一次估算：${
                                    trend.mapNotNull { it.epleyKg }.lastOrNull()?.let { formatNumber(shown(it)) + " $unitLabel" } ?: "暂无"
                                }。",
                            )
                        }
                    }
                }
            } else {
                if (history.isEmpty()) {
                    EmptyHint("还没有训练历史", "完成一次训练后，这里会按月份列出每次记录。")
                } else {
                    val grouped = remember(history) { history.groupBy { monthLabel(it.localEpochDay) } }
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        grouped.forEach { (month, sessions) ->
                            item(key = "month-$month") {
                                Text(
                                    month,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
                                )
                            }
                            items(sessions, key = { it.id }) { session ->
                                val stat = sessionStats[session.id]
                                SwipeActionRow(
                                    end = SwipeAction(
                                        label = "删除",
                                        icon = Icons.Filled.Delete,
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                    ) { remove(session) },
                                ) {
                                    Card(
                                        Modifier.fillMaxWidth().testTag("session_row"),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selection.isSelected(session.id)) {
                                                MaterialTheme.colorScheme.primaryContainer
                                            } else {
                                                MaterialTheme.colorScheme.surface
                                            },
                                        ),
                                    ) {
                                        Row(
                                            Modifier
                                                .fillMaxWidth()
                                                .combinedClickable(
                                                    onClick = {
                                                        if (selection.active) selection.toggle(session.id) else onOpenSession(session.id)
                                                    },
                                                    onLongClick = {
                                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        selection.toggle(session.id)
                                                    },
                                                )
                                                .padding(14.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            if (selection.active) Checkbox(selection.isSelected(session.id), { selection.toggle(session.id) })
                                            Column(Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(shortDate(session.localEpochDay), style = MaterialTheme.typography.titleMedium)
                                                    Spacer(Modifier.width(8.dp))
                                                    Text(
                                                        session.title,
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                }
                                                Text(
                                                    "${sessionMinutes(session)} 分钟 · ${stat?.sets ?: 0} 组 · ${formatNumber(shown(stat?.volumeKg ?: 0.0))} $unitLabel",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                            Icon(Icons.Filled.EventNote, "查看记录", tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MuscleBars(items: List<MuscleShare>) {
    if (items.isEmpty()) {
        Text("完成训练后，这里会按主肌群统计组数。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val max = items.maxOf { it.sets }.coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.take(8).forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row {
                    Text(item.muscleZh, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Text("${item.sets} 组", style = MaterialTheme.typography.labelLarge)
                }
                LinearProgressIndicator(
                    progress = { item.sets.toFloat() / max },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)),
                )
            }
        }
        Text(
            "共 ${items.sumOf { it.sets }} 组，条形长度表示相对组数。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MonthCalendar(days: Set<Long>, selected: Long, onSelect: (Long) -> Unit) {
    val month = YearMonth.now()
    val offset = month.atDay(1).dayOfWeek.value - 1
    val today = LocalDate.now().toEpochDay()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${month.year}年${month.monthValue}月", style = MaterialTheme.typography.titleMedium)
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    label,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        val cells = buildList<LocalDate?> {
            repeat(offset) { add(null) }
            for (day in 1..month.lengthOfMonth()) add(month.atDay(day))
        }
        cells.chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    Box(Modifier.weight(1f).aspectRatio(1f), contentAlignment = Alignment.Center) {
                        if (date != null) {
                            val epoch = date.toEpochDay()
                            val trained = epoch in days
                            val isSelected = epoch == selected
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> MaterialTheme.colorScheme.primary
                                            trained -> MaterialTheme.colorScheme.primaryContainer
                                            else -> androidx.compose.ui.graphics.Color.Transparent
                                        },
                                    )
                                    .clickable { onSelect(epoch) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "${date.dayOfMonth}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = when {
                                        isSelected -> MaterialTheme.colorScheme.onPrimary
                                        trained -> MaterialTheme.colorScheme.onPrimaryContainer
                                        else -> MaterialTheme.colorScheme.onSurface
                                    },
                                )
                            }
                            if (epoch == today) {
                                Box(
                                    Modifier
                                        .padding(top = 26.dp)
                                        .size(4.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.tertiary),
                                )
                            }
                        }
                    }
                }
            }
        }
        Text(
            "有底色的是训练过的日子，点一天查看当天记录。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun weeksSummary(weeks: List<com.superman.lianji.core.domain.WeekPoint>): String {
    val total = weeks.sumOf { it.sessions }
    val best = weeks.maxByOrNull { it.sessions }
    return "近 8 周共训练 $total 次。${
        best?.let { if (it.sessions > 0) "最多的一周从 ${shortDate(it.startEpochDay)} 开始，共 ${it.sessions} 次。" else "" } ?: ""
    }"
}

private fun sessionMinutes(session: SessionEntity): Int =
    (((session.endedAtEpochMs ?: session.startedAtEpochMs) - session.startedAtEpochMs).coerceAtLeast(0) / 60000).toInt()
