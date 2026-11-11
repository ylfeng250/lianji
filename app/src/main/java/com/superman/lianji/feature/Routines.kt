package com.superman.lianji.feature

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.superman.lianji.core.data.AppContainer
import com.superman.lianji.core.data.RoutineDetails
import com.superman.lianji.core.data.UserSettings
import com.superman.lianji.core.database.RoutineEntity
import com.superman.lianji.core.database.RoutineExerciseEntity
import com.superman.lianji.core.database.RoutineSetEntity
import com.superman.lianji.core.domain.WeightUnit
import com.superman.lianji.core.domain.displayToGrams
import com.superman.lianji.core.domain.gramsToInput
import com.superman.lianji.core.ui.EmptyHint
import com.superman.lianji.core.ui.ExerciseArt
import com.superman.lianji.core.ui.LocalSnackbarHostState
import com.superman.lianji.core.ui.NumberInputDialog
import com.superman.lianji.core.ui.NumberStepper
import com.superman.lianji.core.ui.SectionTitle
import com.superman.lianji.core.ui.SelectionTopBar
import com.superman.lianji.core.ui.SwipeAction
import com.superman.lianji.core.ui.SwipeActionRow
import com.superman.lianji.core.ui.rememberSelectionController
import com.superman.lianji.core.ui.showInfo
import com.superman.lianji.core.ui.showUndo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private fun routineUpdatedLabel(updatedAt: Long): String = com.superman.lianji.core.ui.shortDate(
    java.time.Instant.ofEpochMilli(updatedAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay(),
)

class RoutineListViewModel(private val container: AppContainer) : ViewModel() {
    val routines = container.training.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun create(onOpen: (String) -> Unit) = viewModelScope.launch { onOpen(container.training.createRoutine("新计划")) }

    fun duplicate(routine: RoutineEntity, onOpen: (String) -> Unit) = viewModelScope.launch {
        container.training.duplicateRoutine(routine.id)?.let(onOpen)
    }

    fun delete(routine: RoutineEntity, onDeleted: (RoutineDetails) -> Unit) = viewModelScope.launch {
        val details = container.training.observeRoutine(routine.id).first() ?: return@launch
        container.training.deleteRoutine(routine.id)
        onDeleted(details)
    }

    fun deleteMany(items: List<RoutineEntity>, onDeleted: (List<RoutineDetails>) -> Unit) = viewModelScope.launch {
        val captured = items.mapNotNull { container.training.observeRoutine(it.id).first() }
        items.forEach { container.training.deleteRoutine(it.id) }
        onDeleted(captured)
    }

    fun restore(details: List<RoutineDetails>) = viewModelScope.launch {
        details.forEach { container.training.restoreRoutine(it) }
    }

    fun templates() = viewModelScope.launch {
        val rest = container.settings.settings.first().defaultRestSeconds
        container.training.insertTemplate("推", listOf("bench-press", "overhead-press", "dip", "lateral-raise", "tricep-pushdown"), rest)
        container.training.insertTemplate("拉", listOf("deadlift", "barbell-row", "lat-pulldown", "face-pull", "bicep-curl"), rest)
        container.training.insertTemplate("腿", listOf("squat", "romanian-deadlift", "walking-lunge", "leg-curl", "standing-calf-raise"), rest)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RoutineListScreen(container: AppContainer, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val model: RoutineListViewModel = viewModel(factory = simpleFactory { RoutineListViewModel(container) })
    val routines by model.routines.collectAsState()
    val selection = rememberSelectionController()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val allIds = remember(routines) { routines.map { it.id } }
    BackHandler(selection.active) { selection.clear() }

    fun remove(item: RoutineEntity) {
        model.delete(item) { details ->
            scope.launch { snackbar?.showUndo("已删除计划「${item.name}」") { model.restore(listOf(details)) } }
        }
    }

    fun removeSelected() {
        val items = routines.filter { it.id in selection.selected }
        if (items.isEmpty()) return
        model.deleteMany(items) { details ->
            scope.launch { snackbar?.showUndo("已删除 ${items.size} 个计划") { model.restore(details) } }
        }
        selection.clear()
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selection.active) {
                SelectionTopBar(selection, allIds, onClose = { selection.clear() }) {}
            } else {
                TopAppBar(
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    title = { Text("训练计划") },
                    navigationIcon = { IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "返回") } },
                )
            }
        },
        bottomBar = {
            if (selection.active) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Button({ removeSelected() }, Modifier.weight(1f)) { Text("删除 ${selection.count} 个计划") }
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Button({ model.create(onOpen) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("新建计划")
                }
            }
            if (routines.isEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("还没有计划", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "计划就是一组动作和目标次数，训练时一键开始。也可以先用推、拉、腿三个基础计划。",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            OutlinedButton(model::templates) { Text("加入推、拉、腿基础计划") }
                        }
                    }
                }
            } else {
                item {
                    TextButton(model::templates) { Text("加入推、拉、腿基础计划") }
                }
            }
            items(routines, key = { it.id }) { routine ->
                SwipeActionRow(
                    end = SwipeAction(
                        label = "删除",
                        icon = Icons.Filled.Delete,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ) { remove(routine) },
                ) {
                    Card(
                        Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (selection.isSelected(routine.id)) {
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
                                        if (selection.active) selection.toggle(routine.id) else onOpen(routine.id)
                                    },
                                    onLongClick = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selection.toggle(routine.id)
                                    },
                                )
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (selection.active) Checkbox(selection.isSelected(routine.id), { selection.toggle(routine.id) })
                            Column(Modifier.weight(1f)) {
                                Text(routine.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "更新于 " + routineUpdatedLabel(routine.updatedAt),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(Icons.Filled.PlayArrow, "打开计划", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

class RoutineEditorViewModel(private val container: AppContainer, private val id: String) : ViewModel() {
    val details = container.training.observeRoutine(id)
    val settings = container.settings.settings

    fun rename(name: String) = viewModelScope.launch {
        container.training.updateRoutine(id, name, details.first()?.routine?.notes.orEmpty())
    }

    fun add(nav: NavHostController) = nav.navigate("pick/routine/$id/-")
    fun replace(nav: NavHostController, routineExerciseId: String) = nav.navigate("pick/replace_routine/$id/$routineExerciseId")

    fun start(onOpen: (String) -> Unit) = viewModelScope.launch {
        val rest = container.settings.settings.first().defaultRestSeconds
        onOpen(container.training.startFromRoutine(id, rest))
    }

    fun duplicate(onOpen: (String) -> Unit) = viewModelScope.launch {
        container.training.duplicateRoutine(id)?.let(onOpen)
    }

    fun delete(done: () -> Unit) = viewModelScope.launch {
        container.training.deleteRoutine(id)
        done()
    }

    fun move(exerciseId: String, delta: Int) = viewModelScope.launch { container.training.moveRoutineExercise(id, exerciseId, delta) }

    fun remove(exercise: RoutineExerciseEntity, sets: List<RoutineSetEntity>, onDeleted: () -> Unit) = viewModelScope.launch {
        container.training.deleteRoutineExercise(exercise.id)
        onDeleted()
    }

    fun removeMany(items: List<Pair<RoutineExerciseEntity, List<RoutineSetEntity>>>) = viewModelScope.launch {
        items.forEach { (exercise, _) -> container.training.deleteRoutineExercise(exercise.id) }
    }

    fun restore(items: List<Pair<RoutineExerciseEntity, List<RoutineSetEntity>>>) = viewModelScope.launch {
        items.forEach { (exercise, sets) -> container.training.restoreRoutineExercise(exercise, sets) }
    }

    fun rest(exerciseId: String, current: Int, delta: Int) = viewModelScope.launch {
        container.training.updateRoutineRest(exerciseId, current + delta)
    }

    fun addSet(exerciseId: String) = viewModelScope.launch { container.training.addRoutineSet(exerciseId) }

    fun removeSet(set: RoutineSetEntity) = viewModelScope.launch { container.training.deleteRoutineSet(set.id) }

    fun restoreSet(set: RoutineSetEntity) = viewModelScope.launch { container.training.restoreRoutineSet(set) }

    fun targetReps(exerciseId: String, setId: String, reps: Int, grams: Int?) = viewModelScope.launch {
        container.training.updateRoutineSet(exerciseId, setId, reps.coerceIn(1, 100), grams)
    }

    fun targetWeight(exerciseId: String, setId: String, reps: Int?, grams: Int?) = viewModelScope.launch {
        container.training.updateRoutineSet(exerciseId, setId, reps, grams)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RoutineEditorScreen(container: AppContainer, id: String, nav: NavHostController) {
    val model: RoutineEditorViewModel = viewModel(factory = simpleFactory { RoutineEditorViewModel(container, id) })
    val details by model.details.collectAsState(initial = null)
    val settings by model.settings.collectAsState(initial = UserSettings())
    val selection = rememberSelectionController()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val routine = details ?: run {
        Column(Modifier.padding(20.dp)) { Text("正在读取计划") }
        return
    }
    val exercises = routine.exercises
    val allIds = remember(exercises) { exercises.map { it.first.id } }
    var renameOpen by rememberSaveable { mutableStateOf(false) }
    var deleteConfirm by remember { mutableStateOf(false) }
    var targetsFor by remember { mutableStateOf<RoutineExerciseEntity?>(null) }
    var menu by remember { mutableStateOf(false) }
    BackHandler(selection.active) { selection.clear() }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selection.active) {
                SelectionTopBar(selection, allIds, onClose = { selection.clear() }) {}
            } else {
                TopAppBar(
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    title = {
                        Text(
                            routine.routine.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = { renameOpen = true }),
                        )
                    },
                    navigationIcon = { IconButton({ nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回") } },
                    actions = {
                        IconButton({ renameOpen = true }) { Icon(Icons.Filled.Edit, "编辑名称") }
                        Box {
                            IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "更多") }
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; renameOpen = true })
                                DropdownMenuItem(text = { Text("复制计划") }, onClick = { menu = false; model.duplicate { nav.navigate("routine/$it") } })
                                DropdownMenuItem(text = { Text("删除计划") }, onClick = { menu = false; deleteConfirm = true })
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (selection.active) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp)) {
                        Button(
                            {
                                val picked = exercises.filter { it.first.id in selection.selected }
                                model.removeMany(picked)
                                selection.clear()
                                scope.launch {
                                    snackbar?.showUndo("已移除 ${picked.size} 个动作") { model.restore(picked) }
                                }
                            },
                            Modifier.weight(1f),
                        ) { Text("移除 ${selection.count} 个动作") }
                    }
                }
            } else {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton({ model.add(nav) }, Modifier.weight(1f)) {
                            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("添加动作")
                        }
                        Button({ model.start { nav.navigate("workout/$it") } }, Modifier.weight(1f)) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("开始计划")
                        }
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
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${exercises.size} 个动作 · ${exercises.sumOf { it.second.size }} 组目标", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "左滑动作可以移除，长按可以多选。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            if (exercises.isEmpty()) {
                item {
                    EmptyHint(
                        title = "计划还是空的",
                        hint = "从动作库挑几个动作，给每组设定次数和重量。",
                        action = { Button({ model.add(nav) }) { Text("添加动作") } },
                    )
                }
            }
            items(exercises, key = { it.first.id }) { (exercise, sets) ->
                ExerciseTargetCard(
                    exercise = exercise,
                    sets = sets,
                    unit = settings.weightUnit,
                    selectionMode = selection.active,
                    selected = selection.isSelected(exercise.id),
                    onClick = {
                        if (selection.active) selection.toggle(exercise.id) else targetsFor = exercise
                    },
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        selection.toggle(exercise.id)
                    },
                    onMoveUp = { model.move(exercise.id, -1) },
                    onMoveDown = { model.move(exercise.id, 1) },
                    onReplace = { model.replace(nav, exercise.id) },
                    onDelete = {
                        model.remove(exercise, sets) {
                            scope.launch {
                                snackbar?.showUndo("已移除 ${exercise.exerciseName}") {
                                    model.restore(listOf(exercise to sets))
                                }
                            }
                        }
                    },
                    onEditTargets = { targetsFor = exercise },
                    onRest = { delta -> model.rest(exercise.id, exercise.restSeconds, delta) },
                )
            }
            item {
                OutlinedButton({ model.add(nav) }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("添加动作")
                }
            }
        }
    }

    if (renameOpen) {
        var name by rememberSaveable { mutableStateOf(routine.routine.name) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text("计划名称") },
            text = { OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("名称") }) },
            confirmButton = { TextButton({ model.rename(name); renameOpen = false }) { Text("保存") } },
            dismissButton = { TextButton({ renameOpen = false }) { Text("取消") } },
        )
    }
    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("删除这个计划？") },
            text = { Text("计划里的动作和目标都会被删除，训练历史不受影响。") },
            confirmButton = { TextButton({ deleteConfirm = false; model.delete { nav.popBackStack() } }) { Text("删除", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton({ deleteConfirm = false }) { Text("取消") } },
        )
    }
    val targets = targetsFor
    if (targets != null) {
        val current = details?.exercises?.firstOrNull { it.first.id == targets.id } ?: (targets to emptyList())
        TargetSheet(
            exercise = current.first,
            sets = current.second,
            unit = settings.weightUnit,
            onReps = { set, reps -> model.targetReps(targets.id, set.id, reps, set.targetWeightGrams) },
            onWeight = { set, grams -> model.targetWeight(targets.id, set.id, set.targetReps, grams) },
            onAdd = { model.addSet(targets.id) },
            onDeleteSet = { set ->
                model.removeSet(set)
                scope.launch { snackbar?.showUndo("已删除一组目标") { model.restoreSet(set) } }
            },
            onDismiss = { targetsFor = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ExerciseTargetCard(
    exercise: RoutineExerciseEntity,
    sets: List<RoutineSetEntity>,
    unit: WeightUnit,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onReplace: () -> Unit,
    onDelete: () -> Unit,
    onEditTargets: () -> Unit,
    onRest: (Int) -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    SwipeActionRow(
        end = SwipeAction(
            label = "移除",
            icon = Icons.Filled.Delete,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) { onDelete() },
    ) {
        Card(
            Modifier.fillMaxWidth().testTag("routine_exercise"),
            colors = CardDefaults.cardColors(
                containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
            ),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (selectionMode) Checkbox(selected, { onClick() })
                    ExerciseArt(listOf(exercise.frameName), "${exercise.exerciseName} 动作图", Modifier.size(48.dp))
                    Column(Modifier.weight(1f)) {
                        Text(exercise.exerciseName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${sets.size} 组 · 休息 ${exercise.restSeconds} 秒",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Box {
                        IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "动作菜单") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(text = { Text("编辑目标") }, onClick = { menu = false; onEditTargets() })
                            DropdownMenuItem(text = { Text("上移") }, onClick = { menu = false; onMoveUp() })
                            DropdownMenuItem(text = { Text("下移") }, onClick = { menu = false; onMoveDown() })
                            DropdownMenuItem(text = { Text("替换动作") }, onClick = { menu = false; onReplace() })
                            DropdownMenuItem(text = { Text("移除") }, onClick = { menu = false; onDelete() })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    sets.take(3).forEach { set ->
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceVariant) {
                            Text(
                                targetText(set, unit),
                                Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                    if (sets.size > 3) Text("+${sets.size - 3}", style = MaterialTheme.typography.labelMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton({ onRest(-15) }) { Text("−15 秒") }
                    TextButton({ onRest(15) }) { Text("+15 秒") }
                    Spacer(Modifier.weight(1f))
                    TextButton({ onEditTargets() }) { Text("编辑目标") }
                }
            }
        }
    }
}

private fun targetText(set: RoutineSetEntity, unit: WeightUnit): String {
    val reps = set.targetReps ?: 8
    val weight = set.targetWeightGrams?.takeIf { it > 0 }?.let { gramsToInput(it, unit) }
    return if (weight != null) "$reps × $weight" else "$reps 次"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TargetSheet(
    exercise: RoutineExerciseEntity,
    sets: List<RoutineSetEntity>,
    unit: WeightUnit,
    onReps: (RoutineSetEntity, Int) -> Unit,
    onWeight: (RoutineSetEntity, Int?) -> Unit,
    onAdd: () -> Unit,
    onDeleteSet: (RoutineSetEntity) -> Unit,
    onDismiss: () -> Unit,
) {
    val weightStep = if (unit == WeightUnit.KG) 2.5 else 5.0
    val gramsStep = displayToGrams(weightStep.toString(), unit) ?: 2500
    var dialog by remember { mutableStateOf<Pair<RoutineSetEntity, String>?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(max = 560.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("${exercise.exerciseName} · 目标", style = MaterialTheme.typography.titleLarge)
            Text(
                "点数字可以精确输入，−/+ 按 ${if (unit == WeightUnit.KG) "2.5 公斤" else "5 磅"} 调整重量。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            sets.forEachIndexed { index, set ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${index + 1}", Modifier.width(18.dp), style = MaterialTheme.typography.titleMedium)
                    NumberStepper(
                        value = (set.targetReps ?: 8).toString(),
                        label = "次数",
                        modifier = Modifier.weight(1f),
                        onMinus = { onReps(set, ((set.targetReps ?: 8) - 1).coerceAtLeast(1)) },
                        onPlus = { onReps(set, ((set.targetReps ?: 8) + 1).coerceAtMost(100)) },
                        onEdit = { dialog = set to "reps" },
                    )
                    NumberStepper(
                        value = set.targetWeightGrams?.let { gramsToInput(it, unit) }.orEmpty(),
                        label = if (unit == WeightUnit.KG) "重量(公斤)" else "重量(磅)",
                        modifier = Modifier.weight(1f),
                        onMinus = {
                            val current = set.targetWeightGrams ?: 0
                            onWeight(set, (current - gramsStep).coerceAtLeast(0).takeIf { it > 0 })
                        },
                        onPlus = {
                            val current = set.targetWeightGrams ?: 0
                            onWeight(set, current + gramsStep)
                        },
                        onEdit = { dialog = set to "weight" },
                    )
                    IconButton({ onDeleteSet(set) }) { Icon(Icons.Filled.Delete, "删除这组") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onAdd, Modifier.weight(1f)) { Text("加一组") }
                Button(onDismiss, Modifier.weight(1f)) { Text("完成") }
            }
            HorizontalDivider()
            Text(
                "训练时点圆钮完成一组，休息会按这里的秒数自动开始。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    val current = dialog
    if (current != null) {
        val (set, field) = current
        NumberInputDialog(
            title = if (field == "reps") "目标次数" else "目标重量",
            unitLabel = if (field == "reps") "次数" else if (unit == WeightUnit.KG) "公斤" else "磅",
            initial = if (field == "reps") (set.targetReps ?: 8).toString() else set.targetWeightGrams?.let { gramsToInput(it, unit) }.orEmpty(),
            decimal = field != "reps",
            onConfirm = { text ->
                if (field == "reps") {
                    text.toIntOrNull()?.let { onReps(set, it.coerceIn(1, 100)) }
                } else {
                    onWeight(set, displayToGrams(text, unit))
                }
            },
            onDismiss = { dialog = null },
        )
    }
}
