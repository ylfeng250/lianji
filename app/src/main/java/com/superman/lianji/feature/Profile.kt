package com.superman.lianji.feature

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.superman.lianji.core.data.AppContainer
import com.superman.lianji.core.data.ThemeMode
import com.superman.lianji.core.data.UserSettings
import com.superman.lianji.core.domain.WeightUnit
import com.superman.lianji.core.ui.LocalSnackbarHostState
import com.superman.lianji.core.ui.SectionTitle
import com.superman.lianji.core.ui.showInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

class ProfileViewModel(private val container: AppContainer) : ViewModel() {
    val settings = container.settings.settings
    val message = MutableStateFlow<String?>(null)

    fun update(transform: (UserSettings) -> UserSettings) = viewModelScope.launch { container.settings.update(transform) }

    fun export(uri: Uri) = viewModelScope.launch {
        runCatching { container.backup.exportTo(uri) }
            .onSuccess { message.value = "备份已保存" }
            .onFailure { message.value = it.message ?: "导出失败" }
    }

    fun import(uri: Uri) = viewModelScope.launch {
        runCatching { container.backup.importFrom(uri) }
            .onSuccess { message.value = "数据已恢复" }
            .onFailure { message.value = it.message ?: "恢复失败" }
    }

    fun clearMessage() {
        message.value = null
    }
}

@Composable
fun ProfileScreen(container: AppContainer, onRoutines: () -> Unit, onAbout: () -> Unit) {
    val model: ProfileViewModel = viewModel(factory = simpleFactory { ProfileViewModel(container) })
    val settings by model.settings.collectAsState(initial = UserSettings())
    val message by model.message.collectAsState()
    val snackbar = LocalSnackbarHostState.current
    var confirm by rememberSaveable { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) model.export(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.import(uri)
    }
    LaunchedEffect(message) {
        message?.let {
            snackbar?.showInfo(it)
            model.clearMessage()
        }
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("我的", style = MaterialTheme.typography.headlineLarge)
        Text(
            "离线记录，没有账号，也不上传训练数据。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("训练")
                SettingRow("重量单位", "影响输入、统计和纪录显示") {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            settings.weightUnit == WeightUnit.KG,
                            { model.update { it.copy(weightUnit = WeightUnit.KG) } },
                            { Text("公斤") },
                        )
                        FilterChip(
                            settings.weightUnit == WeightUnit.LB,
                            { model.update { it.copy(weightUnit = WeightUnit.LB) } },
                            { Text("磅") },
                        )
                    }
                }
                HorizontalDivider()
                SettingRow("默认休息", "完成一组后自动开始计时") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton({
                            model.update { it.copy(defaultRestSeconds = (it.defaultRestSeconds - 15).coerceIn(15, 300)) }
                        }) { Text("−", style = MaterialTheme.typography.titleLarge) }
                        Text("${settings.defaultRestSeconds} 秒", style = MaterialTheme.typography.titleMedium)
                        IconButton({
                            model.update { it.copy(defaultRestSeconds = (it.defaultRestSeconds + 15).coerceIn(15, 300)) }
                        }) { Text("+", style = MaterialTheme.typography.titleLarge) }
                    }
                }
                HorizontalDivider()
                SwitchRow("休息结束提示音", settings.soundEnabled) { model.update { current -> current.copy(soundEnabled = it) } }
                SwitchRow("休息结束振动", settings.vibrationEnabled) { model.update { current -> current.copy(vibrationEnabled = it) } }
                SwitchRow("训练时屏幕常亮", settings.keepScreenOn) { model.update { current -> current.copy(keepScreenOn = it) } }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("外观")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(settings.themeMode == mode, { model.update { it.copy(themeMode = mode) } }, { Text(themeLabel(mode)) })
                    }
                }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionTitle("计划与数据")
                OutlinedButton(onRoutines, Modifier.fillMaxWidth()) { Text("训练计划") }
                Button({ export.launch("lianji-${LocalDate.now()}.lianji") }, Modifier.fillMaxWidth()) { Text("导出备份") }
                OutlinedButton({ confirm = true }, Modifier.fillMaxWidth()) { Text("从备份恢复") }
                Text(
                    "备份是包含计划、训练记录和设置的 .lianji 文件，不包含动作图。恢复会替换当前数据。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        TextButton(onAbout, Modifier.fillMaxWidth()) { Text("关于、隐私与许可") }
        Spacer(Modifier.padding(bottom = 12.dp))
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("从备份恢复？") },
            text = { Text("当前训练记录、计划和设置都会被替换。建议先导出一份。") },
            confirmButton = {
                TextButton({
                    confirm = false
                    importLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                }) { Text("继续恢复") }
            },
            dismissButton = { TextButton({ confirm = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        content()
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked, onChange)
    }
}

private fun themeLabel(mode: ThemeMode) = when (mode) {
    ThemeMode.SYSTEM -> "跟随系统"
    ThemeMode.LIGHT -> "浅色"
    ThemeMode.DARK -> "深色"
}

class AboutViewModel(app: Application) : AndroidViewModel(app) {
    val text = MutableStateFlow("正在读取许可")
    init {
        viewModelScope.launch(Dispatchers.IO) {
            text.value = listOf("LICENSE-LIANJI", "ATTRIBUTION.md", "LICENSE-ASSETS", "LICENSE", "UNLICENSE.md").joinToString("\n\n") { name ->
                app.assets.open("licenses/$name").bufferedReader().use { it.readText() }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val model: AboutViewModel = viewModel()
    val text by model.text.collectAsState()
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onBack) { Icon(Icons.Filled.ArrowBack, "返回") }
            Text("关于练迹", style = MaterialTheme.typography.headlineSmall)
        }
        Text("练迹是开源、离线的训练记录应用。没有账号，不申请网络权限，训练数据只保存在这台手机上。源代码和许可：github.com/ylfeng250/lianji")
        Text("动作图示来自 Bryl Lim 的 workout-guide，部分第一帧改编自 Everkinetic，采用 CC BY-SA 4.0。应用把 SVG 转成 Android Vector Drawable 并用主题色着色；修改后的图示继续以同一许可提供。")
        Text("302 个动作都有中文说明：其中 85 个的分步文字来自 free-exercise-db（Unlicense），已译为中文；其余 217 个是项目原创要点（MIT）。不使用 free-exercise-db 的照片。")
        Text("交互设计参考了 GymMane 与 LibreFit 的公开界面与产品思路，没有使用它们的源代码。")
        Text("动作参考不能替代教练、医生或康复人员的指导。疼痛、头晕或不适时请停止训练。")
        Text(text, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.padding(bottom = 12.dp))
    }
}
