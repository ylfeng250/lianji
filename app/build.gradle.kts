import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// 版本号允许被 CI 覆盖：tag v1.2.0 -> -PappVersionName=1.2.0 -PappVersionCode=10200
// 未显式传 versionCode 时，由 versionName 推导（主*10000 + 次*100 + 修订），
// 保证本地构建和 GitHub Release 的 versionCode 规则一致。
fun versionCodeOf(name: String): Int {
    val match = Regex("""^(\d+)\.(\d+)\.(\d+)""").find(name) ?: return 1
    val (major, minor, patch) = match.destructured
    return major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
}

val appVersionName = providers.gradleProperty("appVersionName").orNull?.takeIf { it.isNotBlank() } ?: "1.1.0"
val appVersionCode = providers.gradleProperty("appVersionCode").orNull?.toIntOrNull()?.takeIf { it > 0 }
    ?: versionCodeOf(appVersionName)

// 发布签名：优先 rootProject/keystore.properties，其次环境变量；都没有时退回 debug 签名。
// CI 上由 .github/workflows/release.yml 写入 LIANJI_* 环境变量。
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun signingValue(property: String, env: String): String? =
    keystoreProperties.getProperty(property)?.takeIf { it.isNotBlank() }
        ?: System.getenv(env)?.takeIf { it.isNotBlank() }

val releaseStorePath = signingValue("storeFile", "LIANJI_KEYSTORE")
val releaseStorePassword = signingValue("storePassword", "LIANJI_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingValue("keyAlias", "LIANJI_KEY_ALIAS")
val releaseKeyPassword = signingValue("keyPassword", "LIANJI_KEY_PASSWORD")
val hasReleaseSigning = releaseStorePath != null && rootProject.file(releaseStorePath).isFile
if (releaseStorePath != null && !hasReleaseSigning) {
    logger.warn("配置了发布密钥但找不到文件：$releaseStorePath，本次将退回 debug 签名。")
}

android {
    namespace = "com.superman.lianji"
    compileSdk = 36
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.superman.lianji"
        minSdk = 23
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // 默认会给 906 张矢量动作图按 6 个密度各生成一套 PNG 兜底（约 70 MB），
        // 但 Compose 直接解析矢量 XML（含 fillType），不需要这些位图，显式关掉。
        vectorDrawables {
            generatedDensities?.clear()
        }
    }

    androidResources {
        localeFilters += listOf("zh")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // 没有发布密钥时退回 debug 签名，方便本地和 fork 直接出可安装包；
            // 正式 release 一定要配置 secrets，否则不同机器构建的签名无法互相覆盖安装。
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    lint {
        abortOnError = true
        disable += setOf("MissingTranslation")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    val bom = platform(libs.androidx.compose.bom)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(bom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.sqlite.bundled)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.profileinstaller)
    coreLibraryDesugaring(libs.desugar)
    ksp(libs.androidx.room.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.kotlinx.serialization.json)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(bom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}

tasks.register("checkOfflineManifest") {
    doLast {
        val manifest = file("src/main/AndroidManifest.xml").readText()
        val tags = Regex("<uses-permission\\b[^>]*>").findAll(manifest).map { it.value }.toList()
        check(tags.any { "INTERNET" in it && "tools:node=\"remove\"" in it })
        check(tags.any { "ACCESS_NETWORK_STATE" in it && "tools:node=\"remove\"" in it })
        check(tags.none { ("INTERNET" in it || "ACCESS_NETWORK_STATE" in it) && "tools:node=\"remove\"" !in it })
        check(manifest.contains("android:allowBackup=\"false\""))
    }
}

tasks.named("preBuild").configure { dependsOn("checkOfflineManifest") }

tasks.register("assertReleaseOffline") {
    dependsOn("processReleaseManifest")
    doLast {
        val root = layout.buildDirectory.dir("intermediates/merged_manifest").get().asFile
        val manifests = root.walkTopDown().filter { it.isFile && it.name == "AndroidManifest.xml" }.toList()
        check(manifests.isNotEmpty()) { "没有找到合并后的清单" }
        manifests.forEach { file ->
            val text = file.readText()
            check("android.permission.INTERNET" !in text) { "${file.path} 仍包含网络权限" }
            check("android.permission.ACCESS_NETWORK_STATE" !in text) { "${file.path} 仍包含网络状态权限" }
            check("android:allowBackup=\"false\"" in text) { "${file.path} 未关闭备份" }
        }
    }
}

// 动作图只在运行时用 getIdentifier 按名字查找，资源压缩看不到静态引用。
// res/raw/keep.xml 负责保留它们，这里在打包后再验证一次，避免动作图被静默删掉。
tasks.register("assertExerciseArtRetained") {
    dependsOn("packageRelease")
    doLast {
        val apk = layout.buildDirectory.file("outputs/apk/release/app-release.apk").get().asFile
        check(apk.isFile) { "没有找到发布 APK：${apk.path}" }
        val frames = Regex("\"ex_[a-z0-9_]+\"")
            .findAll(file("src/main/assets/catalog/exercises_zh.json").readText())
            .map { it.value.trim('"') }
            .distinct()
            .toList()
        check(frames.size >= 900) { "目录中的动作图数量异常：${frames.size}" }
        val arsc = ZipFile(apk).use { zip ->
            val entry = zip.getEntry("resources.arsc") ?: error("发布 APK 缺少 resources.arsc")
            zip.getInputStream(entry).readBytes()
        }
        // 资源名可能以 UTF-8 或 UTF-16LE 写入 resources.arsc，去掉 NUL 后统一按字节查找。
        val names = String(arsc, Charsets.ISO_8859_1).replace("\u0000", "")
        val missing = frames.filterNot { names.contains(it) }
        check(missing.isEmpty()) {
            "资源压缩删掉了 ${missing.size} 张动作图，例如 ${missing.take(5)}；请检查 res/raw/keep.xml"
        }
        println("已确认 ${frames.size} 张动作图保留在发布 APK 中")
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy("assertReleaseOffline")
    finalizedBy("assertExerciseArtRetained")
}
