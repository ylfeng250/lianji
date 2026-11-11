plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

tasks.register<Exec>("generateCatalog") {
    workingDir = rootProject.projectDir
    commandLine("python3", "catalog-tools/generate_catalog.py")
}

dependencies {
    testImplementation(libs.junit)
}
