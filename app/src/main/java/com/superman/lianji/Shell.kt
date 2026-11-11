package com.superman.lianji

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.superman.lianji.core.data.AppContainer
import com.superman.lianji.core.data.ExerciseSummary
import com.superman.lianji.core.data.ThemeMode
import com.superman.lianji.core.data.UserSettings
import com.superman.lianji.core.database.RoutineEntity
import com.superman.lianji.core.database.SessionEntity
import com.superman.lianji.core.domain.RecordHit
import com.superman.lianji.core.domain.WeightUnit
import com.superman.lianji.core.domain.formatNumber
import com.superman.lianji.core.domain.recentRecords
import com.superman.lianji.core.domain.trainingStreak
import com.superman.lianji.core.domain.volumeKg
import com.superman.lianji.core.domain.weekStart
import com.superman.lianji.core.ui.ExerciseArt
import com.superman.lianji.core.ui.LianjiTheme
import com.superman.lianji.core.ui.LocalSnackbarHostState
import com.superman.lianji.core.ui.SectionTitle
import com.superman.lianji.core.ui.StatTile
import com.superman.lianji.core.ui.chineseDate
import com.superman.lianji.feature.AboutScreen
import com.superman.lianji.feature.DataScreen
import com.superman.lianji.feature.ExerciseDetailScreen
import com.superman.lianji.feature.ExerciseListScreen
import com.superman.lianji.feature.ProfileScreen
import com.superman.lianji.feature.RoutineEditorScreen
import com.superman.lianji.feature.RoutineListScreen
import com.superman.lianji.feature.SessionDetailScreen
import com.superman.lianji.feature.WorkoutScreen
import com.superman.lianji.feature.simpleFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class LianjiApp : android.app.Application() {
    lateinit var container: AppContainer
        private set

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(wrapChinese(base))
    }

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(wrapChinese(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as LianjiApp).container
        setContent {
            val settings by container.settings.settings.collectAsState(initial = UserSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            }
            LianjiTheme(dark) { LianjiRoot(container) }
        }
    }
}

private fun wrapChinese(base: Context): Context {
    val locale = Locale.forLanguageTag("zh-CN")
    Locale.setDefault(locale)
    val config = Configuration(base.resources.configuration)
    config.setLocale(locale)
    config.setLayoutDirection(locale)
    return base.createConfigurationContext(config)
}

data class TodayUi(
    val greeting: String = "你好",
    val date: String = "",
    val active: SessionEntity? = null,
    val routines: List<RoutineEntity> = emptyList(),
    val sessions: Int = 0,
    val volume: String = "0",
    val volumeUnit: String = "公斤",
    val streak: Int = 0,
    val records: List<RecordHit> = emptyList(),
)

class TodayViewModel(private val container: AppContainer) : ViewModel() {
    val state = combine(
        container.training.observeActive(),
        container.training.observeRoutines(),
        container.training.observeHistory(),
        container.training.observeFlat(),
        container.settings.settings,
    ) { active, routines, history, sets, settings ->
        val today = LocalDate.now().toEpochDay()
        val start = weekStart(today)
        val weekSets = sets.filter { weekStart(it.epochDay) == start }
        TodayUi(
            greeting = when (LocalTime.now().hour) { in 5..11 -> "早上好"; in 12..17 -> "下午好"; else -> "晚上好" },
            date = chineseDate(),
            active = active,
            routines = routines,
            sessions = history.count { weekStart(it.localEpochDay) == start },
            volume = formatNumber(weekSets.sumOf { volumeKg(it) }.let { kg -> if (settings.weightUnit == WeightUnit.KG) kg else kg * 2.2046226218 }),
            volumeUnit = if (settings.weightUnit == WeightUnit.KG) "公斤" else "磅",
            streak = trainingStreak(history.map { it.localEpochDay }.toSet(), today),
            records = recentRecords(sets, today, settings.weightUnit).distinctBy { it.exerciseId }.take(4),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUi())

    fun quick(onOpen: (String) -> Unit) = viewModelScope.launch { onOpen(container.training.ensureSession("自由训练")) }
    fun fromRoutine(id: String, onOpen: (String) -> Unit) = viewModelScope.launch {
        val rest = container.settings.settings.first().defaultRestSeconds
        onOpen(container.training.startFromRoutine(id, rest))
    }
}

@Composable
private fun LianjiRoot(container: AppContainer) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route.orEmpty()
    val showBar = route in setOf("today", "exercises", "data", "profile")
    val snackbarHostState = remember { SnackbarHostState() }
    var showStart by rememberSaveable { mutableStateOf(false) }
    val todayModel: TodayViewModel = viewModel(factory = simpleFactory { TodayViewModel(container) })
    val today by todayModel.state.collectAsState()

