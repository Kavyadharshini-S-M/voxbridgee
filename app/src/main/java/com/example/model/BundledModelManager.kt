package com.example.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Pipeline specification v2.1.0 for on-device offline ASR/TTS.
 */
data class PipelineSpec(
    val project: String = "iTantra Offline ASR/TTS Speech Communication Pipeline",
    val version: String = "2.1.0",
    val timestamp: String = "2026-09-10T20:05:00+05:30",
    val ramBudgetMb: Float = 400.0f,
    val maxResidentAsrModels: Int = 1,
    val maxResidentTtsModels: Int = 1,
    val physicalAsrModelCount: Int = 2,
    val physicalTtsModelCount: Int = 7,
    val totalModelStorageMb: Float = 614.96f
)

/**
 * Detailed ASR Model specification from the 2.1.0 pipeline.
 */
data class AsrModelSpec(
    val engine: String,
    val model: String,
    val sizeMb: Float,
    val license: String,
    val offline: Boolean = true,
    val isShared: Boolean = false,
    val isFallback: Boolean = false
)

/**
 * Detailed TTS Model specification from the 2.1.0 pipeline.
 */
data class TtsModelSpec(
    val engine: String,
    val voice: String,
    val sizeMb: Float,
    val license: String,
    val offline: Boolean = true,
    val isFallback: Boolean = false
)

/**
 * Describes one language's real, on-device STT model as shipped in assets/models.
 */
data class SttModelAsset(
    val id: String,
    val name: String,
    val architecture: String,
    val sourceModel: String,
    val onnxExport: String,
    val modelType: String,
    val modelPath: String,
    val tokensPath: String,
    val sampleRateHz: Int,
    val featureDim: Int,
    val spec: AsrModelSpec? = null
)

data class TtsModelAsset(
    val id: String,
    val name: String,
    val acousticModelPath: String,
    val vocoderPath: String,
    val frontendConfigPath: String,
    val sampleRateHz: Int,
    val spec: TtsModelSpec? = null
)

data class LanguagePack(
    val code: String,
    val englishName: String,
    val nativeName: String = "",
    val stt: SttModelAsset?,
    val tts: TtsModelAsset?,
    val asrSpec: AsrModelSpec? = null,
    val ttsSpec: TtsModelSpec? = null
)

data class VadModelAsset(
    val id: String,
    val name: String,
    val modelPath: String,
    val sampleRateHz: Int,
    val windowSizeSamples: Int,
)

/**
 * File-level facts about one asset actually present on disk.
 */
data class VerifiedAsset(
    val path: String,
    val sizeBytes: Long,
    val sha256: String,
)

/**
 * Loads and exposes the real, data-driven language-pack manifest under
 * assets/models/model_manifest.json.
 */
class BundledModelManager(private val context: Context) {

    private val _pipelineSpec = MutableStateFlow<PipelineSpec?>(null)
    val pipelineSpec: StateFlow<PipelineSpec?> = _pipelineSpec.asStateFlow()

    private val _vadModel = MutableStateFlow<VadModelAsset?>(null)
    val vadModel: StateFlow<VadModelAsset?> = _vadModel.asStateFlow()

    private val _languagePacks = MutableStateFlow<Map<String, LanguagePack>>(emptyMap())
    val languagePacks: StateFlow<Map<String, LanguagePack>> = _languagePacks.asStateFlow()

    private val _isManifestLoaded = MutableStateFlow(false)
    val isManifestLoaded: StateFlow<Boolean> = _isManifestLoaded.asStateFlow()

    private val loadStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    private val loadCompleted = kotlinx.coroutines.CompletableDeferred<Unit>()

    private val _verifiedAssets = MutableStateFlow<Map<String, VerifiedAsset>>(emptyMap())
    val verifiedAssets: StateFlow<Map<String, VerifiedAsset>> = _verifiedAssets.asStateFlow()

