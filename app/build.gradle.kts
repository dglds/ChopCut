tasks.register("quick") {
    group = "verification"
    description = "Verificação rápida de sintaxe e imports (Java/Kotlin)"

    dependsOn(
        "compileDebugKotlin",
        "compileDebugJavaWithJavac"
    )
}

// ==================== Log Monitor Tasks ====================

tasks.register<Exec>("startLogMonitor") {
    group = "chopcut"
    description = "🚀 Inicia o servidor de monitoramento de logs em tempo real"

    workingDir = file("${project.rootDir}/log-monitor")
    commandLine = listOf("npm", "run", "monitor")

    doFirst {
        println("\u001B[36m╔══════════════════════════════════════════╗\u001B[0m")
        println("\u001B[36m║  🚀 Iniciando Log Monitor...             ║\u001B[0m")
        println("\u001B[36m╚══════════════════════════════════════════╝\u001B[0m")
        println("")
    }
}

tasks.register<Exec>("stopLogMonitor") {
    group = "chopcut"
    description = "🛑 Para o servidor de monitoramento de logs"

    commandLine = listOf("bash", "-c", "pkill -f 'node.*server.js' && pkill -f 'next dev' || echo 'Servidor não estava rodando'")

    doFirst {
        println("\u001B[36m🛑 Parando Log Monitor...\u001B[0m")
    }

    doLast {
        println("\u001B[36m✅ Log Monitor parado\u001B[0m")
    }
}

tasks.register<Exec>("openLogMonitor") {
    group = "chopcut"
    description = "🌐 Abre a interface do log monitor no navegador"

    val url = "http://localhost:3000"

    commandLine = when {
        org.gradle.internal.os.OperatingSystem.current().isLinux ->
            listOf("xdg-open", url)
        org.gradle.internal.os.OperatingSystem.current().isMacOsX ->
            listOf("open", url)
        org.gradle.internal.os.OperatingSystem.current().isWindows ->
            listOf("cmd", "/c", "start", url)
        else -> listOf("echo", "Sistema operacional não suportado")
    }

    doFirst {
        println("\u001B[36m🌐 Abrindo Log Monitor no navegador...\u001B[0m")
        println("\u001B[36m🌐 URL: ${url}\u001B[0m")
    }
}

tasks.register("logMonitor") {
    group = "chopcut"
    description = "📱 Inicia o log monitor completo (servidor + navegador)"

    dependsOn("startLogMonitor")

    doLast {
        println("")
        println("\u001B[36m╔══════════════════════════════════════════════╗\u001B[0m")
        println("\u001B[36m║  ✅ Log Monitor Iniciado!                    ║\u001B[0m")
        println("\u001B[36m║                                              ║\u001B[0m")
        println("\u001B[36m║  Aguarde o servidor inicializar...          ║\u001B[0m")
        println("\u001B[36m╚══════════════════════════════════════════════╝\u001B[0m")
        println("")

        // Aguardar o servidor inicializar
        Thread.sleep(5000)

        // Abrir navegador
        project.tasks.getByName("openLogMonitor").actions.forEach { it.execute(project.tasks.getByName("openLogMonitor")) }
    }
}

tasks.register<Exec>("checkLogMonitor") {
    group = "chopcut"
    description = "🔍 Verifica o status do log monitor"

    commandLine = listOf("bash", "-c", """
        if pgrep -f 'node.*server.js' > /dev/null && pgrep -f 'next dev' > /dev/null; then
            echo '\u001B[32m✅ Log Monitor está rodando\u001B[0m'
            echo '\u001B[36m🌐 Interface: http://localhost:3000\u001B[0m'
            echo '\u001B[36m🌐 API: http://localhost:8080\u001B[0m'
            exit 0
        else
            echo '\u001B[31m❌ Log Monitor não está rodando\u001B[0m'
            echo '\u001B[33m💡 Execute: ./gradlew logMonitor\u001B[0m'
            exit 1
        fi
    """.trimIndent())

    isIgnoreExitValue = true
}

tasks.register("installDebugWithMonitor") {
    group = "chopcut"
    description = "📦 Build, instala o app e inicia o log monitor"

    dependsOn("installDebug", "logMonitor")

    doLast {
        println("")
        println("\u001B[36m╔══════════════════════════════════════════════╗\u001B[0m")
        println("\u001B[36m║  🎉 App instalado e monitor iniciado!       ║\u001B[0m")
        println("\u001B[36m║                                              ║\u001B[0m")
        println("\u001B[36m║  Use 'Iniciar Servidor' no navegador        ║\u001B[0m")
        println("\u001B[36m╚══════════════════════════════════════════════╝\u001B[0m")
    }
}

// ==================== End Log Monitor Tasks ====================


plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.devtools.ksp)
    kotlin("plugin.parcelize")
}

android {
    namespace = "com.chopcut"
    compileSdk {
        version = release(36)
    }

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

    // ViewModel
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // ProcessLifecycleOwner
    implementation("androidx.lifecycle:lifecycle-process:2.8.7")

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

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
