package com.chopcut.runner

import androidx.test.runner.AndroidJUnitRunner

/**
 * Runner de testes instrumentados do ChopCut.
 *
 * Referenciado em `build.gradle.kts` via `testInstrumentationRunner`.
 * Mínimo por design — apenas estende [AndroidJUnitRunner]. Ponto único caso
 * precisemos de setup global de processo de teste no futuro.
 */
class ChopCutTestRunner : AndroidJUnitRunner()
