
package com.ats.sentinel

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class ATSSpeechInput(
    context: Context,
    private val onReady: () -> Unit,
    private val onPartialResult: (String) -> Unit,
    private val onFinalResult: (String) -> Unit,
    private val onError: (String) -> Unit
) {

    companion object {
        const val ARABIC = "ar-EG"
        const val ENGLISH = "en-US"
    }

    private val appContext = context.applicationContext

    private var recognizer: SpeechRecognizer? = null
    private var isListening = false

    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(appContext)
    }

    fun start(languageTag: String = ARABIC) {

        if (!isAvailable()) {
            onError("Speech recognition is unavailable")
            return
        }

        if (isListening) {
            stop()
        }

        try {
            recognizer?.destroy()

            recognizer =
                SpeechRecognizer.createSpeechRecognizer(appContext)

            recognizer?.setRecognitionListener(
                object : RecognitionListener {

                    override fun onReadyForSpeech(params: Bundle?) {
                        isListening = true
                        onReady()
                    }

                    override fun onBeginningOfSpeech() {}

                    override fun onRmsChanged(rmsdB: Float) {}

                    override fun onBufferReceived(buffer: ByteArray?) {}

                    override fun onEndOfSpeech() {
                        isListening = false
                    }

                    override fun onError(error: Int) {
                        isListening = false
                        onError(errorMessage(error))
                    }

                    override fun onResults(results: Bundle?) {
                        isListening = false

                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val recognizedText = matches
                            ?.firstOrNull()
                            ?.trim()

                        if (!recognizedText.isNullOrEmpty()) {
                            onFinalResult(recognizedText)
                        } else {
                            onError("No speech detected")
                        }
                    }

                    override fun onPartialResults(results: Bundle?) {
                        val matches = results?.getStringArrayList(
                            SpeechRecognizer.RESULTS_RECOGNITION
                        )

                        val text = matches?.firstOrNull()

                        if (!text.isNullOrBlank()) {
                            onPartialResult(text)
                        }
                    }

                    override fun onEvent(
                        eventType: Int,
                        params: Bundle?
                    ) {}
                }
            )

            val intent = Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH
            ).apply {

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE,
                    languageTag
                )

                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,
                    languageTag
                )

                putExtra(
                    RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                    true
                )

                putExtra(
                    RecognizerIntent.EXTRA_MAX_RESULTS,
                    3
                )
            }

            recognizer?.startListening(intent)

        } catch (_: Exception) {
            isListening = false
            onError("Unable to start speech recognition")
        }
    }

    fun stop() {
        try {
            recognizer?.stopListening()
        } catch (_: Exception) {
        }

        isListening = false
    }

    fun destroy() {
        try {
            recognizer?.cancel()
            recognizer?.destroy()
        } catch (_: Exception) {
        }

        recognizer = null
        isListening = false
    }

    private fun errorMessage(error: Int): String {
        return when (error) {
            SpeechRecognizer.ERROR_AUDIO ->
                "Audio recording error"

            SpeechRecognizer.ERROR_CLIENT ->
                "Recognition client error"

            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                "Microphone permission is required"

            SpeechRecognizer.ERROR_NETWORK ->
                "Network error"

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                "Network timeout"

            SpeechRecognizer.ERROR_NO_MATCH ->
                "No speech match found"

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
                "Speech recognizer is busy"

            SpeechRecognizer.ERROR_SERVER ->
                "Speech recognition server error"

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                "No speech detected"

            else ->
                "Speech recognition error: $error"
        }
    }
}
