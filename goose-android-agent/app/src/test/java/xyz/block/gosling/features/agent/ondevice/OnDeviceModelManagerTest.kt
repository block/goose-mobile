package xyz.block.gosling.features.agent.ondevice

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import xyz.block.gosling.features.agent.AiModel
import xyz.block.gosling.features.agent.ModelProvider
import java.io.File
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Robolectric tests for [OnDeviceModelManager]: registry loading, file-system
 * model discovery, formatting helpers, and AiModel registration.
 *
 * Network/DownloadManager paths are not exercised here because they require
 * real Android system services; they're tested manually on device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OnDeviceModelManagerTest {

    private lateinit var context: Context
    private lateinit var modelsDir: File
    private var savedLocale: Locale? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()

        // Lock locale for the formatSize tests so "%.1f" produces a dot
        // regardless of the host JVM's default locale.
        savedLocale = Locale.getDefault()
        Locale.setDefault(Locale.US)

        // Reset the manager's static cache so tests don't see each other's data.
        OnDeviceModelManager.reloadModelsConfig(context)

        // Reset AiModel's mutable on-device registry so registration tests
        // start with a known state.
        AiModel.getModelsForProvider(ModelProvider.ON_DEVICE_LITERT).forEach {
            AiModel.unregisterOnDeviceModel(it.identifier)
        }

        // Ensure models dir is empty before each test.
        modelsDir = File(context.getExternalFilesDir(null), "models")
        modelsDir.mkdirs()
        modelsDir.listFiles()?.forEach { it.delete() }
    }

    @After
    fun tearDown() {
        AiModel.getModelsForProvider(ModelProvider.ON_DEVICE_LITERT).forEach {
            AiModel.unregisterOnDeviceModel(it.identifier)
        }
        modelsDir.listFiles()?.forEach { it.delete() }
        savedLocale?.let { Locale.setDefault(it) }
    }

    // ---- Registry loading ----

    @Test
    fun getKnownModels_loadsFromBundledAssets() {
        val models = OnDeviceModelManager.getKnownModels(context)
        assertTrue(models.isNotEmpty(), "Should load models from bundled asset")
        assertTrue(models.all { it.engine == OnDeviceEngine.LITERT })
    }

    @Test
    fun getKnownModels_includesGemma4E2B() {
        val e2b = OnDeviceModelManager.getKnownModels(context)
            .find { it.id == "on-device/gemma4-e2b" }
        assertNotNull(e2b, "Should include Gemma 4 E2B")
        assertEquals("Gemma 4 E2B", e2b.displayName)
        assertEquals("gemma-4-E2B-it.litertlm", e2b.fileName)
        assertEquals(32000, e2b.contextLength)
    }

    @Test
    fun getKnownModels_isCachedAcrossCalls() {
        val first = OnDeviceModelManager.getKnownModels(context)
        val second = OnDeviceModelManager.getKnownModels(context)
        assertTrue(first === second, "Cached call should return the same list instance")
    }

    @Test
    fun reloadModelsConfig_clearsCache() {
        val first = OnDeviceModelManager.getKnownModels(context)
        OnDeviceModelManager.reloadModelsConfig(context)
        val second = OnDeviceModelManager.getKnownModels(context)
        assertFalse(first === second, "Reload should produce a fresh list instance")
        assertEquals(first, second, "Reloaded list should equal the original by content")
    }

    @Test
    fun getKnownModels_userOverrideTakesPrecedenceOverBundledAsset() {
        File(modelsDir, "models_litert.json").writeText(
            """[{
              "id": "override/test",
              "displayName": "Override Model",
              "fileName": "override.litertlm",
              "downloadUrl": "https://example.com/override.litertlm",
              "sizeBytes": 1024,
              "contextLength": 512
            }]""".trimIndent()
        )
        OnDeviceModelManager.reloadModelsConfig(context)

        val models = OnDeviceModelManager.getKnownModels(context)
        assertEquals(1, models.size)
        assertEquals("override/test", models.first().id)
        assertEquals(512, models.first().contextLength)
    }

    @Test
    fun getKnownModels_malformedOverride_returnsEmpty() {
        File(modelsDir, "models_litert.json").writeText("{ this is not valid json")
        OnDeviceModelManager.reloadModelsConfig(context)

        // Override is found but parsing fails -> empty list (the manager logs but doesn't crash).
        val models = OnDeviceModelManager.getKnownModels(context)
        assertTrue(models.isEmpty(), "Malformed override JSON should yield empty list, not crash")
    }

    // ---- Model path resolution ----

    @Test
    fun getModelPath_unknownIdentifier_returnsNull() {
        assertNull(OnDeviceModelManager.getModelPath(context, "no/such/model"))
    }

    @Test
    fun getModelPath_knownIdButFileMissing_returnsNull() {
        assertNull(OnDeviceModelManager.getModelPath(context, "on-device/gemma4-e2b"))
    }

    @Test
    fun getModelPath_fileExists_returnsAbsolutePath() {
        val expected = File(modelsDir, "gemma-4-E2B-it.litertlm").apply {
            writeBytes(ByteArray(8))
        }

        val path = OnDeviceModelManager.getModelPath(context, "on-device/gemma4-e2b")
        assertEquals(expected.absolutePath, path)
    }

    @Test
    fun getModelPath_partialDownload_returnsNull() {
        // .part suffix indicates an in-progress download; should not be reported as a usable model.
        File(modelsDir, "gemma-4-E2B-it.litertlm.part").writeBytes(ByteArray(8))
        assertNull(OnDeviceModelManager.getModelPath(context, "on-device/gemma4-e2b"))
    }

    // ---- Downloaded-models discovery ----

    @Test
    fun getDownloadedModels_emptyDir_returnsEmpty() {
        assertTrue(OnDeviceModelManager.getDownloadedModels(context).isEmpty())
    }

    @Test
    fun getDownloadedModels_filtersOutPartFiles() {
        File(modelsDir, "gemma-4-E2B-it.litertlm.part").writeBytes(ByteArray(8))
        assertTrue(OnDeviceModelManager.getDownloadedModels(context).isEmpty())
    }

    @Test
    fun getDownloadedModels_filtersOutNonLitertlmFiles() {
        File(modelsDir, "random.txt").writeBytes(ByteArray(8))
        File(modelsDir, "config.json").writeBytes(ByteArray(8))
        assertTrue(OnDeviceModelManager.getDownloadedModels(context).isEmpty())
    }

    @Test
    fun getDownloadedModels_returnsMatchingKnownModels() {
        File(modelsDir, "gemma-4-E2B-it.litertlm").writeBytes(ByteArray(8))

        val downloaded = OnDeviceModelManager.getDownloadedModels(context)
        assertEquals(1, downloaded.size)
        assertEquals("on-device/gemma4-e2b", downloaded.first().id)
    }

    @Test
    fun getDownloadedModels_ignoresUnknownLitertlmFiles() {
        // File whose name doesn't match any registry entry should be ignored.
        File(modelsDir, "stranger.litertlm").writeBytes(ByteArray(8))

        assertTrue(OnDeviceModelManager.getDownloadedModels(context).isEmpty())
    }

    // ---- Context length lookup ----

    @Test
    fun getContextLength_knownModel_returnsConfiguredLength() {
        assertEquals(32000, OnDeviceModelManager.getContextLength(context, "on-device/gemma4-e2b"))
    }

    @Test
    fun getContextLength_unknownModel_returnsDefault() {
        // Default is 2048 per OnDeviceModelManager.getContextLength
        assertEquals(2048, OnDeviceModelManager.getContextLength(context, "no/such/model"))
    }

    // ---- Registration ----

    @Test
    fun registerDownloadedModels_registersInAiModel() {
        File(modelsDir, "gemma-4-E2B-it.litertlm").writeBytes(ByteArray(8))

        OnDeviceModelManager.registerDownloadedModels(context)

        val registered = AiModel.getModelsForProvider(ModelProvider.ON_DEVICE_LITERT)
        assertEquals(1, registered.size)
        assertEquals("on-device/gemma4-e2b", registered.first().identifier)
        assertEquals("Gemma 4 E2B", registered.first().displayName)
    }

    @Test
    fun registerDownloadedModels_idempotent() {
        File(modelsDir, "gemma-4-E2B-it.litertlm").writeBytes(ByteArray(8))

        OnDeviceModelManager.registerDownloadedModels(context)
        OnDeviceModelManager.registerDownloadedModels(context)

        val registered = AiModel.getModelsForProvider(ModelProvider.ON_DEVICE_LITERT)
        assertEquals(1, registered.size, "Calling register twice should not duplicate entries")
    }

    @Test
    fun registerDownloadedModels_noFiles_registersNothing() {
        OnDeviceModelManager.registerDownloadedModels(context)
        assertTrue(AiModel.getModelsForProvider(ModelProvider.ON_DEVICE_LITERT).isEmpty())
    }

    // ---- formatSize ----

    @Test
    fun formatSize_subMegabyte_formattedAsKilobytes() {
        assertEquals("0.5 KB", OnDeviceModelManager.formatSize(500))
        assertEquals("1.0 KB", OnDeviceModelManager.formatSize(1_000))
        // 999_400 = 999.4 KB; values closer to 1_000_000 round up to "1000.0 KB",
        // which is awkward but consistent with the impl's threshold.
        assertEquals("999.4 KB", OnDeviceModelManager.formatSize(999_400))
    }

    @Test
    fun formatSize_megabyteRange_formattedAsMegabytes() {
        assertEquals("1.0 MB", OnDeviceModelManager.formatSize(1_000_000))
        assertEquals("12.3 MB", OnDeviceModelManager.formatSize(12_345_678))
    }

    @Test
    fun formatSize_gigabyteRange_formattedAsGigabytes() {
        assertEquals("1.0 GB", OnDeviceModelManager.formatSize(1_000_000_000))
        assertEquals("2.6 GB", OnDeviceModelManager.formatSize(2_583_085_056L))
    }
}