    val start: () -> Unit = { showStart = true }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = { if (showBar) LianjiBar(route) { nav.navigateTab(it) } },
        floatingActionButton = {
            if (route == "today") {
                ExtendedFloatingActionButton(
                    onClick = start,
                    modifier = Modifier.testTag("start_fab"),
                    icon = { Icon(Icons.Filled.Add, null) },
                    text = { Text("开始训练") },
                )
            }
        },
    ) { padding ->
        CompositionLocalProvider(LocalSnackbarHostState provides snackbarHostState) {
            NavHost(nav, startDestination = "today", modifier = Modifier.padding(padding)) {
                composable("today") {
                    TodayScreen(
                        state = today,
                        onOpenWorkout = { nav.navigate("workout/$it") },
                        onOpenRoutine = { nav.navigate("routine/$it") },
                        onRoutines = { nav.navigate("routines") },
                        onQuick = { todayModel.quick { nav.navigate("workout/$it") } },
                        onStartRoutine = { todayModel.fromRoutine(it) { session -> nav.navigate("workout/$session") } },
                        onExercises = { nav.navigateTab("exercises") },
                    )
                }
                composable("exercises") { ExerciseListScreen(container, nav) }
                composable("data") { DataScreen(container) { nav.navigate("history/$it") } }
                composable("profile") { ProfileScreen(container, { nav.navigate("routines") }, { nav.navigate("about") }) }
                composable("routines") { RoutineListScreen(container, { nav.popBackStack() }, { nav.navigate("routine/$it") }) }
                composable("routine/{id}", listOf(navArgument("id") { defaultValue = "" })) {
                    RoutineEditorScreen(container, it.arguments?.getString("id").orEmpty(), nav)
                }
                composable("exercise/{id}") {
                    ExerciseDetailScreen(
                        container = container,
                        id = it.arguments?.getString("id").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onOpenWorkout = { session -> nav.navigate("workout/$session") },
                    )
                }
                composable("workout/{id}") { WorkoutScreen(container, it.arguments?.getString("id").orEmpty(), nav) }
                composable("history/{id}") {
                    SessionDetailScreen(
                        container = container,
                        sessionId = it.arguments?.getString("id").orEmpty(),
                        onBack = { nav.popBackStack() },
                        onRepeat = { session -> nav.navigate("workout/$session") },
                    )
                }
                composable("pick/{kind}/{owner}/{replace}") {
                    ExerciseListScreen(
                        container = container,
                        nav = nav,
                        pick = com.superman.lianji.core.data.PickTarget(
                            kind = it.arguments?.getString("kind").orEmpty(),
                            ownerId = it.arguments?.getString("owner").orEmpty(),
                            replaceId = it.arguments?.getString("replace")?.takeIf { value -> value != "-" },
                        ),
                    )
                }
                composable("about") { AboutScreen { nav.popBackStack() } }
            }
        }
    }
    if (showStart) {
        StartSheet(
            active = today.active,
            onContinue = { showStart = false; today.active?.let { session -> nav.navigate("workout/${session.id}") } },
            onQuick = { showStart = false; todayModel.quick { nav.navigate("workout/$it") } },
            onRoutines = { showStart = false; nav.navigate("routines") },
            onDismiss = { showStart = false },
        )
    }
}

