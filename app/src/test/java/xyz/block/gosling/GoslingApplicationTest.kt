package xyz.block.gosling

import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.block.gosling.features.app.MainActivity
import xyz.block.gosling.features.launcher.LauncherActivity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class GoslingApplicationTest {
    @Before
    fun setUp() {
        FakeAndroidKeyStore.setUp()
    }

    @After
    fun tearDown() {
        FakeAndroidKeyStore.tearDown()
    }

    @Test
    fun overlay_withoutAnyActivityRunning_shouldShow() {
        assertFalse { GoslingApplication.shouldHideOverlay() }
    }

    @Test
    fun overlay_withMainActivityRunning_shouldHide() {
        Robolectric.buildActivity(MainActivity::class.java).use {
            it.create().start().resume()
            assertTrue { GoslingApplication.shouldHideOverlay() }
        }
    }

    @Test
    fun overlay_withLauncherActivityRunning_shouldHide() {
        Robolectric.buildActivity(LauncherActivity::class.java).use {
            it.create().start().resume()
            assertTrue { GoslingApplication.shouldHideOverlay() }
        }
    }

    @Test
    fun runningState_mainActivityResumed_mainActivityRunningIsTrue() {
        Robolectric.buildActivity(MainActivity::class.java).use {
            it.create().start().resume()
            assertTrue { GoslingApplication.isMainActivityRunning }
        }
    }

    @Test
    fun runningState_mainActivityNotResumed_mainActivityRunningIsFalse() {
        Robolectric.buildActivity(MainActivity::class.java).use {
            it.create()
            assertFalse { GoslingApplication.isMainActivityRunning }

            it.start()
            assertFalse { GoslingApplication.isMainActivityRunning }

            it.resume().pause()
            assertFalse { GoslingApplication.isMainActivityRunning }

            it.stop()
            assertFalse { GoslingApplication.isMainActivityRunning }

            it.destroy()
            assertFalse { GoslingApplication.isMainActivityRunning }
        }
    }

    @Test
    fun runningState_launcherActivityResumed_launcherActivityRunningIsTrue() {
        Robolectric.buildActivity(LauncherActivity::class.java).use {
            it.create().start().resume()
            assertTrue { GoslingApplication.isLauncherActivityRunning }
        }
    }

    @Test
    fun runningState_launcherActivityNotResumed_launcherActivityIsFalse() {
        Robolectric.buildActivity(LauncherActivity::class.java).use {
            it.create()
            assertFalse { GoslingApplication.isLauncherActivityRunning }

            it.start()
            assertFalse { GoslingApplication.isLauncherActivityRunning }

            it.resume().pause()
            assertFalse { GoslingApplication.isLauncherActivityRunning }

            it.stop()
            assertFalse { GoslingApplication.isLauncherActivityRunning }

            it.destroy()
            assertFalse { GoslingApplication.isLauncherActivityRunning }
        }
    }
}