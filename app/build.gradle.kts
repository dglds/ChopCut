import java.io.ByteArrayOutputStream
import org.gradle.kotlin.dsl.support.serviceOf

fun gitCommitCount(): Int {
    return try {
        val output = ByteArrayOutputStream()
        exec {
            commandLine("git", "rev-list", "--count", "HEAD")
            standardOutput = output
            isIgnoreExitValue = true
        }
        output.toString().trim().toIntOrNull() ?: 1
    } catch (e: Exception) {
        1
    }
}

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
        val commitCount = gitCommitCount()
        versionCode = commitCount
        versionName = "1.0.$commitCount"

        testInstrumentationRunner = "com.chopcut.runner.ChopCutTestRunner"
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
    implementation(libs.androidx.compose.ui.test.manifest)
    implementation(libs.coil.compose)
    implementation(libs.coil.video)
    implementation(libs.androidx.recyclerview)
}

tasks.register("logcatTimber", Exec::class) {
    group = "Debug"
    description = "Starts logcat with a filter for the app's PID and specific tags."

    val pid by lazy {
        val output = ByteArrayOutputStream()
        try {
            serviceOf<org.gradle.process.ExecOperations>().exec {
                commandLine("adb", "shell", "pidof", "-s", "com.chopcut")
                standardOutput = output
                isIgnoreExitValue = true
            }.rethrowFailure()
            output.toString().trim()
        } catch (e: Exception) {
            ""
        }
    }

    commandLine("echo", "Checking for app PID...")

    doFirst {
        if (pid.isBlank()) {
            val errorMsg = "Could not find PID for com.chopcut. Is the app running?"
            logger.lifecycle(errorMsg)
            throw StopExecutionException(errorMsg)
        }
        logger.lifecycle("Found PID: $pid. Starting logcat...")
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
 * TASK GENÉRICA PARA RODAR TESTES
 * Uso: ./gradlew runTest -Ptarget=NomeDaClasse#NomeDoMetodo
 */
tasks.register("runTest", Exec::class) {
    group = "Verification"
    description = "Runs any test with LIVE logs. Usage: ./gradlew runTest -Ptarget=ClassName#method"

    val target = project.findProperty("target")?.toString()
        ?: "com.chopcut.ThumbnailExtractionTest#extractsOneThumbPerSecond"

    workingDir = rootDir
    commandLine("./scripts/run_live_tests.sh", target)
}

/**
 * Atalho para rodar apenas os testes de extração de thumbnails.
 */
tasks.register("performanceTestExtraction") {
    group = "Performance"
    description = "Run only THUMBNAIL EXTRACTION performance tests with LIVE logs"
    dependsOn("runTest")
    
    // Força o target padrão para a classe de extração se rodar via este atalho
    doFirst {
        project.extra["target"] = "com.chopcut.performance.ThumbnailExtractionPerformanceTest#testExtractionCountAccuracy"
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
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logger.lifecycle("📥 Pulling performance reports from device...")
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")

        // Cria diretório local se não existir
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
            logger.lifecycle("✓ Created local directory: ${reportsDir.absolutePath}")
        }
    }

    commandLine("adb", "pull", devicePath, reportsDir.absolutePath)

    doLast {
        logger.lifecycle("")
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logger.lifecycle("✅ Reports downloaded to: ${reportsDir.absolutePath}")
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logger.lifecycle("")
        logger.lifecycle("Available reports:")
        reportsDir.listFiles()?.filter { it.isFile }?.forEach {
            val icon = when {
                it.name.endsWith(".json") -> "📊"
                it.name.endsWith(".md") -> "📝"
                it.name.endsWith(".csv") -> "📈"
                else -> "📄"
            }
            logger.lifecycle("  $icon ${it.name}")
        }
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
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
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logger.lifecycle("📋 Listing reports on device: $devicePath")
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
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
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
        logger.lifecycle("🗑️  Clearing reports from device...")
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }

    doLast {
        logger.lifecycle("✅ Reports cleared from device")
        logger.lifecycle("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━")
    }
}

// ============================================================================
// Pre-Test Device Check Task
// ============================================================================
tasks.register("checkDeviceConnection") {
    group = "Verification"
    description = "Checks if an Android device or emulator is connected before running tests."

    doLast {
        val outputStream = ByteArrayOutputStream()
        serviceOf<org.gradle.process.ExecOperations>().exec {
            commandLine("adb", "devices")
            standardOutput = outputStream
            isIgnoreExitValue = true
        }

        val devicesStr = outputStream.toString().trim()
        val lines = devicesStr.split("\n")
        var hasDevice = false
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && trimmed.endsWith("device") && !trimmed.startsWith("List")) {
                hasDevice = true
                break
            }
        }

        if (!hasDevice) {
            throw GradleException("❌ ERRO: Nenhum dispositivo Android ou emulador conectado. Conecte um dispositivo via USB ou inicie um emulador para rodar os testes.")
        } else {
            logger.lifecycle("✅ Dispositivo Android detectado. Continuando com os testes...")
        }
    }
}

// Intercepta e faz com que qualquer task de teste instrumentado no device dependa do checkDeviceConnection
tasks.whenTaskAdded {
    if (name.startsWith("connected") && name.endsWith("AndroidTest")) {
        dependsOn("checkDeviceConnection")
    }
}





