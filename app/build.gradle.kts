import java.io.ByteArrayOutputStream

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    kotlin("plugin.parcelize")
}

android {
    namespace = "com.chopcut"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.chopcut"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ProcessLifecycleOwner
    implementation(libs.androidx.lifecycle.process)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // ExoPlayer (Media3)
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.ui)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.transformer)

    // Timber
    implementation(libs.timber)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Coroutines
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.android)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Unit testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.androidx.lifecycle.viewmodel.compose)

    // Instrumentation testing
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.kotlinx.coroutines.test)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
}

tasks.register("logcatTimber", Exec::class) {
    group = "Debug"
    description = "Starts logcat with a filter for the app's PID and specific tags."

    // Use a lazy property to find the PID only when the task executes
    val pid by lazy {
        val output = ByteArrayOutputStream()
        try {
            project.exec {
                commandLine("adb", "shell", "pidof", "-s", "com.chopcut")
                standardOutput = output
                isIgnoreExitValue = true // Don't fail if pidof returns non-zero
            }.rethrowFailure() // Rethrow if adb command itself fails
            output.toString().trim()
        } catch (e: Exception) {
            "" // Return empty string on error
        }
    }

    commandLine("echo", "Checking for app PID...")

    doFirst {
        if (pid.isBlank()) {
            val errorMsg = "Error: Could not find PID for com.chopcut. Is the app running on a connected device?"
            println(errorMsg)
            throw StopExecutionException(errorMsg)
        }
        println("Found PID: $pid. Starting logcat...")
        commandLine(
            "adb",
            "logcat",
            "--pid=$pid",
            "-s", "Timber:D", "ThumbnailStrip:I", "ThumbnailExtractorBatch:D"
        )
    }
}

// ============================================================================
// Performance Testing Tasks
// ============================================================================

/**
 * Executa a suite completa de testes de performance
 * Gera relatórios em JSON, Markdown e CSV
 */
tasks.register("performanceTest") {
    group = "Performance"
    description = "Run complete performance test suite with reports (JSON, MD, CSV)"

    dependsOn("connectedDebugAndroidTest")

    doFirst {
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🚀 Running Complete Performance Test Suite")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("Tests included:")
        println("  ✓ Video metadata extraction")
        println("  ✓ Thumbnail extraction (single & batch)")
        println("  ✓ Different resolutions (160x90 to 1280x720)")
        println("  ✓ File operations")
        println()
        println("Reports will be saved to:")
        println("  /storage/emulated/0/Android/data/com.chopcut/files/performance_reports/")
        println()
        println("Estimated time: 5-10 minutes")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Configura argumentos de teste
        project.gradle.startParameter.projectProperties.put(
            "android.testInstrumentationRunnerArguments.class",
            "com.chopcut.performance.PerformanceTestSuite"
        )
    }

    doLast {
        println()
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("✅ Performance tests completed!")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println()
        println("To view reports:")
        println("  ./gradlew pullPerformanceReports")
        println("  ./scripts/view_performance_reports.sh")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}

/**
 * Executa apenas testes de extração de thumbnails
 */
tasks.register("performanceTestThumbnails", Exec::class) {
    group = "Performance"
    description = "Run thumbnail extraction performance tests only"

    commandLine(
        "./gradlew",
        "connectedAndroidTest",
        "-Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.performance.ThumbnailExtractionPerformanceTest"
    )

    doFirst {
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🎬 Running Thumbnail Extraction Performance Tests")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}

/**
 * Executa apenas testes de operações de vídeo
 */
tasks.register("performanceTestVideo", Exec::class) {
    group = "Performance"
    description = "Run video operations performance tests only"

    commandLine(
        "./gradlew",
        "connectedAndroidTest",
        "-Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.performance.VideoOperationsPerformanceTest"
    )

    doFirst {
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🎥 Running Video Operations Performance Tests")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}

/**
 * Executa stress test (200 thumbnails)
 */
tasks.register("performanceTestStress", Exec::class) {
    group = "Performance"
    description = "Run stress test (200 thumbnails extraction)"

    commandLine(
        "./gradlew",
        "connectedAndroidTest",
        "-Pandroid.testInstrumentationRunnerArguments.class=com.chopcut.performance.PerformanceTestSuite",
        "-Pandroid.testInstrumentationRunnerArguments.method=runStressTest"
    )

    doFirst {
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("💪 Running Stress Test (200 thumbnails)")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("⚠️  Warning: This test may take 10-15 minutes")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}

/**
 * Baixa relatórios de performance do dispositivo
 */
tasks.register("pullPerformanceReports", Exec::class) {
    group = "Performance"
    description = "Pull performance reports from device to ./performance_reports/"

    val reportsDir = file("${project.rootDir}/performance_reports")
    val devicePath = "/storage/emulated/0/Android/data/com.chopcut/files/performance_reports/"

    doFirst {
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("📥 Pulling performance reports from device...")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Cria diretório local se não existir
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
            println("✓ Created local directory: ${reportsDir.absolutePath}")
        }
    }

    commandLine("adb", "pull", devicePath, reportsDir.absolutePath)

    doLast {
        println()
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("✅ Reports downloaded to: ${reportsDir.absolutePath}")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println()
        println("Available reports:")
        reportsDir.listFiles()?.filter { it.isFile }?.forEach {
            val icon = when {
                it.name.endsWith(".json") -> "📊"
                it.name.endsWith(".md") -> "📝"
                it.name.endsWith(".csv") -> "📈"
                else -> "📄"
            }
            println("  $icon ${it.name}")
        }
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}

/**
 * Lista relatórios de performance no dispositivo
 */
tasks.register("listPerformanceReports", Exec::class) {
    group = "Performance"
    description = "List performance reports on device"

    val devicePath = "/storage/emulated/0/Android/data/com.chopcut/files/performance_reports/"

    commandLine("adb", "shell", "ls", "-lh", devicePath)

    doFirst {
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("📋 Listing reports on device: $devicePath")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}

/**
 * Limpa relatórios de performance do dispositivo
 */
tasks.register("clearPerformanceReports", Exec::class) {
    group = "Performance"
    description = "Clear performance reports from device"

    val devicePath = "/storage/emulated/0/Android/data/com.chopcut/files/performance_reports/"

    commandLine("adb", "shell", "rm", "-rf", devicePath)

    doFirst {
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        println("🗑️  Clearing reports from device...")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    doLast {
        println("✅ Reports cleared from device")
        println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}





