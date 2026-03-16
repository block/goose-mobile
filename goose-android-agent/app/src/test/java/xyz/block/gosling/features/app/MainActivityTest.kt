package xyz.block.gosling.features.app

import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.android.controller.ActivityController
import org.robolectric.annotation.Config
import xyz.block.gosling.FakeAndroidKeyStore
import xyz.block.gosling.GoslingApplication
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MainActivityTest {

    private lateinit var mainActivity: ActivityController<MainActivity>

    @Before
    fun setUp() {
        FakeAndroidKeyStore.setUp()
        mainActivity = Robolectric.buildActivity(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        mainActivity.close()
        FakeAndroidKeyStore.tearDown()
    }

    @Test
    fun lifecycle_initialState_notRunning() {
        assertFalse(GoslingApplication.isMainActivityRunning)
    }

    @Test
    fun lifecycle_whenResumed_isRunning() {
        mainActivity.create().start().resume()
        
        assertTrue(GoslingApplication.isMainActivityRunning)
    }

    @Test
    fun lifecycle_whenPaused_notRunning() {
        mainActivity.create().start().resume()
        assertTrue(GoslingApplication.isMainActivityRunning)
        
        mainActivity.pause()
        
        assertFalse(GoslingApplication.isMainActivityRunning)
    }

    @Test
    fun lifecycle_resumeAfterPause_isRunningAgain() {
        mainActivity.create().start().resume()
        mainActivity.pause()
        assertFalse(GoslingApplication.isMainActivityRunning)
        
        mainActivity.resume()
        
        assertTrue(GoslingApplication.isMainActivityRunning)
    }

    @Test
    fun lifecycle_whenDestroyed_notRunning() {
        mainActivity.create().start().resume()
        assertTrue(GoslingApplication.isMainActivityRunning)
        
        mainActivity.pause().stop().destroy()
        
        assertFalse(GoslingApplication.isMainActivityRunning)
    }

    @Test
    fun overlay_whenMainActivityRunning_shouldHide() {
        assertFalse(GoslingApplication.shouldHideOverlay())
        
        mainActivity.create().start().resume()
        
        assertTrue(GoslingApplication.shouldHideOverlay())
    }

    @Test
    fun overlay_whenMainActivityPaused_shouldShow() {
        mainActivity.create().start().resume()
        assertTrue(GoslingApplication.shouldHideOverlay())
        
        mainActivity.pause()
        
        assertFalse(GoslingApplication.shouldHideOverlay())
    }
}
