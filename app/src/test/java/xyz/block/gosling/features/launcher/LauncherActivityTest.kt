package xyz.block.gosling.features.launcher

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import xyz.block.gosling.GoslingApplication
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class LauncherActivityTest {
    private lateinit var launcherActivity: ActivityController<LauncherActivity>

    @Before
    fun setUp() {
        launcherActivity = Robolectric.buildActivity(LauncherActivity::class.java)
    }

    @After
    fun tearDown() {
        launcherActivity.close()
    }

    @Test
    fun lifecycle_runningState_succeed() {
        assertFalse { GoslingApplication.isLauncherActivityRunning }

        launcherActivity.resume()
        assertTrue { GoslingApplication.isLauncherActivityRunning }

        launcherActivity.pause()
        assertFalse { GoslingApplication.isLauncherActivityRunning }

        launcherActivity.resume()
        assertTrue { GoslingApplication.isLauncherActivityRunning }

        launcherActivity.pause()
        launcherActivity.stop()
        assertFalse { GoslingApplication.isLauncherActivityRunning }

        launcherActivity.destroy()
        assertFalse { GoslingApplication.isLauncherActivityRunning }
    }


}