package com.superman.lianji.core.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

@Serializable
data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val pageUrl: String = "",
    val notes: String = "",
)

sealed class UpdateCheckResult {
    data class Available(val manifest: UpdateManifest) : UpdateCheckResult()
    data object UpToDate : UpdateCheckResult()
    data class Failed(val reason: String) : UpdateCheckResult()
}

private val updateJson = Json { ignoreUnknownKeys = true }

fun compareUpdate(currentCode: Int, manifest: UpdateManifest): UpdateCheckResult =
    if (manifest.versionCode > currentCode) UpdateCheckResult.Available(manifest) else UpdateCheckResult.UpToDate

fun fetchUpdateManifest(manifestUrl: String, timeoutMs: Int = 5_000): UpdateManifest {
    val connection = (URL(manifestUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = timeoutMs
        readTimeout = timeoutMs
        requestMethod = "GET"
        instanceFollowRedirects = true
        setRequestProperty("Accept", "application/json")
        setRequestProperty("User-Agent", "Lianji-UpdateCheck")
    }
    try {
        val code = connection.responseCode
        if (code !in 200..299) throw IOException("版本信息请求失败：$code")
        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val manifest = updateJson.decodeFromString<UpdateManifest>(body)
        require(manifest.versionCode > 0) { "版本号无效" }
        require(manifest.versionName.isNotBlank()) { "版本名无效" }
        require(manifest.apkUrl.startsWith("https://")) { "安装包地址必须是 https" }
        if (manifest.pageUrl.isNotBlank()) require(manifest.pageUrl.startsWith("https://")) { "下载页必须是 https" }
        return manifest
    } finally {
        connection.disconnect()
    }
}

fun checkForUpdate(manifestUrl: String, currentCode: Int): UpdateCheckResult =
    runCatching { compareUpdate(currentCode, fetchUpdateManifest(manifestUrl)) }
        .getOrElse { UpdateCheckResult.Failed(it.message ?: "检查更新失败") }

fun shouldAutoCheck(lastCheckEpochMs: Long, nowEpochMs: Long, intervalMs: Long = TimeUnit.DAYS.toMillis(1)): Boolean =
    lastCheckEpochMs <= 0L || nowEpochMs - lastCheckEpochMs >= intervalMs
