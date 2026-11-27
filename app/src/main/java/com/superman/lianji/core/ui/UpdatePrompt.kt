package com.superman.lianji.core.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.superman.lianji.core.domain.UpdateManifest

@Composable
fun UpdateAvailableDialog(
    manifest: UpdateManifest,
    onDismiss: () -> Unit,
    onLater: () -> Unit = onDismiss,
) {
    val context = LocalContext.current
    fun openDownload() {
        val target = manifest.apkUrl.ifBlank { manifest.pageUrl }
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(target)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        onDismiss()
    }
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text("发现新版本 ${manifest.versionName}") },
        text = {
            Text(
                buildString {
                    append("当前安装包可以更新。点「去下载」用浏览器打开安装包。")
                    if (manifest.notes.isNotBlank()) {
                        append("\n\n")
                        append(manifest.notes.trim())
                    }
                },
            )
        },
        confirmButton = { TextButton(::openDownload) { Text("去下载") } },
        dismissButton = { TextButton(onLater) { Text("稍后") } },
    )
}
