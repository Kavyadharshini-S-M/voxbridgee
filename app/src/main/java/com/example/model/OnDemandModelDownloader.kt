package com.example.model

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "OnDemandModelDownloader"

/**
 * Manages on-demand in-app downloading and caching of offline speech models.
 */
class OnDemandModelDownloader(
    private val context: Context,
    private val scope: CoroutineScope
) {
    // Map of language code to download progress percentage (0..100) or -1 for error
    private val _downloadProgress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Int>> = _downloadProgress.asStateFlow()

    private val _downloadingLanguages = MutableStateFlow<Set<String>>(emptySet())
    val downloadingLanguages: StateFlow<Set<String>> = _downloadingLanguages.asStateFlow()

    /**
     * Checks if a language model is already available locally (either in assets or cache).
     */
    fun isLanguageModelAvailable(lang: SupportedLanguage): Boolean {
        // Check cache on disk first
        val cachedModel = File(context.filesDir, "models_cache/${lang.code}/stt/model.int8.onnx")
        val cachedTokens = File(context.filesDir, "models_cache/${lang.code}/stt/tokens.txt")
        if (cachedModel.exists() && cachedModel.length() > 10_000_000 && cachedTokens.exists()) {
            return true
        }

        // Check bundled APK assets
        return try {
            context.assets.open("models/${lang.code}/stt/model.int8.onnx").close()
            context.assets.open("models/${lang.code}/stt/tokens.txt").close()
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Downloads and prepares speech models for [lang] directly into internal storage.
     */
    fun downloadLanguageModel(
        lang: SupportedLanguage,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        val langCode = lang.code
        if (_downloadingLanguages.value.contains(langCode)) return

        _downloadingLanguages.value = _downloadingLanguages.value + langCode
        _downloadProgress.value = _downloadProgress.value + (langCode to 0)

        scope.launch(Dispatchers.IO) {
            try {
                val targetDir = File(context.filesDir, "models_cache/$langCode/stt")
                targetDir.mkdirs()

                val modelFile = File(targetDir, "model.int8.onnx")
                val tokensFile = File(targetDir, "tokens.txt")

                // HuggingFace model URLs
                val hfBase = "https://huggingface.co/trysem/indicconformer-120m-onnx/resolve/main/$langCode"
                val tokensUrl = "$hfBase/vocab.json"
                val modelUrl = "$hfBase/model.onnx"

                // 1. Download Tokens/Vocabulary
                downloadDirectFile(
                    urlStr = tokensUrl,
                    destFile = tokensFile,
                    onProgress = { percent ->
                        _downloadProgress.value = _downloadProgress.value + (langCode to (percent * 0.05f).toInt())
                    }
                )

                // Convert vocab.json to tokens.txt if needed
                if (tokensFile.exists() && tokensFile.readText().startsWith("[")) {
                    formatJsonVocabToTokens(tokensFile)
                }

                // 2. Download Model Weights
                downloadDirectFile(
                    urlStr = modelUrl,
                    destFile = modelFile,
                    onProgress = { percent ->
                        val totalProgress = 5 + (percent * 0.95f).toInt()
                        _downloadProgress.value = _downloadProgress.value + (langCode to totalProgress)
                    }
                )

                _downloadProgress.value = _downloadProgress.value + (langCode to 100)
                _downloadingLanguages.value = _downloadingLanguages.value - langCode

                withContext(Dispatchers.Main) {
                    onSuccess()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to download model for $langCode: ${e.message}", e)
                _downloadProgress.value = _downloadProgress.value + (langCode to -1)
                _downloadingLanguages.value = _downloadingLanguages.value - langCode
                withContext(Dispatchers.Main) {
                    onError(e.message ?: "Download failed")
                }
            }
        }
    }

    private fun downloadDirectFile(
        urlStr: String,
        destFile: File,
        onProgress: (Int) -> Unit
    ) {
        val url = URL(urlStr)
        val connection = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "VoxBridge-Android-Client")
        }

        val totalLength = connection.contentLength
        connection.inputStream.use { input ->
            FileOutputStream(destFile).use { output ->
                val buffer = ByteArray(64 * 1024)
                var bytesRead: Int
                var totalRead = 0L
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    totalRead += bytesRead
                    if (totalLength > 0) {
                        val percent = ((totalRead * 100) / totalLength).toInt()
                        onProgress(percent.coerceIn(0, 100))
                    }
                }
            }
        }
    }

    private fun formatJsonVocabToTokens(tokensFile: File) {
        try {
            val jsonText = tokensFile.readText(Charsets.UTF_8)
            val jsonArray = org.json.JSONArray(jsonText)
            val sb = StringBuilder()
            for (i in 0 until jsonArray.length()) {
                val token = jsonArray.getString(i)
                sb.append("$token $i\n")
            }
            sb.append("<blk> ${jsonArray.length()}\n")
            tokensFile.writeText(sb.toString(), Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing vocab JSON: ${e.message}")
        }
    }
}
