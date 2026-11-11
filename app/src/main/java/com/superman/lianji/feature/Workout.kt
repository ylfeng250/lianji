package com.superman.lianji.feature

import android.os.SystemClock
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.superman.lianji.core.data.AppContainer
import com.superman.lianji.core.domain.ExercisePerformance
import com.superman.lianji.core.data.SetEdit
import com.superman.lianji.core.data.UserSettings
import com.superman.lianji.core.data.WorkoutDetails
import com.superman.lianji.core.database.SetEntity
import com.superman.lianji.core.database.WorkoutExerciseEntity
import com.superman.lianji.core.domain.RestSnapshot
import com.superman.lianji.core.domain.WeightUnit
import com.superman.lianji.core.domain.countsTowardTraining
import com.superman.lianji.core.domain.displayToGrams
import com.superman.lianji.core.domain.formatNumber
import com.superman.lianji.core.domain.gramsToInput
import com.superman.lianji.core.domain.performanceCompact
import com.superman.lianji.core.domain.recentPerformances
import com.superman.lianji.core.domain.remainingMillis
import com.superman.lianji.core.ui.ExerciseArt
import com.superman.lianji.core.ui.LocalSnackbarHostState
import com.superman.lianji.core.ui.NumberStepper
import com.superman.lianji.core.ui.SwipeAction
import com.superman.lianji.core.ui.SwipeActionRow
import com.superman.lianji.core.ui.showInfo
import com.superman.lianji.core.ui.showUndo
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DraftSet(
    val id: String,
    val kind: String,
    val weight: String,
    val reps: String,
    val durationSeconds: String,
    val distance: String,
    val rpe: String,
    val note: String,
    val completed: Boolean,
)

class WorkoutViewModel(private val container: AppContainer, private val sessionId: String) : ViewModel() {
    val details = container.training.observeWorkout(sessionId)
    val drafts = MutableStateFlow<Map<String, DraftSet>>(emptyMap())
    val settings = container.settings.settings
    private val jobs = mutableMapOf<String, Job>()
    private var unit = WeightUnit.KG

