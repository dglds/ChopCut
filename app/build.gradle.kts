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

import java.io.ByteArrayOutputStream

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





