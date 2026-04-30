package xyz.block.gosling.features.settings

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.block.gosling.features.agent.ondevice.OnDeviceEngine
import xyz.block.gosling.features.agent.ondevice.OnDeviceModelInfo
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Robolectric Compose UI tests for [OnDeviceModelDropdown].
 *
 * Verifies the per-row download badge and the routing decision (select vs.
 * navigate) — the two pieces of behavior the on-device picker adds on top
 * of a regular dropdown.
 *
 * Runs on the debug variant only; the release variant's unit-test task is
 * disabled in build.gradle.kts because its merged manifest doesn't include
 * the `ComponentActivity` that `createComposeRule()` requires.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnDeviceModelDropdownTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val e2b = OnDeviceModelInfo(
        id = "on-device/gemma4-e2b",
        displayName = "Gemma 4 E2B",
        fileName = "gemma-4-E2B-it.litertlm",
        downloadUrl = "https://example.com/e2b.litertlm",
        sizeBytes = 2_583_085_056L,
        contextLength = 32000,
        engine = OnDeviceEngine.LITERT
    )

    private val e4b = OnDeviceModelInfo(
        id = "on-device/gemma4-e4b",
        displayName = "Gemma 4 E4B",
        fileName = "gemma-4-E4B-it.litertlm",
        downloadUrl = "https://example.com/e4b.litertlm",
        sizeBytes = 3_654_467_584L,
        contextLength = 32000,
        engine = OnDeviceEngine.LITERT
    )

    @Test
    fun emptyKnownModels_rendersDisabledNoModelsField() {
        composeRule.setContent {
            OnDeviceModelDropdown(
                knownModels = emptyList(),
                downloadedModels = emptyList(),
                selectedModelId = "",
                onModelSelected = {},
                onUndownloadedClicked = {}
            )
        }
        composeRule.onNodeWithText("No models available").assertIsDisplayed()
    }

    @Test
    fun emptySelection_rendersSelectAModelPlaceholder() {
        composeRule.setContent {
            OnDeviceModelDropdown(
                knownModels = listOf(e2b, e4b),
                downloadedModels = emptyList(),
                selectedModelId = "",
                onModelSelected = {},
                onUndownloadedClicked = {}
            )
        }
        composeRule.onNodeWithText("Select a model").assertIsDisplayed()
    }

    @Test
    fun rendersSelectedModelDisplayName() {
        composeRule.setContent {
            OnDeviceModelDropdown(
                knownModels = listOf(e2b, e4b),
                downloadedModels = listOf(e2b),
                selectedModelId = e2b.id,
                onModelSelected = {},
                onUndownloadedClicked = {}
            )
        }
        composeRule.onNodeWithText("Gemma 4 E2B").assertIsDisplayed()
    }

    @Test
    fun expandedDropdown_showsAllKnownModelsRegardlessOfDownloadStatus() {
        composeRule.setContent {
            OnDeviceModelDropdown(
                knownModels = listOf(e2b, e4b),
                downloadedModels = listOf(e2b),
                selectedModelId = e2b.id,
                onModelSelected = {},
                onUndownloadedClicked = {}
            )
        }
        // Open the dropdown by tapping the field.
        composeRule.onNodeWithText("Gemma 4 E2B").performClick()

        // E2B appears twice now: as the field value and as a menu item.
        assertTrue(
            composeRule.onAllNodesWithText("Gemma 4 E2B").fetchSemanticsNodes().size >= 2,
            "E2B should appear in both field and menu"
        )
        composeRule.onNodeWithText("Gemma 4 E4B").assertIsDisplayed()
    }

    @Test
    fun undownloadedItem_showsNotDownloadedSubtext() {
        composeRule.setContent {
            OnDeviceModelDropdown(
                knownModels = listOf(e2b, e4b),
                downloadedModels = listOf(e2b),
                selectedModelId = e2b.id,
                onModelSelected = {},
                onUndownloadedClicked = {}
            )
        }
        composeRule.onNodeWithText("Gemma 4 E2B").performClick()
        composeRule.onNodeWithText("Not downloaded · Tap to download").assertIsDisplayed()
    }

    @Test
    fun downloadedItems_haveNoNotDownloadedSubtext() {
        composeRule.setContent {
            OnDeviceModelDropdown(
                knownModels = listOf(e2b, e4b),
                downloadedModels = listOf(e2b, e4b),
                selectedModelId = e2b.id,
                onModelSelected = {},
                onUndownloadedClicked = {}
            )
        }
        composeRule.onNodeWithText("Gemma 4 E2B").performClick()
        // Neither E2B nor E4B should show the un-downloaded tag.
        assertEquals(
            0,
            composeRule.onAllNodesWithText("Not downloaded · Tap to download")
                .fetchSemanticsNodes().size
        )
    }

    @Test
    fun clickDownloadedItem_invokesOnModelSelected() {
        var selected: OnDeviceModelInfo? = null
        var navigated: OnDeviceModelInfo? = null
        composeRule.setContent {
            OnDeviceModelDropdown(
                knownModels = listOf(e2b, e4b),
                downloadedModels = listOf(e2b, e4b), // both downloaded
                selectedModelId = e2b.id,
                onModelSelected = { selected = it },
                onUndownloadedClicked = { navigated = it }
            )
        }
        composeRule.onNodeWithText("Gemma 4 E2B").performClick() // open dropdown
        composeRule.onNodeWithText("Gemma 4 E4B").performClick() // pick E4B

        assertEquals(e4b.id, selected?.id)
        assertNull(navigated)
    }

    @Test
    fun clickUndownloadedItem_routesToUndownloadedHandler() {
        var selected: OnDeviceModelInfo? = null
        var navigated: OnDeviceModelInfo? = null
        composeRule.setContent {
            OnDeviceModelDropdown(
                knownModels = listOf(e2b, e4b),
                downloadedModels = listOf(e2b),
                selectedModelId = e2b.id,
                onModelSelected = { selected = it },
                onUndownloadedClicked = { navigated = it }
            )
        }
        composeRule.onNodeWithText("Gemma 4 E2B").performClick() // open
        composeRule.onNodeWithText("Gemma 4 E4B").performClick() // pick E4B (not downloaded)

        assertNull(selected, "Undownloaded tap must not change selection")
        assertEquals(e4b.id, navigated?.id, "Undownloaded tap must route to manage models")
    }
}
