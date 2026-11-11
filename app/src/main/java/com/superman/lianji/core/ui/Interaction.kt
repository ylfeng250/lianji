package com.superman.lianji.core.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import kotlin.math.roundToInt
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/** 根 Scaffold 提供的 Snackbar，用来在任何页面弹出「撤销」。 */
val LocalSnackbarHostState = staticCompositionLocalOf<SnackbarHostState?> { null }

suspend fun SnackbarHostState.showUndo(message: String, onUndo: suspend () -> Unit) {
    val result = showSnackbar(
        message = message,
        actionLabel = "撤销",
        withDismissAction = false,
        duration = SnackbarDuration.Short,
    )
    if (result == SnackbarResult.ActionPerformed) onUndo()
}

suspend fun SnackbarHostState.showInfo(message: String) {
    showSnackbar(message = message, duration = SnackbarDuration.Short)
}

/**
 * 多选状态。列表项长按进入，顶部栏与底部操作条都读它。
 * 用 rememberSaveable 保存，旋转屏幕或进程被系统回收后选择不丢。
 */
class SelectionController(initial: Set<String> = emptySet()) {
    var selected by mutableStateOf(initial)
        private set

    val active: Boolean get() = selected.isNotEmpty()
    val count: Int get() = selected.size

    fun isSelected(id: String): Boolean = id in selected

    fun toggle(id: String) {
        selected = if (id in selected) selected - id else selected + id
    }

    fun clear() {
        selected = emptySet()
    }

    fun selectAll(ids: Collection<String>) {
        selected = ids.toSet()
    }
}

@Composable
fun rememberSelectionController(): SelectionController {
    val saver = remember {
        Saver<SelectionController, List<String>>(
            save = { it.selected.toList() },
            restore = { SelectionController(it.toSet()) },
        )
    }
    return rememberSaveable(saver = saver) { SelectionController() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selection: SelectionController,
    allIds: List<String>,
    onClose: () -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    TopAppBar(
        windowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        navigationIcon = { IconButton(onClose) { Icon(Icons.Filled.Close, "退出多选") } },
        title = { Text("已选 ${selection.count} 项", fontWeight = FontWeight.SemiBold) },
        actions = {
            TextButton(
                onClick = {
                    if (selection.count == allIds.size && allIds.isNotEmpty()) selection.clear()
                    else selection.selectAll(allIds)
                },
            ) { Text(if (selection.count == allIds.size && allIds.isNotEmpty()) "取消全选" else "全选") }
            actions()
        },
    )
}

data class SwipeAction(
    val label: String,
    val icon: ImageVector,
    val containerColor: Color,
    val contentColor: Color,
    val onAction: () -> Unit,
)

/**
 * 给列表项加左右滑动动作：滑过阈值触发一次动作并回弹。
 * 自己实现而不是用 SwipeToDismissBox，是为了保证「动作触发后一定回弹」，
 * 危险操作交给调用方删除数据、Snackbar 负责撤销。
 */
@Composable
fun SwipeActionRow(
    start: SwipeAction? = null,
    end: SwipeAction? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val threshold = with(density) { 88.dp.toPx() }
    val maxOffset = with(density) { 132.dp.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    Box(modifier.clip(RoundedCornerShape(18.dp))) {
        Row(Modifier.matchParentSize()) {
            start?.let { action ->
                SwipeBadge(action, offset = (offsetX / threshold).coerceIn(0f, 1f), alignment = Alignment.CenterStart)
            }
            Spacer(Modifier.weight(1f))
            end?.let { action ->
                SwipeBadge(action, offset = (-offsetX / threshold).coerceIn(0f, 1f), alignment = Alignment.CenterEnd)
            }
        }
        Box(
            Modifier
                .offset { androidx.compose.ui.unit.IntOffset(offsetX.roundToInt(), 0) }
                .draggable(
                    enabled = start != null || end != null,
                    orientation = Orientation.Horizontal,
                    state = rememberDraggableState { delta ->
                        offsetX = (offsetX + delta).coerceIn(-maxOffset, maxOffset)
                    },
                    onDragStopped = {
                        val action = when {
                            offsetX >= threshold -> start
                            offsetX <= -threshold -> end
                            else -> null
                        }
                        action?.let {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            it.onAction()
                        }
                        animate(
                            initialValue = offsetX,
                            targetValue = 0f,
                            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        ) { value, _ -> offsetX = value }
                    },
                ),
        ) {
            content()
        }
    }
}

@Composable
private fun SwipeBadge(action: SwipeAction, offset: Float, alignment: Alignment) {
    if (offset <= 0f) return
    Row(
        Modifier
            .fillMaxHeight()
            .width(120.dp)
            .graphicsLayer { alpha = offset }
            .background(action.containerColor)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(action.icon, contentDescription = null, tint = action.contentColor, modifier = Modifier.size(20.dp))
        Text(action.label, color = action.contentColor, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

@Composable
fun NumberStepper(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    onEdit: () -> Unit,
) {
    Column(modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepperButton(Icons.Filled.Remove, "减少$label", enabled, onMinus)
                Text(
                    value.ifBlank { "—" },
                    modifier = Modifier.weight(1f).clickable(enabled = enabled, onClick = onEdit).padding(vertical = 6.dp),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                )
                StepperButton(Icons.Filled.Add, "增加$label", enabled, onPlus)
            }
        }
    }
}

@Composable
private fun StepperButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size(26.dp)) {
        Icon(icon, description, modifier = Modifier.size(16.dp))
    }
}

/** 精确输入用一个轻量输入框，不让列表里每一行都挂键盘焦点。 */
@Composable
fun NumberInputDialog(
    title: String,
    unitLabel: String? = null,
    initial: String,
    decimal: Boolean = false,
    allowEmpty: Boolean = true,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by rememberSaveable(initial) { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { value -> text = value.filter { it.isDigit() || it == '.' || it == ',' } },
                singleLine = true,
                label = { Text(unitLabel ?: "数值") },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (text.isNotBlank() || allowEmpty) {
                        onConfirm(text.replace(',', '.'))
                        onDismiss()
                    }
                },
            ) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = "确定",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm()
                    onDismiss()
                },
            ) {
                Text(
                    confirmLabel,
                    color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
fun EmptyHint(title: String, hint: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            hint,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        action?.invoke()
    }
}

@Composable
fun Pill(
    text: String,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(50),
        color = background,
        modifier = if (onClick == null) modifier else modifier.clickable(onClick = onClick),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelLarge,
            color = foreground,
            maxLines = 1,
        )
    }
}
