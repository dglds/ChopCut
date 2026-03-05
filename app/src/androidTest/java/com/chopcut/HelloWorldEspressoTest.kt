package com.chopcut

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Hello world Espresso — verifica que a MainActivity sobe sem crash.
 *
 * Rodar:
 *   ./gradlew runTest -Ptarget=com.chopcut.HelloWorldEspressoTest#app_launches_without_crash
 * ou:
 *   ./gradlew connectedAndroidTest --tests "com.chopcut.HelloWorldEspressoTest"
 */
@RunWith(AndroidJUnit4::class)
class HelloWorldEspressoTest {

    @get:Rule
    val activityRule = ActivityScenarioRule(MainActivity::class.java)

    @Test
    fun app_launches_without_crash() {
        activityRule.scenario.onActivity { activity ->
            assertNotNull("MainActivity deve ser criada com sucesso", activity)
        }
    }
}
