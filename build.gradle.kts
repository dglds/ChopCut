// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}

task<Exec>("logs") {
    group = "application"
    description = "Abre o monitor de logs no navegador"

    val logFile = file("logs.html")
    
    doFirst {
        if (!logFile.exists()) {
            throw GradleException("Arquivo logs.html não encontrado em ${projectDir}")
        }
    }

    val os = System.getProperty("os.name").lowercase()
    commandLine = when {
        os.contains("win") -> listOf("cmd", "/c", "start", "", logFile.absolutePath)
        os.contains("mac") -> listOf("open", logFile.absolutePath)
        else -> listOf("xdg-open", logFile.absolutePath)
    }
}