import java.io.ByteArrayOutputStream
import org.gradle.kotlin.dsl.support.serviceOf

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
    implementation(libs.androidx.recyclerview) // Added for RecyclerView usage
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
            println("✅ Dispositivo Android detectado. Continuando com os testes...")
        }
    }
}

// Intercepta e faz com que qualquer task de teste instrumentado no device dependa do checkDeviceConnection
tasks.whenTaskAdded {
    if (name.startsWith("connected") && name.endsWith("AndroidTest")) {
        dependsOn("checkDeviceConnection")
    }
}





