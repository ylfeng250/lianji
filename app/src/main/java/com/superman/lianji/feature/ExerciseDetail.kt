package com.superman.lianji.feature

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.superman.lianji.core.data.AppContainer
import com.superman.lianji.core.data.ExerciseSummary
import com.superman.lianji.core.data.UserSettings
import com.superman.lianji.core.domain.StatSet
import com.superman.lianji.core.domain.WeightUnit
import com.superman.lianji.core.domain.performanceSummary
import com.superman.lianji.core.domain.recentPerformances
import com.superman.lianji.core.ui.ExerciseArtPlayer
import com.superman.lianji.core.ui.LocalSnackbarHostState
import com.superman.lianji.core.ui.Pill
import com.superman.lianji.core.ui.SectionTitle
import com.superman.lianji.core.ui.showInfo
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@Composable
fun ExerciseDetailScreen(container: AppContainer, id: String, onBack: () -> Unit, onOpenWorkout: (String) -> Unit) {
    val rawSummaries by remember { container.exercises.observe("", "", "", "", false) }.collectAsState(initial = emptyList())
    val collator = remember { java.text.Collator.getInstance(java.util.Locale.CHINA) }
    val summaries = remember(rawSummaries) { rawSummaries.sortedWith(compareBy(collator) { it.nameZh }) }
    if (summaries.isEmpty()) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("正在读取动作", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                TextButton(onBack) { Text("返回") }
            }
        }
        return
    }
    ExerciseDetailPager(container, summaries, id, onBack, onOpenWorkout)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExerciseDetailPager(
    container: AppContainer,
    summaries: List<ExerciseSummary>,
    initialId: String,
    onBack: () -> Unit,
    onOpenWorkout: (String) -> Unit,
) {
    val initial = remember(summaries, initialId) {
        summaries.indexOfFirst { it.id == initialId }.let { if (it < 0) 0 else it }
    }
    val pager = rememberPagerState(initialPage = initial, pageCount = { summaries.size })
    val currentId = summaries.getOrNull(pager.currentPage)?.id ?: initialId
    val current by remember(currentId) { container.exercises.observeDetail(currentId) }.collectAsState(initial = null)
    val sets by remember { container.training.observeFlat() }.collectAsState(initial = emptyList())
    val settings by container.settings.settings.collectAsState(initial = UserSettings())
    val routines by remember { container.training.observeRoutines() }.collectAsState(initial = emptyList())
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    var routineSheet by remember { mutableStateOf(false) }

    fun toast(message: String) {
        scope.launch { snackbar?.showInfo(message) }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                title = {
                    Text(
                        current?.nameZh ?: "动作",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = { IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                actions = {
                    IconButton({
                        scope.launch {
                            container.exercises.toggleFavorite(currentId)
                            toast(if (current?.favorite == true) "已取消收藏" else "已收藏")
                        }
                    }) {
                        Icon(
                            if (current?.favorite == true) Icons.Filled.Star else Icons.Filled.StarBorder,
                            if (current?.favorite == true) "取消收藏" else "收藏",
                            tint = if (current?.favorite == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton({ routineSheet = true }, Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlaylistAdd, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("加入计划")
                    }
                    Button(
                        {
                            scope.launch {
                                val rest = container.settings.settings.first().defaultRestSeconds
                                val session = container.training.ensureSession("自由训练")
                                container.training.addExerciseToSession(session, currentId, rest)
                                onOpenWorkout(session)
                            }
                        },
                        Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.FitnessCenter, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("开始训练")
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            HorizontalPager(state = pager, modifier = Modifier.weight(1f).testTag("exercise_pager")) { page ->
                val item = summaries[page]
                ExerciseDetailPage(
                    container = container,
                    summary = item,
                    sets = sets,
                    weightUnit = settings.weightUnit,
                )
            }
            Text(
                "${pager.currentPage + 1} / ${summaries.size} · 左右滑动切换动作",
                Modifier.fillMaxWidth().padding(vertical = 6.dp),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (routineSheet) {
        AddToRoutineSheet(
            routines = routines,
            onPick = { routineId ->
                scope.launch {
                    val rest = container.settings.settings.first().defaultRestSeconds
                    container.training.addExerciseToRoutine(routineId, currentId, rest)
                    routineSheet = false
                    toast("已加入计划")
                }
            },
            onCreate = {
                scope.launch {
                    val rest = container.settings.settings.first().defaultRestSeconds
                    val routineId = container.training.createRoutine("新计划")
                    container.training.addExerciseToRoutine(routineId, currentId, rest)
                    routineSheet = false
                    toast("已新建计划并加入动作")
                }
            },
            onDismiss = { routineSheet = false },
        )
    }
}

@Composable
private fun ExerciseDetailPage(
    container: AppContainer,
    summary: ExerciseSummary,
    sets: List<StatSet>,
    weightUnit: WeightUnit,
) {
    val detail by remember(summary.id) { container.exercises.observeDetail(summary.id) }.collectAsState(initial = null)
    val item = detail
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            ExerciseArtPlayer(
                frames = item?.frames ?: listOf(summary.frame),
                description = "${summary.nameZh}动作图",
                modifier = Modifier.fillMaxWidth().height(300.dp),
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill(summary.muscleZh)
                Pill(summary.equipmentZh)
                Pill(summary.typeZh)
                if (item?.stretch == true) Pill("拉伸")
            }
        }
        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("最近训练", style = MaterialTheme.typography.titleMedium)
                    val performances = remember(sets, summary.id) { recentPerformances(sets, summary.id) }
                    if (performances.isEmpty()) {
                        Text(
                            "还没有记录。练完第一次后，这里会显示最近三次的重量和次数。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    } else {
                        performances.forEach { performance ->
                            Text(performanceSummary(performance, weightUnit), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (item != null && item.secondaryZh.isNotBlank()) Pill("协同：" + item.secondaryZh)
            }
        }
        item { SectionTitle("动作步骤") }
        if (item == null) {
            item { Text("正在读取步骤", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else if (item.steps.isEmpty()) {
            item {
                Text(
                    "这个动作暂时没有文字说明，请结合三帧图示和器械设置练习。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(item.steps.size) { index ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(18.dp),
                    )
                    Text(item.steps[index], style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        item {
            Text(
                if (item != null && item.sourceId != null) {
                    "分步文字来自 free-exercise-db（Unlicense），已译为中文。"
                } else {
                    "说明为项目原创要点（MIT），结合三帧图示参考。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            Text(
                "动作图来自 workout-guide（CC BY-SA 4.0），仅供训练参考，不能替代专业指导。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddToRoutineSheet(
    routines: List<com.superman.lianji.core.database.RoutineEntity>,
    onPick: (String) -> Unit,
    onCreate: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(max = 560.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("加入训练计划", style = MaterialTheme.typography.titleLarge)
            Text(
                "选一个已有计划，或者直接新建。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Button(onCreate, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建计划并加入")
            }
            Spacer(Modifier.height(8.dp))
            routines.forEach { routine ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(routine.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    TextButton({ onPick(routine.id) }) { Text("加入") }
                }
                HorizontalDivider()
            }
            if (routines.isEmpty()) {
                Text(
                    "还没有计划，点上面的按钮建一个。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}
