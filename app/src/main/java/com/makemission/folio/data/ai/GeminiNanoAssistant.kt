package com.makemission.folio.data.ai

import com.google.mlkit.genai.common.DownloadStatus
import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.prompt.Generation

/** Uses Android AICore's on-device Gemini Nano model through ML Kit Prompt API. */
object GeminiNanoAssistant {
    private val model by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { Generation.getClient() }

    suspend fun availability(): Int = model.checkStatus()

    suspend fun explainPassage(
        passage: String,
        onProgress: (String) -> Unit = {},
    ): String {
        val status = model.checkStatus()
        when (status) {
            FeatureStatus.AVAILABLE -> Unit
            FeatureStatus.DOWNLOADABLE, FeatureStatus.DOWNLOADING -> {
                onProgress("Preparing Gemini Nano on this device…")
                model.download().collect { downloadStatus ->
                    when (downloadStatus) {
                        is DownloadStatus.DownloadStarted -> onProgress("Downloading Gemini Nano…")
                        is DownloadStatus.DownloadProgress -> onProgress("Downloading Gemini Nano…")
                        DownloadStatus.DownloadCompleted -> onProgress("Getting your explanation…")
                        is DownloadStatus.DownloadFailed -> throw downloadStatus.e
                    }
                }
                if (model.checkStatus() != FeatureStatus.AVAILABLE) {
                    throw IllegalStateException("Gemini Nano is not ready on this device yet.")
                }
            }
            else -> throw IllegalStateException("Gemini Nano is not available on this device.")
        }

        onProgress("Thinking about this passage…")
        val request = """Explain the meaning and significance of this passage in plain language for a curious reader. Stay grounded in the quoted text, avoid inventing context, and keep the answer under 120 words.

Passage:
${passage.take(2_400)}""".trimIndent()
        return model.generateContent(request).candidates.firstOrNull()?.text.orEmpty().ifBlank {
            "Folio couldn't form an explanation for this passage. Try selecting a shorter excerpt."
        }
    }
}
