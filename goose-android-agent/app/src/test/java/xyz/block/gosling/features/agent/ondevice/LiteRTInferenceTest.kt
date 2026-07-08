package xyz.block.gosling.features.agent.ondevice

import org.junit.Test
import kotlin.test.assertTrue

/**
 * JVM unit test for [LiteRTInference]'s lightweight surface.
 *
 * The actual `initialize()` path can't run here because it loads the
 * native `liblitertlm_jni.so` — that's covered by the instrumented test
 * in androidTest/. We can still verify the dependency-availability check
 * since `Class.forName` works on the test classpath when the LiteRT-LM
 * AAR is on `implementation`.
 */
class LiteRTInferenceTest {

    @Test
    fun isAvailable_returnsTrue_whenLiteRtLmDependencyOnClasspath() {
        // The LiteRT-LM AAR is declared as `implementation` in build.gradle.kts,
        // so its classes are on the unit-test classpath. A regression that drops
        // the dependency would flip this to false.
        assertTrue(LiteRTInference.isAvailable())
    }
}
