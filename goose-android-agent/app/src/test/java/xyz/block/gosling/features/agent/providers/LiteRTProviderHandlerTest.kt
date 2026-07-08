package xyz.block.gosling.features.agent.providers

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.block.gosling.features.agent.ParameterDef
import xyz.block.gosling.features.agent.SerializableToolDefinitions
import xyz.block.gosling.features.agent.Tool
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * JVM unit tests for the parts of [LiteRTProviderHandler] that don't touch
 * the native LiteRT-LM engine.
 *
 * The engine path (executeLocal -> Engine(config).initialize -> JNI) is
 * covered by the instrumented test in androidTest/, since it requires the
 * native .so to be loaded.
 *
 * Robolectric is needed so `org.json.JSONObject` has a real implementation;
 * otherwise the chained `put()` calls return null under the project's
 * `unitTests.isReturnDefaultValues = true` policy.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LiteRTProviderHandlerTest {

    private val handler = LiteRTProviderHandler()

    @Test
    fun isLocalProvider_returnsTrue() {
        assertTrue(handler.isLocalProvider())
    }

    @Test
    fun getApiUrl_returnsEmpty_forAnyInput() {
        assertEquals("", handler.getApiUrl("any-model", "any-key"))
        assertEquals("", handler.getApiUrl("any-model", null))
    }

    @Test
    fun getHeaders_returnsEmpty_forAnyKey() {
        assertTrue(handler.getHeaders("key").isEmpty())
        assertTrue(handler.getHeaders(null).isEmpty())
    }

    @Test
    fun createRequest_returnsEmpty() {
        val out = handler.createRequest(
            modelIdentifier = "any",
            messages = emptyList(),
            tools = SerializableToolDefinitions.OpenAITools(emptyList()),
            apiKey = "key"
        )
        assertEquals("", out)
    }

    @Test
    fun createToolDefinitions_filtersToEssentialTools() {
        val methods = MockToolBag::class.java.declaredMethods.filter {
            it.isAnnotationPresent(Tool::class.java)
        }

        val defs = handler.createToolDefinitions(methods) as SerializableToolDefinitions.OpenAITools
        val names = defs.definitions.map { it.function.name }.toSet()

        // Essential ones in our fixture
        assertTrue("getUiHierarchy" in names, "essential tool getUiHierarchy must pass through")
        assertTrue("click" in names, "essential tool click must pass through")
        assertTrue("openUrl" in names, "essential tool openUrl must pass through")

        // Non-essential ones must be filtered out
        assertTrue("nonEssentialTool" !in names, "non-essential tool must be filtered out")
        assertTrue("alsoFiltered" !in names, "non-essential tool must be filtered out")
    }

    @Test
    fun createToolDefinitions_emptyList_returnsEmptyDefinitions() {
        val defs = handler.createToolDefinitions(emptyList()) as SerializableToolDefinitions.OpenAITools
        assertTrue(defs.definitions.isEmpty())
    }

    @Test
    fun createToolDefinitions_preservesParametersAndRequiredFlags() {
        val methods = MockToolBag::class.java.declaredMethods.filter {
            it.getAnnotation(Tool::class.java)?.name == "click"
        }

        val defs = handler.createToolDefinitions(methods) as SerializableToolDefinitions.OpenAITools
        assertEquals(1, defs.definitions.size)
        val click = defs.definitions.first()
        assertEquals("click", click.function.name)

        val params = click.function.parameters
        assertTrue("x" in params.properties.keys)
        assertTrue("y" in params.properties.keys)
        assertEquals("number", params.properties["x"]?.type)

        // Both required in the fixture
        assertTrue("x" in params.required)
        assertTrue("y" in params.required)
    }

    @Test
    fun parseResponse_extractsText() {
        val json = JSONObject().put("text", "hello world")
        val (text, _, _) = handler.parseResponse(json, 1234.0)
        assertEquals("hello world", text)
    }

    @Test
    fun parseResponse_extractsToolCalls() {
        val tcArray = JSONArray().put(
            JSONObject()
                .put("id", "abc")
                .put("name", "click")
                .put("arguments", JSONObject().put("x", 100).put("y", 200))
        )
        val json = JSONObject().put("text", "").put("tool_calls", tcArray)

        val (_, calls, _) = handler.parseResponse(json, 100.0)
        assertNotNull(calls)
        assertEquals(1, calls.size)
        assertEquals("abc", calls[0].toolId)
        assertEquals("click", calls[0].name)
        assertEquals(100, calls[0].arguments.optInt("x"))
        assertEquals(200, calls[0].arguments.optInt("y"))
    }

    @Test
    fun parseResponse_returnsNullToolCalls_whenAbsent() {
        val json = JSONObject().put("text", "no tools here")
        val (_, calls, _) = handler.parseResponse(json, 0.0)
        assertNull(calls)
    }

    @Test
    fun parseResponse_includesDurationStat() {
        val json = JSONObject().put("text", "")
        val (_, _, stats) = handler.parseResponse(json, 9999.0)
        assertEquals(9999.0, stats["duration"])
    }

    @Test
    fun condenseSystemPrompt_alwaysIncludesBaseInstructions() {
        val condensed = handler.condenseSystemPrompt("")
        assertTrue(condensed.contains("autonomous phone assistant"))
        assertTrue(condensed.contains("getUiHierarchy"))
    }

    @Test
    fun condenseSystemPrompt_keepsInstalledAppsSection() {
        val original = """
            You are a verbose assistant with many examples to drop.

            The phone has the following apps installed:
            - Chrome
            - Calendar
            - Maps

            Before getting started, do something verbose.
        """.trimIndent()

        val condensed = handler.condenseSystemPrompt(original)
        assertTrue(condensed.contains("The phone has the following apps installed"))
        assertTrue(condensed.contains("Chrome"))
        assertTrue(condensed.contains("Calendar"))
        assertTrue(condensed.contains("Maps"))
        // The boundary (Before getting started…) should NOT bleed in
        assertTrue(!condensed.contains("Before getting started"))
    }

    @Test
    fun condenseSystemPrompt_keepsScreenResolution() {
        val original = "Some text. The device has a screen resolution of 1080x2400 pixels for layout."
        val condensed = handler.condenseSystemPrompt(original)
        assertTrue(condensed.contains("1080x2400"))
    }

    @Test
    fun condenseSystemPrompt_keepsUserMemories() {
        val original = """
            Some intro.

            USER PREFS, FACTS AND IMPORTANT MEMORIES
            - User prefers metric units.
            - User's name is Alex.

            The phone has the following apps installed:
            - Chrome
        """.trimIndent()

        val condensed = handler.condenseSystemPrompt(original)
        assertTrue(condensed.contains("USER PREFS"))
        assertTrue(condensed.contains("metric"))
        assertTrue(condensed.contains("Alex"))
    }

    @Test
    fun condenseSystemPrompt_missingSections_doesNotCrash() {
        val original = "Plain prompt with nothing extractable."
        val condensed = handler.condenseSystemPrompt(original)
        // Base prompt is always present
        assertTrue(condensed.contains("autonomous phone assistant"))
        // None of the optional sections leaked in
        assertTrue(!condensed.contains("The phone has the following apps installed"))
        assertTrue(!condensed.contains("USER PREFS"))
    }

    /**
     * Test fixture: a class with annotated methods used to exercise
     * createToolDefinitions filtering behavior. The names are chosen to mix
     * essential tools (getUiHierarchy, click, openUrl) with arbitrary
     * non-essential ones.
     */
    private class MockToolBag {
        @Tool(name = "getUiHierarchy", description = "Get UI", parameters = [])
        fun getUiHierarchy() {}

        @Tool(
            name = "click",
            description = "Click at coords",
            parameters = [
                ParameterDef(name = "x", type = "number", description = "X coord", required = true),
                ParameterDef(name = "y", type = "number", description = "Y coord", required = true)
            ]
        )
        fun click(x: Int, y: Int) {}

        @Tool(
            name = "openUrl",
            description = "Open a URL",
            parameters = [
                ParameterDef(name = "url", type = "string", description = "URL", required = true)
            ]
        )
        fun openUrl(url: String) {}

        @Tool(name = "nonEssentialTool", description = "should be filtered", parameters = [])
        fun nonEssentialTool() {}

        @Tool(name = "alsoFiltered", description = "also filtered", parameters = [])
        fun alsoFiltered() {}
    }
}