private fun NavHostController.navigateTab(route: String) {
    navigate(route) {
        popUpTo("today") { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
private fun LianjiBar(route: String, onSelect: (String) -> Unit) {
    NavigationBar {
        NavigationBarItem(route == "today", { onSelect("today") }, { Icon(Icons.Filled.Home, null) }, label = { Text("今日") }, modifier = Modifier.testTag("tab_today"))
        NavigationBarItem(route == "exercises", { onSelect("exercises") }, { Icon(Icons.Filled.FitnessCenter, null) }, label = { Text("动作") }, modifier = Modifier.testTag("tab_exercises"))
        NavigationBarItem(route == "data", { onSelect("data") }, { Icon(Icons.Filled.BarChart, null) }, label = { Text("数据") }, modifier = Modifier.testTag("tab_data"))
        NavigationBarItem(route == "profile", { onSelect("profile") }, { Icon(Icons.Filled.Person, null) }, label = { Text("我的") }, modifier = Modifier.testTag("tab_profile"))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartSheet(
    active: SessionEntity?,
    onContinue: () -> Unit,
    onQuick: () -> Unit,
    onRoutines: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(androidx.compose.foundation.rememberScrollState())
                .testTag("start_sheet")
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
        ) {
            Text("开始训练", style = MaterialTheme.typography.headlineSmall)
            Text(
                "先热身，再记录。所有数据只留在这台手机上。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            if (active != null) {
                StartOption("继续「${active.title}」", "进行中", Icons.Filled.PlayArrow, onContinue)
            }
            StartOption("自由训练", "直接开始，动作随时加", Icons.Filled.FitnessCenter, onQuick)
            StartOption("从计划开始", "推、拉、腿或自己的计划", Icons.Filled.CalendarMonth, onRoutines)
            Spacer(Modifier.height(4.dp))
            TextButton(onDismiss, Modifier.fillMaxWidth()) { Text("取消") }
        }
    }
}

@Composable
private fun StartOption(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).padding(0.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.Filled.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

@Composable
private fun TodayScreen(
    state: TodayUi,
    onOpenWorkout: (String) -> Unit,
    onOpenRoutine: (String) -> Unit,
    onRoutines: () -> Unit,
    onQuick: () -> Unit,
    onStartRoutine: (String) -> Unit,
    onExercises: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(state.greeting, style = MaterialTheme.typography.headlineLarge)
            Text(state.date, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile("本周训练", "${state.sessions} 次", Modifier.weight(1f))
                StatTile("本周容量", "${state.volume} ${state.volumeUnit}", Modifier.weight(1f), highlight = true)
                StatTile("连续", "${state.streak} 天", Modifier.weight(1f))
            }
        }
        state.active?.let { active ->
            item {
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("训练进行中", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Text(active.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "开始于 ${java.time.Instant.ofEpochMilli(active.startedAtEpochMs).atZone(java.time.ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Button({ onOpenWorkout(active.id) }) {
                            Icon(Icons.Filled.PlayArrow, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("继续")
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onQuick, Modifier.weight(1f)) {
                    Icon(Icons.Filled.FitnessCenter, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("自由训练")
                }
                OutlinedButton(onExercises, Modifier.weight(1f)) { Text("浏览动作") }
            }
        }
        item {
            SectionTitle("训练计划") {
                TextButton(onRoutines) { Text("管理") }
            }
        }
        if (state.routines.isEmpty()) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("还没有计划", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "计划就是一组动作和目标次数，训练时一键开始。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onRoutines) { Text("去创建") }
                    }
                }
            }
        } else {
            items(state.routines.take(4), key = { it.id }) { routine ->
                Card(Modifier.fillMaxWidth().clickable { onOpenRoutine(routine.id) }) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(routine.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("点击查看或调整目标", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton({ onStartRoutine(routine.id) }) { Icon(Icons.Filled.PlayArrow, "开始这个计划", tint = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
        item { SectionTitle("近期个人纪录") }
        if (state.records.isEmpty()) {
            item {
                Text(
                    "完成一组正式训练后，这里会显示新的个人纪录。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            items(state.records) { record ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Timer, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(record.exerciseName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(record.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