    /** 同一动作在本次训练之前的最近一次表现，用来在动作卡上提示上次练了多少。 */
    val lastPerformances = container.training.observeFlat()
        .map { rows ->
            rows.filter { it.sessionId != sessionId && countsTowardTraining(it) }
                .groupBy { it.exerciseId }
                .mapNotNull { (exerciseId, sets) ->
                    recentPerformances(sets, exerciseId, 1).firstOrNull()?.let { exerciseId to it }
                }
                .toMap()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    init {
        viewModelScope.launch {
            unit = settings.first().weightUnit
            details.collect { workout ->
                val sets = workout?.exercises?.flatMap { it.second }.orEmpty()
                drafts.update { current ->
                    val next = current.filterKeys { id -> sets.any { it.id == id } }.toMutableMap()
                    sets.forEach { set -> if (set.id !in next) next[set.id] = set.toDraft(unit) }
                    next
                }
            }
        }
    }

    fun edit(set: DraftSet) {
        drafts.update { it + (set.id to set) }
        jobs[set.id]?.cancel()
        jobs[set.id] = viewModelScope.launch { delay(250); save(set) }
    }

    fun complete(name: String, restSeconds: Int, set: DraftSet) {
        val next = set.copy(completed = !set.completed)
        drafts.update { it + (set.id to next) }
        viewModelScope.launch { save(next) }
        if (next.completed) viewModelScope.launch { startRest(name, restSeconds) }
    }

    fun addExercise(nav: NavHostController) = nav.navigate("pick/session/$sessionId/-")
    fun replace(nav: NavHostController, exerciseId: String) = nav.navigate("pick/replace/$sessionId/$exerciseId")

    fun addSet(id: String) = viewModelScope.launch { container.training.addSet(id) }

    fun deleteSet(set: SetEntity) = viewModelScope.launch { container.training.deleteSet(set.id) }

    fun restoreSet(set: SetEntity) = viewModelScope.launch { container.training.restoreSet(set) }

    fun deleteExercise(exercise: WorkoutExerciseEntity) = viewModelScope.launch {
        container.training.deleteWorkoutExercise(exercise.id)
    }

    fun restoreExercise(exercise: WorkoutExerciseEntity, sets: List<SetEntity>) = viewModelScope.launch {
        container.training.restoreWorkoutExercise(exercise, sets)
    }

    fun completeAll(exercise: WorkoutExerciseEntity) = viewModelScope.launch {
        container.training.completeAllSets(exercise.id)
    }

    fun move(id: String, delta: Int) = viewModelScope.launch { container.training.moveWorkoutExercise(sessionId, id, delta) }

    fun rename(title: String) = viewModelScope.launch {
        container.training.updateSessionText(sessionId, title, details.first()?.session?.notes.orEmpty())
    }

    fun finish(done: () -> Unit) = viewModelScope.launch {
        container.training.finish(sessionId)
        container.settings.setRest(null)
        done()
    }

    fun discard(done: () -> Unit) = viewModelScope.launch {
        container.training.deleteSession(sessionId)
        container.settings.setRest(null)
        done()
    }

    fun skipRest() = viewModelScope.launch { container.settings.setRest(null) }

    fun adjustRest(delta: Int) = viewModelScope.launch {
        val current = settings.first().rest ?: return@launch
        container.settings.setRest(
            current.copy(
                deadlineElapsedRealtime = current.deadlineElapsedRealtime + delta * 1000L,
                wallDeadlineEpochMs = current.wallDeadlineEpochMs + delta * 1000L,
                durationSeconds = (current.durationSeconds + delta).coerceAtLeast(5),
            ),
        )
    }

    private suspend fun startRest(name: String, seconds: Int) {
        val duration = seconds.coerceIn(5, 3600)
        container.settings.setRest(
            RestSnapshot(
                deadlineElapsedRealtime = SystemClock.elapsedRealtime() + duration * 1000L,
                durationSeconds = duration,
                wallDeadlineEpochMs = System.currentTimeMillis() + duration * 1000L,
                bootCount = container.feedback.bootCount(),
                sessionId = sessionId,
                exerciseName = name,
            ),
        )
    }

    private suspend fun save(set: DraftSet) {
        val seconds = set.durationSeconds.trim().toIntOrNull()
        val duration = if (seconds == null) null else seconds.coerceAtLeast(0) * 1000L
        val distance = set.distance.replace(',', '.').toDoubleOrNull()?.let { (it * 1_000_000).toInt() }
        container.training.updateSet(
            SetEdit(
                id = set.id,
                setType = set.kind,
                reps = set.reps.trim().toIntOrNull(),
                weightGrams = if (set.weight.isBlank()) null else displayToGrams(set.weight, unit),
                durationMs = duration,
                distanceMm = distance,
                rpe = set.rpe.trim().toIntOrNull()?.takeIf { it in 1..10 },
                note = set.note.take(500),
                completed = set.completed,
            ),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutScreen(container: AppContainer, sessionId: String, nav: NavHostController) {
    val model: WorkoutViewModel = viewModel(factory = simpleFactory { WorkoutViewModel(container, sessionId) })
    val workout by model.details.collectAsState(null)
    val drafts by model.drafts.collectAsState()
    val settings by model.settings.collectAsState(initial = UserSettings())
    val lastPerformances by model.lastPerformances.collectAsState()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = SystemClock.elapsedRealtime()
            delay(250)
        }
    }
    val bootCount = remember { container.feedback.bootCount() }
    val rest = settings.rest?.takeIf { it.sessionId == sessionId }
    val remaining = rest?.let { remainingMillis(it, now, System.currentTimeMillis(), bootCount) } ?: 0L
    var sawRunningRest by remember { mutableStateOf(false) }
    LaunchedEffect(remaining, rest?.deadlineElapsedRealtime) {
        if (rest == null) {
            sawRunningRest = false
            return@LaunchedEffect
        }
        if (remaining > 0L) {
            sawRunningRest = true
            return@LaunchedEffect
        }
        if (sawRunningRest) {
            if (settings.soundEnabled) container.feedback.beep()
            if (settings.vibrationEnabled) container.feedback.vibrate()
        }
        sawRunningRest = false
        container.settings.setRest(null)
    }
    val view = LocalView.current
    DisposableEffect(settings.keepScreenOn) {
        view.keepScreenOn = settings.keepScreenOn
        onDispose { view.keepScreenOn = false }
    }

    var renameOpen by remember { mutableStateOf(false) }
    var finishConfirm by remember { mutableStateOf(false) }
    var discardConfirm by remember { mutableStateOf(false) }
    var editingSet by remember { mutableStateOf<SetEntity?>(null) }

    val data = workout
    if (data == null) {
        Column(Modifier.padding(20.dp)) { Text("正在读取训练") }
        return
    }
    val isCompleted = data.session.status == "completed"
    val unit = settings.weightUnit
    val allSets = data.exercises.flatMap { it.second }
    val completedSets = allSets.count { it.completed }
    val volume = data.exercises.sumOf { (exercise, sets) ->
        sets.filter { it.completed && it.setType != "warmup" }.sumOf { set ->
            if (exercise.trackingType == "assisted_bodyweight") 0.0
            else (set.weightGrams ?: 0) / 1000.0 * (set.reps ?: 0)
        }
    }
    val nextExercise = data.exercises.firstOrNull { (_, sets) -> sets.any { !it.completed } }?.first?.exerciseName

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                title = {
                    Text(data.session.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回") } },
                actions = {
                    if (!isCompleted) {
                        IconButton({ renameOpen = true }) { Icon(Icons.Filled.Edit, "重命名") }
                        Box {
                            var menu by remember { mutableStateOf(false) }
                            IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "更多") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("完成本次训练") }, onClick = { menu = false; finishConfirm = true })
                                DropdownMenuItem(text = { Text("放弃并删除") }, onClick = { menu = false; discardConfirm = true })
                            }
                        }
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
                    if (!isCompleted) {
                        OutlinedButton({ model.addExercise(nav) }, Modifier.weight(1f)) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("添加动作")
                        }
                        Button({ finishConfirm = true }, Modifier.weight(1f)) { Text("完成训练") }
                    } else {
                        Button({ nav.popBackStack() }, Modifier.weight(1f)) { Text("返回") }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SummaryChip(if (isCompleted) "状态" else "时长", if (isCompleted) "已完成" else elapsedText(data), Modifier.weight(1f))
                SummaryChip("完成组", "$completedSets/${allSets.size}", Modifier.weight(1f))
                SummaryChip("容量", "${formatNumber(volume)} ${if (unit == WeightUnit.KG) "公斤" else "磅"}", Modifier.weight(1f))
            }
            if (rest != null && remaining > 0) {
                RestBanner(
                    name = rest.exerciseName,
                    remaining = remaining,
                    durationSeconds = rest.durationSeconds,
                    next = nextExercise,
                    onSkip = model::skipRest,
                    onMinus = { model.adjustRest(-15) },
                    onPlus = { model.adjustRest(15) },
                )
            }
            if (data.exercises.isEmpty()) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("还没有动作", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "从动作库挑几个，或者用计划开始。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(12.dp))
                    Button({ model.addExercise(nav) }) { Text("添加动作") }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(data.exercises, key = { it.first.id }) { (exercise, sets) ->
                        ExerciseCard(
                            exercise = exercise,
                            sets = sets.map { it to (drafts[it.id] ?: it.toDraft(unit)) },
                            unit = unit,
                            completed = isCompleted,
                            lastPerformance = lastPerformances[exercise.exerciseId],
                            onAddSet = { model.addSet(exercise.id) },
                            onCompleteSet = { draft -> haptics.performHapticFeedback(HapticFeedbackType.LongPress); model.complete(exercise.exerciseName, exercise.restSeconds, draft) },
                            onEditSet = model::edit,
                            onOpenEditor = { editingSet = it },
                            onDeleteSet = { entity ->
                                model.deleteSet(entity)
                                scope.launch { snackbar?.showUndo("已删除一组") { model.restoreSet(entity) } }
                            },
                            onMoveUp = { model.move(exercise.id, -1) },
                            onMoveDown = { model.move(exercise.id, 1) },
                            onReplace = { model.replace(nav, exercise.id) },
                            onCompleteAll = {
                                model.completeAll(exercise)
                                scope.launch { snackbar?.showInfo("已把 ${exercise.exerciseName} 的组标记为完成") }
                            },
                            onDelete = {
                                model.deleteExercise(exercise)
                                scope.launch {
                                    snackbar?.showUndo("已移除 ${exercise.exerciseName}") { model.restoreExercise(exercise, sets) }
                                }
                            },
                        )
                    }
                    item {
                        OutlinedButton(
                            { model.addExercise(nav) },
                            Modifier.fillMaxWidth(),
                        ) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("添加动作")
                        }
                    }
                }
            }
        }
    }

    if (renameOpen) {
        var title by rememberSaveable { mutableStateOf(data.session.title) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("训练名称") },
            text = {
                OutlinedTextField(title, { title = it.take(60) }, singleLine = true, label = { Text("名称") })
            },
            confirmButton = {
                TextButton({ model.rename(title); renameOpen = false }) { Text("保存") }
            },
            dismissButton = { TextButton({ renameOpen = false }) { Text("取消") } },
        )
    }
    if (finishConfirm) {
        val unfinished = allSets.count { !it.completed }
        AlertDialog(
            onDismissRequest = { finishConfirm = false },
            title = { Text("完成这次训练？") },
            text = {
                Text(
                    if (unfinished > 0) "还有 $unfinished 组没有标记完成，未完成的组不会计入统计。" else "所有组都完成了，记录会保存到历史。",
                )
            },
            confirmButton = {
                TextButton({
                    finishConfirm = false
                    model.finish {
                        scope.launch { snackbar?.showInfo("训练已记录") }
                        nav.popBackStack()
                    }
                }) { Text("完成") }
            },
            dismissButton = { TextButton({ finishConfirm = false }) { Text("继续练") } },
        )
    }
    if (discardConfirm) {
        AlertDialog(
            onDismissRequest = { discardConfirm = false },
            title = { Text("放弃这次训练？") },
            text = { Text("这次训练和已记录的组都会被删除，不能恢复。") },
            confirmButton = {
                TextButton({
                    discardConfirm = false
                    model.discard { nav.popBackStack() }
                }) { Text("放弃", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton({ discardConfirm = false }) { Text("继续练") } },
        )
    }
    val editing = editingSet
    if (editing != null) {
        val draft = drafts[editing.id] ?: editing.toDraft(unit)
        val editingExercise = data.exercises.firstOrNull { (_, sets) -> sets.any { it.id == editing.id } }?.first
        SetEditSheet(
            draft = draft,
            tracking = editingExercise?.trackingType ?: "weight_reps",
            unit = unit,
            onEdit = model::edit,
            onDelete = {
                model.deleteSet(editing)
                editingSet = null
                scope.launch { snackbar?.showUndo("已删除一组") { model.restoreSet(editing) } }
            },
            onDismiss = { editingSet = null },
        )
    }
}

@Composable
private fun SummaryChip(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun RestBanner(
    name: String,
    remaining: Long,
    durationSeconds: Int,
    next: String?,
    onSkip: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("$name 休息", style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        "%02d:%02d".format(remaining / 1000 / 60, remaining / 1000 % 60),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                TextButton({ onMinus() }) { Text("-15") }
                TextButton({ onPlus() }) { Text("+15") }
                Button({ onSkip() }) { Text("跳过") }
            }
            LinearProgressIndicator(
                progress = { (remaining.toFloat() / (durationSeconds * 1000f)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (next != null) {
                Text("下一个：$next", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: WorkoutExerciseEntity,
    sets: List<Pair<SetEntity, DraftSet>>,
    unit: WeightUnit,
    completed: Boolean,
    lastPerformance: ExercisePerformance?,
    onAddSet: () -> Unit,
    onCompleteSet: (DraftSet) -> Unit,
    onEditSet: (DraftSet) -> Unit,
    onOpenEditor: (SetEntity) -> Unit,
    onDeleteSet: (SetEntity) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onReplace: () -> Unit,
    onCompleteAll: () -> Unit,
    onDelete: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val done = sets.count { it.second.completed }
    Card(
        Modifier.fillMaxWidth().testTag("workout_exercise"),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExerciseArt(listOf(exercise.frameName), "${exercise.exerciseName} 动作图", Modifier.size(52.dp))
                Column(Modifier.weight(1f)) {
                    Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (lastPerformance == null) "第一次练这个动作"
                        else performanceCompact(lastPerformance, unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("$done/${sets.size}", style = MaterialTheme.typography.titleMedium)
                    Text("组完成", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "动作菜单") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("上移") }, onClick = { menu = false; onMoveUp() })
                        DropdownMenuItem(text = { Text("下移") }, onClick = { menu = false; onMoveDown() })
                        DropdownMenuItem(text = { Text("替换动作") }, onClick = { menu = false; onReplace() })
                        DropdownMenuItem(text = { Text("全部完成") }, onClick = { menu = false; onCompleteAll() })
                        DropdownMenuItem(text = { Text("移除动作") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            HorizontalDivider()
            sets.forEachIndexed { index, (entity, draft) ->
                SwipeActionRow(
                    end = SwipeAction(
                        label = "删除",
                        icon = Icons.Filled.Delete,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) { onDeleteSet(entity) },
                ) {
                    SetRow(
                        number = index + 1,
                        draft = draft,
                        tracking = exercise.trackingType,
                        unit = unit,
                        enabled = !completed,
                        onEdit = onEditSet,
                        onComplete = { onCompleteSet(draft) },
                        onOpenEditor = { onOpenEditor(entity) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onAddSet, enabled = !completed) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Text("加一组")
                }
                TextButton(onCompleteAll, enabled = !completed) { Text("全部完成") }
                Spacer(Modifier.weight(1f))
                Text("休息 ${exercise.restSeconds} 秒", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SetRow(
    number: Int,
    draft: DraftSet,
    tracking: String,
    unit: WeightUnit,
    enabled: Boolean,
    onEdit: (DraftSet) -> Unit,
    onComplete: () -> Unit,
    onOpenEditor: () -> Unit,
) {
    val weightStep = if (unit == WeightUnit.KG) 2.5 else 5.0
    Row(
        Modifier.fillMaxWidth().testTag("set_row").padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = kindContainer(draft.kind),
            modifier = Modifier.size(32.dp).clickable(enabled = enabled, onClick = onOpenEditor),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number.toString(),
                    style = MaterialTheme.typography.titleSmall,
                    color = kindOnContainer(draft.kind),
                )
            }
        }
        if (tracking == "weight_reps" || tracking == "bodyweight_reps") {
            NumberStepper(
                value = draft.reps,
                label = "次数",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onMinus = { onEdit(draft.copy(reps = stepInt(draft.reps, -1))) },
                onPlus = { onEdit(draft.copy(reps = stepInt(draft.reps, 1))) },
                onEdit = onOpenEditor,
            )
            NumberStepper(
                value = draft.weight,
                label = if (unit == WeightUnit.KG) "重量(公斤)" else "重量(磅)",
                enabled = enabled,
                modifier = Modifier.weight(1.15f),
                onMinus = { onEdit(draft.copy(weight = stepDouble(draft.weight, -weightStep))) },
                onPlus = { onEdit(draft.copy(weight = stepDouble(draft.weight, weightStep))) },
                onEdit = onOpenEditor,
            )
        } else if (tracking == "assisted_bodyweight") {
            NumberStepper(
                value = draft.reps,
                label = "次数",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onMinus = { onEdit(draft.copy(reps = stepInt(draft.reps, -1))) },
                onPlus = { onEdit(draft.copy(reps = stepInt(draft.reps, 1))) },
                onEdit = onOpenEditor,
            )
            NumberStepper(
                value = draft.weight,
                label = if (unit == WeightUnit.KG) "辅助(公斤)" else "辅助(磅)",
                enabled = enabled,
                modifier = Modifier.weight(1.15f),
                onMinus = { onEdit(draft.copy(weight = stepDouble(draft.weight, -weightStep))) },
                onPlus = { onEdit(draft.copy(weight = stepDouble(draft.weight, weightStep))) },
                onEdit = onOpenEditor,
            )
        } else {
            NumberStepper(
                value = draft.durationSeconds,
                label = "时长(秒)",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                onMinus = { onEdit(draft.copy(durationSeconds = stepInt(draft.durationSeconds, -15))) },
                onPlus = { onEdit(draft.copy(durationSeconds = stepInt(draft.durationSeconds, 15))) },
                onEdit = onOpenEditor,
            )
            if (tracking == "distance_duration") {
                NumberStepper(
                    value = draft.distance,
                    label = "距离(公里)",
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    onMinus = { onEdit(draft.copy(distance = stepDouble(draft.distance, -0.5))) },
                    onPlus = { onEdit(draft.copy(distance = stepDouble(draft.distance, 0.5))) },
                    onEdit = onOpenEditor,
                )
            }
        }
        IconButton(onClick = onComplete, enabled = enabled, modifier = Modifier.size(36.dp)) {
            Icon(
                if (draft.completed) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                if (draft.completed) "取消完成" else "完成这一组",
                tint = if (draft.completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetEditSheet(
    draft: DraftSet,
    tracking: String,
    unit: WeightUnit,
    onEdit: (DraftSet) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(max = 600.dp)
                .testTag("set_editor_sheet")
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("编辑这一组", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton({ onDelete() }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            }
            Text("组类型", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("normal" to "普通", "warmup" to "热身", "drop" to "递减", "failure" to "力竭").forEach { (value, label) ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (draft.kind == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.clickable { onEdit(draft.copy(kind = value)) },
                    ) {
                        Text(
                            label,
                            Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            color = if (draft.kind == value) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            if (tracking == "weight_reps" || tracking == "bodyweight_reps" || tracking == "assisted_bodyweight") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        draft.reps,
                        { onEdit(draft.copy(reps = it.filter { c -> c.isDigit() })) },
                        Modifier.weight(1f),
                        label = { Text("次数") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                        ),
                    )
                    OutlinedTextField(
                        draft.weight,
                        { onEdit(draft.copy(weight = it.filter { c -> c.isDigit() || c == '.' || c == ',' })) },
                        Modifier.weight(1f),
                        label = { Text(if (unit == WeightUnit.KG) "重量(公斤)" else "重量(磅)") },
                        singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal,
                        ),
                    )
                }
            }
            if (tracking == "duration" || tracking == "distance_duration") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        draft.durationSeconds,
                        { onEdit(draft.copy(durationSeconds = it.filter { c -> c.isDigit() })) },
                        Modifier.weight(1f),
                        label = { Text("时长(秒)") },
                        singleLine = true,
                    )
                    if (tracking == "distance_duration") {
                        OutlinedTextField(
                            draft.distance,
                            { onEdit(draft.copy(distance = it.filter { c -> c.isDigit() || c == '.' || c == ',' })) },
                            Modifier.weight(1f),
                            label = { Text("距离(公里)") },
                            singleLine = true,
                        )
                    }
                }
            }
            OutlinedTextField(
                draft.rpe,
                { onEdit(draft.copy(rpe = it.filter { c -> c.isDigit() }.take(2))) },
                Modifier.fillMaxWidth(),
                label = { Text("用力程度 RPE（1 到 10，可留空）") },
                singleLine = true,
            )
            OutlinedTextField(
                draft.note,
                { onEdit(draft.copy(note = it.take(500))) },
                Modifier.fillMaxWidth(),
                label = { Text("备注") },
                singleLine = true,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(draft.completed, { onEdit(draft.copy(completed = it)) })
                Text("标记为已完成")
            }
            Button(onDismiss, Modifier.fillMaxWidth()) { Text("完成") }
        }
    }
}

@Composable
private fun kindContainer(kind: String): Color = when (kind) {
    "warmup" -> MaterialTheme.colorScheme.secondaryContainer
    "drop" -> MaterialTheme.colorScheme.tertiaryContainer
    "failure" -> MaterialTheme.colorScheme.errorContainer
    else -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
private fun kindOnContainer(kind: String): Color = when (kind) {
    "warmup" -> MaterialTheme.colorScheme.onSecondaryContainer
    "drop" -> MaterialTheme.colorScheme.onTertiaryContainer
    "failure" -> MaterialTheme.colorScheme.onErrorContainer
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun stepInt(value: String, delta: Int): String {
    val current = value.trim().toIntOrNull() ?: 0
    return (current + delta).coerceAtLeast(0).toString()
}

private fun stepDouble(value: String, delta: Double): String {
    val current = value.trim().replace(',', '.').toDoubleOrNull() ?: 0.0
    return formatNumber((current + delta).coerceAtLeast(0.0))
}

private fun elapsedText(data: WorkoutDetails): String {
    val end = data.session.endedAtEpochMs ?: System.currentTimeMillis()
    val seconds = ((end - data.session.startedAtEpochMs) / 1000).coerceAtLeast(0)
    return if (seconds >= 3600) {
        "%d:%02d:%02d".format(seconds / 3600, seconds % 3600 / 60, seconds % 60)
    } else {
        "%02d:%02d".format(seconds / 60, seconds % 60)
    }
}

private fun SetEntity.toDraft(unit: WeightUnit): DraftSet = DraftSet(
    id = id,
    kind = setType,
    weight = gramsToInput(weightGrams, unit),
    reps = reps?.toString().orEmpty(),
    durationSeconds = durationMs?.let { (it / 1000).toString() }.orEmpty(),
    distance = distanceMm?.takeIf { it > 0 }?.let { formatNumber(it / 1_000_000.0) }.orEmpty(),
    rpe = rpe?.toString().orEmpty(),
    note = note,
    completed = completed,
)
