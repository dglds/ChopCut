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
}

// ==================== DEPLOYMENT TASKS ====================

val scriptsDir = rootDir.resolve("gradle/scripts")

/**
 * Deploy completo: build + install + restart
 * Executa script: gradle/scripts/deploy.sh
 */
tasks.register<Exec>("deploy") {
    group = "deploy"
    description = "Build, install, kill and restart the app"
    dependsOn("assembleDebug")
    workingDir = rootDir
    commandLine("bash", scriptsDir.resolve("deploy.sh"))
}

/**
 * Instala APK apenas (sem restart)
 * Executa script: gradle/scripts/install.sh
 */
tasks.register<Exec>("install") {
    group = "deploy"
    description = "Install APK only (no restart)"
    dependsOn("assembleDebug")
    workingDir = rootDir
    commandLine("bash", scriptsDir.resolve("install.sh"))
}

/**
 * Conecta via WiFi
 * Executa script: gradle/scripts/wifi.sh
 */
tasks.register<Exec>("wifi") {
    group = "deploy"
    description = "Connect to device via WiFi"
    workingDir = rootDir
    commandLine("bash", scriptsDir.resolve("wifi.sh"))
}

