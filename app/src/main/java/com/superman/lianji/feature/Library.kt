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
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.superman.lianji.core.data.AppContainer
import com.superman.lianji.core.data.ExerciseSummary
import com.superman.lianji.core.data.PickTarget
import com.superman.lianji.core.domain.equipmentRank
import com.superman.lianji.core.domain.muscleRank
import com.superman.lianji.core.domain.orderedEquipment
import com.superman.lianji.core.domain.orderedMuscles
import com.superman.lianji.core.domain.orderedTypes
import com.superman.lianji.core.database.RoutineEntity
import com.superman.lianji.core.ui.EmptyHint
import com.superman.lianji.core.ui.ExerciseArt
import com.superman.lianji.core.ui.LocalSnackbarHostState
import com.superman.lianji.core.ui.Pill
import com.superman.lianji.core.ui.SelectionController
import com.superman.lianji.core.ui.SelectionTopBar
import com.superman.lianji.core.ui.SwipeAction
import com.superman.lianji.core.ui.SwipeActionRow
import com.superman.lianji.core.ui.rememberSelectionController
import com.superman.lianji.core.ui.SectionTitle
import com.superman.lianji.core.ui.showInfo
import com.superman.lianji.core.ui.showUndo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ExerciseSort(val label: String) {
    NAME("名称"),
    MUSCLE("肌群"),
    EQUIPMENT("器械"),
    FAVORITE("收藏优先"),
}