    private val verificationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun loadAndVerifyBundledModels() {
        if (!loadStarted.compareAndSet(false, true)) {
            loadCompleted.await()
            return
        }
        withContext(Dispatchers.IO) {
            try {
                val manifestJson = context.assets.open("models/model_manifest.json")
                    .bufferedReader().use { it.readText() }
                val root = JSONObject(manifestJson)

                _pipelineSpec.value = PipelineSpec(
                    project = root.optString("project", "iTantra Offline ASR/TTS Speech Communication Pipeline"),
                    version = root.optString("version", "2.1.0"),
                    timestamp = root.optString("timestamp", "2026-09-10T20:05:00+05:30"),
                    ramBudgetMb = root.optDouble("ram_budget_mb", 400.0).toFloat(),
                    maxResidentAsrModels = root.optInt("max_resident_asr_models", 1),
                    maxResidentTtsModels = root.optInt("max_resident_tts_models", 1),
                    physicalAsrModelCount = root.optInt("physical_asr_model_count", 2),
                    physicalTtsModelCount = root.optInt("physical_tts_model_count", 7),
                    totalModelStorageMb = root.optDouble("total_model_storage_mb", 614.96).toFloat()
                )

                val vadObj = root.optJSONObject("vad")
                _vadModel.value = vadObj?.let {
                    VadModelAsset(
                        id = it.optString("id", "silero_vad"),
                        name = it.optString("name", "Silero VAD"),
                        modelPath = it.optString("file", "vad/silero_vad.onnx"),
                        sampleRateHz = it.optInt("sampleRateHz", 16000),
                        windowSizeSamples = it.optInt("windowSizeSamples", 512),
                    )
                }

                val languagesObj = root.optJSONObject("languages") ?: JSONObject()
                val packs = mutableMapOf<String, LanguagePack>()
                for (langCode in languagesObj.keys()) {
                    val langObj = languagesObj.getJSONObject(langCode)
                    val sttObj = langObj.optJSONObject("stt") ?: langObj.optJSONObject("asr")
                    val ttsObj = langObj.optJSONObject("tts")

                    val asrSpec = sttObj?.let {
                        val modelStr = it.optString("model", it.optString("name", ""))
                        AsrModelSpec(
                            engine = it.optString("engine", "DolphinSttEngine"),
                            model = modelStr,
                            sizeMb = it.optDouble("size_mb", 98.92).toFloat(),
                            license = it.optString("license", "Apache-2.0 / MIT"),
                            offline = it.optBoolean("offline", true),
                            isShared = modelStr.contains("shared", ignoreCase = true),
                            isFallback = modelStr.contains("fallback", ignoreCase = true)
                        )
                    }

                    val ttsSpec = ttsObj?.let {
                        val voiceStr = it.optString("voice", it.optString("name", ""))
                        TtsModelSpec(
                            engine = it.optString("engine", "SherpaVitsTtsEngine"),
                            voice = voiceStr,
                            sizeMb = it.optDouble("size_mb", 60.0).toFloat(),
                            license = it.optString("license", "MIT"),
                            offline = it.optBoolean("offline", true),
                            isFallback = voiceStr.contains("Fallback", ignoreCase = true)
                        )
                    }

                    val sttAsset = sttObj?.let {
                        val modelPath = it.optString("model_file", it.optString("model", "$langCode/stt/model.int8.onnx"))
                        val tokensPath = it.optString("tokens_file", it.optString("tokens", "$langCode/stt/tokens.txt"))
                        SttModelAsset(
                            id = it.optString("id", "${asrSpec?.engine ?: "stt"}_$langCode"),
                            name = it.optString("name", asrSpec?.model ?: "On-Device ASR ($langCode)"),
                            architecture = it.optString("architecture", "Conformer / CTC INT8"),
                            sourceModel = it.optString("sourceModel", asrSpec?.model ?: ""),
                            onnxExport = it.optString("onnxExport", "sherpa-onnx / onnxruntime"),
                            modelType = it.optString("modelType", "nemo_ctc"),
                            modelPath = modelPath,
                            tokensPath = tokensPath,
                            sampleRateHz = it.optInt("sampleRateHz", 16000),
                            featureDim = it.optInt("featureDim", 80),
                            spec = asrSpec
                        )
                    }

                    val ttsAsset = ttsObj?.let {
                        val acousticPath = it.optString("acoustic_file", it.optString("acoustic", "$langCode/tts/fastpitch.int8.onnx"))
                        val vocoderPath = it.optString("vocoder_file", it.optString("vocoder", "$langCode/tts/hifigan.onnx"))
                        val frontendPath = it.optString("frontend_file", it.optString("frontend", "$langCode/tts/frontend.json"))
                        TtsModelAsset(
                            id = it.optString("id", "${ttsSpec?.engine ?: "tts"}_$langCode"),
                            name = it.optString("name", ttsSpec?.voice ?: "On-Device TTS ($langCode)"),
                            acousticModelPath = acousticPath,
                            vocoderPath = vocoderPath,
                            frontendConfigPath = frontendPath,
                            sampleRateHz = it.optInt("sampleRateHz", 22050),
                            spec = ttsSpec
                        )
                    }

                    packs[langCode] = LanguagePack(
                        code = langCode,
                        englishName = langObj.optString("name", langObj.optString("englishName", langCode)),
                        nativeName = langObj.optString("native_name", ""),
                        stt = sttAsset,
                        tts = ttsAsset,
                        asrSpec = asrSpec,
                        ttsSpec = ttsSpec
                    )
                }
                _languagePacks.value = packs
                _isManifestLoaded.value = true
            } catch (e: Exception) {
                Log.e("BundledModelManager", "Error loading model manifest", e)
                _isManifestLoaded.value = false
            }
        }
        loadCompleted.complete(Unit)

        val packs = _languagePacks.value
        if (packs.isEmpty()) return

        verificationScope.launch {
            try {
                val toVerify = mutableListOf<String>()
                _vadModel.value?.let { if (it.modelPath.isNotBlank()) toVerify += it.modelPath }
                packs.values.forEach { pack ->
                    pack.stt?.let {
                        if (it.modelPath.isNotBlank()) toVerify += it.modelPath
                        if (it.tokensPath.isNotBlank()) toVerify += it.tokensPath
                    }
                    pack.tts?.let {
                        if (it.acousticModelPath.isNotBlank()) toVerify += it.acousticModelPath
                        if (it.vocoderPath.isNotBlank()) toVerify += it.vocoderPath
                        if (it.frontendConfigPath.isNotBlank()) toVerify += it.frontendConfigPath
                    }
                }
                val verified = coroutineScope {
                    toVerify.distinct().map { path -> async { path to verifyAsset(path) } }
                        .awaitAll()
                        .mapNotNull { (path, asset) -> asset?.let { path to it } }
                        .toMap()
                }
                _verifiedAssets.value = verified
                Log.i("BundledModelManager", "Verified ${verified.size}/${toVerify.distinct().size} bundled assets on disk")
            } catch (e: Exception) {
                Log.e("BundledModelManager", "Error verifying bundled assets", e)
            }
        }
    }

    private fun verifyAsset(assetPath: String): VerifiedAsset? {
        if (assetPath.isBlank()) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            context.assets.open("models/$assetPath").use { stream ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                    totalBytes += read
                }
            }
            val hex = digest.digest().joinToString("") { "%02x".format(it) }
            VerifiedAsset(path = assetPath, sizeBytes = totalBytes, sha256 = hex)
        } catch (e: Exception) {
            Log.w("BundledModelManager", "Asset missing or unreadable: models/$assetPath (${e.message})")
            null
        }
    }

    fun isLanguageSttReady(code: String): Boolean = _languagePacks.value[code]?.stt != null
    fun isLanguageTtsReady(code: String): Boolean = _languagePacks.value[code]?.tts != null
}