private data class ExerciseFilterQuery(
    val query: String,
    val muscle: String,
    val equipment: String,
    val type: String,
    val favoritesOnly: Boolean,
    val stepsOnly: Boolean,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ExerciseListViewModel(private val container: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")
    val muscle = MutableStateFlow("")
    val equipment = MutableStateFlow("")
    val type = MutableStateFlow("")
    val favoritesOnly = MutableStateFlow(false)
    val stepsOnly = MutableStateFlow(false)
    val sort = MutableStateFlow(ExerciseSort.NAME)

    private val request = combine(
        combine(query, muscle, equipment) { q, m, e -> Triple(q, m, e) },
        combine(type, favoritesOnly, stepsOnly) { t, f, s -> Triple(t, f, s) },
    ) { a, b -> ExerciseFilterQuery(a.first, a.second, a.third, b.first, b.second, b.third) }

    private val source = request.flatMapLatest { filter ->
        container.exercises.observe(filter.query, filter.muscle, filter.equipment, filter.type, filter.favoritesOnly)
    }

    private val collator = java.text.Collator.getInstance(java.util.Locale.CHINA)
    private val byName = Comparator<ExerciseSummary> { a, b -> collator.compare(a.nameZh, b.nameZh) }

    val exercises = combine(source, stepsOnly, sort) { list, onlySteps, order ->
        val filtered = if (onlySteps) list.filter { it.hasSteps } else list
        when (order) {
            ExerciseSort.NAME -> filtered.sortedWith(byName)
            ExerciseSort.MUSCLE -> filtered.sortedWith(compareBy<ExerciseSummary> { muscleRank(it.muscleZh) }.then(byName))
            ExerciseSort.EQUIPMENT -> filtered.sortedWith(compareBy<ExerciseSummary> { equipmentRank(it.equipmentZh) }.then(byName))
            ExerciseSort.FAVORITE -> filtered.sortedWith(compareByDescending<ExerciseSummary> { it.favorite }.then(byName))
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val options = container.exercises.observe("", "", "", "", false)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val routines = container.training.observeRoutines()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeSession = container.training.observeActive()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setFavorite(ids: Collection<String>, favorite: Boolean) = viewModelScope.launch {
        container.exercises.setFavorite(ids, favorite)
    }

    fun addToRoutine(routineId: String, ids: List<String>, done: () -> Unit) = viewModelScope.launch {
        container.training.addExercisesToRoutine(routineId, ids, container.settings.settings.first().defaultRestSeconds)
        done()
    }

    fun createRoutineWith(ids: List<String>, done: (String) -> Unit) = viewModelScope.launch {
        val id = container.training.createRoutine("新计划")
        container.training.addExercisesToRoutine(id, ids, container.settings.settings.first().defaultRestSeconds)
        done(id)
    }

    fun addToSession(sessionId: String?, ids: List<String>, onSession: (String) -> Unit) = viewModelScope.launch {
        val rest = container.settings.settings.first().defaultRestSeconds
        val session = sessionId ?: container.training.ensureSession("自由训练")
        container.training.addExercisesToSession(session, ids, rest)
        onSession(session)
    }

    fun pick(target: PickTarget, ids: List<String>, done: () -> Unit) = viewModelScope.launch {
        val rest = container.settings.settings.first().defaultRestSeconds
        when (target.kind) {
            "routine" -> container.training.addExercisesToRoutine(target.ownerId, ids, rest)
            "session" -> container.training.addExercisesToSession(target.ownerId, ids, rest)
            "replace" -> target.replaceId?.let { replaceId -> ids.firstOrNull()?.let { container.training.replaceExercise(replaceId, it) } }
            "replace_routine" -> target.replaceId?.let { replaceId -> ids.firstOrNull()?.let { container.training.replaceRoutineExercise(replaceId, it) } }
        }
        done()
    }

    fun resetFilters() {
        muscle.value = ""
        equipment.value = ""
        type.value = ""
        favoritesOnly.value = false
        stepsOnly.value = false
    }

    fun activeFilterCount(): Int =
        listOf(muscle.value, equipment.value, type.value).count { it.isNotBlank() } +
            (if (favoritesOnly.value) 1 else 0) + (if (stepsOnly.value) 1 else 0)
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExerciseListScreen(container: AppContainer, nav: NavHostController, pick: PickTarget? = null) {
    val model: ExerciseListViewModel = viewModel(factory = simpleFactory { ExerciseListViewModel(container) })
    val exercises by model.exercises.collectAsState()
    val options by model.options.collectAsState()
    val routines by model.routines.collectAsState()
    val active by model.activeSession.collectAsState()
    val muscle by model.muscle.collectAsState()
    val equipment by model.equipment.collectAsState()
    val type by model.type.collectAsState()
    val favoritesOnly by model.favoritesOnly.collectAsState()
    val stepsOnly by model.stepsOnly.collectAsState()
    val sort by model.sort.collectAsState()
    val query by model.query.collectAsState()
    val selection = rememberSelectionController()
    val snackbar = LocalSnackbarHostState.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var showFilters by rememberSaveable { mutableStateOf(false) }
    var sortOpen by remember { mutableStateOf(false) }
    var routineSheet by remember { mutableStateOf<List<String>?>(null) }
    val allIds = remember(exercises) { exercises.map { it.id } }
    val replaceMode = pick?.kind == "replace" || pick?.kind == "replace_routine"
    val pickMode = pick != null

    BackHandler(enabled = selection.active) { selection.clear() }

    fun toast(message: String) {
        scope.launch { snackbar?.showInfo(message) }
    }

    fun toggleFavorite(ids: List<String>) {
        if (ids.isEmpty()) return
        val itemFavorite = ids.all { id -> exercises.firstOrNull { it.id == id }?.favorite == true }
        val target = !itemFavorite
        model.setFavorite(ids, target)
        scope.launch {
            snackbar?.showUndo(if (target) "已收藏 ${ids.size} 个动作" else "已取消收藏 ${ids.size} 个动作") {
                model.setFavorite(ids, !target)
            }
        }
    }

    fun addToWorkout(ids: List<String>) {
        if (ids.isEmpty()) return
        model.addToSession(active?.id, ids) { session ->
            toast(if (active == null) "已开始自由训练，加入 ${ids.size} 个动作" else "已加入 ${ids.size} 个动作")
            nav.navigate("workout/$session")
        }
    }

    fun confirmSelection() {
        val ids = selection.selected.toList()
        if (pick != null) {
            model.pick(pick, ids) { selection.clear(); nav.popBackStack() }
        } else {
            addToWorkout(ids)
            selection.clear()
        }
    }

    Scaffold(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selection.active) {
                SelectionTopBar(selection, allIds, onClose = { selection.clear() }) {
                    IconButton({ toggleFavorite(selection.selected.toList()) }) {
                        val allFavorite = selection.selected.all { id -> exercises.firstOrNull { it.id == id }?.favorite == true }
                        Icon(if (allFavorite) Icons.Filled.StarBorder else Icons.Filled.Star, if (allFavorite) "取消收藏" else "收藏")
                    }
                    IconButton({ routineSheet = selection.selected.toList() }) { Icon(Icons.Filled.PlaylistAdd, "加入计划") }
                }
            } else {
                TopAppBar(
                    windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    title = { Text(if (pickMode) pickTitle(pick) else "动作") },
                    navigationIcon = {
                        if (pickMode) IconButton({ nav.popBackStack() }) { Icon(Icons.Filled.ArrowBack, "返回") }
                    },
                    actions = {
                        if (!replaceMode) {
                            Box {
                                IconButton({ sortOpen = true }) { Icon(Icons.Filled.SwapVert, "排序") }
                                DropdownMenu(sortOpen, { sortOpen = false }) {
                                    ExerciseSort.entries.forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.label) },
                                            onClick = { model.sort.value = option; sortOpen = false },
                                            trailingIcon = { if (sort == option) Icon(Icons.Filled.Check, null) },
                                        )
                                    }
                                }
                            }
                        }
                        Box {
                            IconButton({ showFilters = true }) {
                                Icon(Icons.Filled.FilterList, "筛选")
                            }
                            val activeCount = listOf(muscle, equipment, type).count { it.isNotBlank() } +
                                (if (favoritesOnly) 1 else 0) + (if (stepsOnly) 1 else 0)
                            if (activeCount > 0) {
                                Surface(
                                    shape = RoundedCornerShape(50),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.align(Alignment.TopEnd).size(16.dp),
                                ) {
                                    Text(
                                        activeCount.toString(),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        style = MaterialTheme.typography.labelSmall,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                }
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (selection.active) {
                Surface(tonalElevation = 3.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton({ toggleFavorite(selection.selected.toList()) }, Modifier.weight(1f)) {
                            Text(if (selection.selected.all { id -> exercises.firstOrNull { it.id == id }?.favorite == true }) "取消收藏" else "收藏")
                        }
                        OutlinedButton({ routineSheet = selection.selected.toList() }, Modifier.weight(1f)) { Text("加入计划") }
                        Button({ confirmSelection() }, Modifier.weight(1f)) {
                            Text(if (pickMode) "添加 ${selection.count}" else if (active == null) "开始训练" else "加入训练")
                        }
                    }
                }
            } else if (pickMode && !replaceMode) {
                Surface(tonalElevation = 3.dp) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "长按动作可多选",
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton({ selection.selectAll(allIds) }) { Text("全选") }
                    }
                }
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!selection.active) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { model.query.value = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    label = { Text("搜索中文名、肌群或器械") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton({ model.query.value = "" }) { Icon(Icons.Filled.Clear, "清空") }
                    },
                    singleLine = true,
                )
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val muscles = listOf("") + orderedMuscles(options.map { it.muscleZh })
                    items(muscles) { value ->
                        FilterChip(value == muscle, { model.muscle.value = value }, { Text(value.ifBlank { "全部" }) })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "共 ${exercises.size} 个动作",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton({ selection.selectAll(allIds) }, enabled = exercises.isNotEmpty()) {
                        Icon(Icons.Filled.Checklist, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("多选")
                    }
                }
            }
            if (exercises.isEmpty()) {
                EmptyHint(
                    title = if (query.isNotBlank() || model.activeFilterCount() > 0) "没有匹配的动作" else "动作库是空的",
                    hint = if (query.isNotBlank() || model.activeFilterCount() > 0) "换个关键词，或者重置筛选条件。" else "动作目录会随安装包一起预置。",
                    action = if (model.activeFilterCount() > 0 || query.isNotBlank()) {
                        { TextButton({ model.query.value = ""; model.resetFilters() }) { Text("重置筛选") } }
                    } else null,
                )
            }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(exercises, key = { it.id }) { item ->
                    val favoriteAction = SwipeAction(
                        label = if (item.favorite) "取消收藏" else "收藏",
                        icon = if (item.favorite) Icons.Filled.StarBorder else Icons.Filled.Star,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ) { toggleFavorite(listOf(item.id)) }
                    val addAction = SwipeAction(
                        label = if (pickMode) "选择" else "加入训练",
                        icon = if (pickMode) Icons.Filled.Check else Icons.Filled.Add,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ) {
                        if (pick != null) {
                            model.pick(pick, listOf(item.id)) { nav.popBackStack() }
                        } else {
                            addToWorkout(listOf(item.id))
                        }
                    }
                    SwipeActionRow(
                        start = if (replaceMode) null else favoriteAction,
                        end = if (replaceMode) null else addAction,
                    ) {
                        ExerciseRow(
                            item = item,
                            selectionMode = selection.active,
                            selected = selection.isSelected(item.id),
                            pickMode = pickMode,
                            onClick = {
                                when {
                                    selection.active -> selection.toggle(item.id)
                                    pick != null -> model.pick(pick, listOf(item.id)) { nav.popBackStack() }
                                    else -> nav.navigate("exercise/${item.id}")
                                }
                            },
                            onLongClick = {
                                if (!replaceMode) {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    selection.toggle(item.id)
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (showFilters) {
        FilterSheet(
            muscles = orderedMuscles(options.map { it.muscleZh }),
            equipments = orderedEquipment(options.map { it.equipmentZh }),
            types = orderedTypes(options.map { it.typeZh }),
            muscle = muscle,
            equipment = equipment,
            type = type,
            favoritesOnly = favoritesOnly,
            stepsOnly = stepsOnly,
            onMuscle = { model.muscle.value = it },
            onEquipment = { model.equipment.value = it },
            onType = { model.type.value = it },
            onFavorites = { model.favoritesOnly.value = it },
            onSteps = { model.stepsOnly.value = it },
            onReset = { model.resetFilters() },
            onDismiss = { showFilters = false },
        )
    }

    routineSheet?.let { ids ->
        RoutinePickerSheet(
            routines = routines,
            count = ids.size,
            onPick = { routineId ->
                model.addToRoutine(routineId, ids) {
                    toast("已加入计划")
                }
                routineSheet = null
                selection.clear()
            },
            onCreate = {
                model.createRoutineWith(ids) {
                    toast("已新建计划并加入 ${ids.size} 个动作")
                }
                routineSheet = null
                selection.clear()
            },
            onDismiss = { routineSheet = null },
        )
    }
}

private fun pickTitle(pick: PickTarget?): String = when (pick?.kind) {
    "routine" -> "选择动作加入计划"
    "session" -> "选择动作加入训练"
    "replace", "replace_routine" -> "替换动作"
    else -> "动作"
}

@Composable
private fun ExerciseRow(
    item: ExerciseSummary,
    selectionMode: Boolean,
    selected: Boolean,
    pickMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth().testTag("exercise_row"),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (selectionMode) {
                Checkbox(checked = selected, onCheckedChange = { onClick() })
            }
            ExerciseArt(listOf(item.frame), "${item.nameZh}动作图", Modifier.size(60.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(item.nameZh, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${item.muscleZh} · ${item.equipmentZh} · ${item.typeZh}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!item.hasSteps) {
                    Text("只有图示", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (item.favorite && !selectionMode) {
                Icon(Icons.Filled.Star, "已收藏", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterSheet(
    muscles: List<String>,
    equipments: List<String>,
    types: List<String>,
    muscle: String,
    equipment: String,
    type: String,
    favoritesOnly: Boolean,
    stepsOnly: Boolean,
    onMuscle: (String) -> Unit,
    onEquipment: (String) -> Unit,
    onType: (String) -> Unit,
    onFavorites: (Boolean) -> Unit,
    onSteps: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .heightIn(max = 560.dp)
                .testTag("filter_sheet")
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("筛选动作", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                TextButton({ onReset() }) { Text("重置") }
                TextButton({ onDismiss() }) { Text("完成") }
            }
            FilterGroup("肌群", muscles, muscle, onMuscle)
            FilterGroup("器械", equipments, equipment, onEquipment)
            FilterGroup("训练类型", types, type, onType)
            HorizontalDivider()
            SwitchRow("只看收藏", favoritesOnly, onFavorites)
            SwitchRow("只看有分步文字", stepsOnly, onSteps)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterGroup(label: String, values: List<String>, selected: String, onSelect: (String) -> Unit) {
    SectionTitle(label)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FilterChip(selected.isBlank(), { onSelect("") }, { Text("全部") })
        values.forEach { value ->
            FilterChip(selected == value, { onSelect(if (selected == value) "" else value) }, { Text(value) })
        }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoutinePickerSheet(
    routines: List<RoutineEntity>,
    count: Int,
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
                .heightIn(max = 520.dp)
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("把 $count 个动作加入计划", style = MaterialTheme.typography.titleLarge)
            Text(
                "选一个已有计划，或者直接新建一个。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Button(onCreate, Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("新建计划并加入")
            }
            if (routines.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("已有计划", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(Modifier.fillMaxWidth()) {
                routines.forEach { routine ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(onClick = { onPick(routine.id) }, onLongClick = {})
                            .padding(vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(routine.name, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
