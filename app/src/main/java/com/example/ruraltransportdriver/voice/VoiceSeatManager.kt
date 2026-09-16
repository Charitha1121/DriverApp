package com.example.ruraltransportdriver.voice

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale

/**
 * Manages the hands-free voice prompt flow:
 * 1. Speaks "How many seats available?" via TextToSpeech.
 * 2. When TTS finishes, immediately listens via SpeechRecognizer.
 * 3. Enforces an 8-second watchdog timeout.
 * 4. Parses driver speech for numbers (0..totalSeats).
 * 5. On success, updates seat count.
 * 6. On failure/timeout, speaks an audio fallback and triggers manual UI fallback.
 */
class VoiceSeatManager(
    private val context: Context,
    private val mainHandler: Handler = Handler(Looper.getMainLooper())
) {

    companion object {
        private const val TAG = "VoiceSeatManager"
        private const val PROMPT_UTTERANCE_ID = "SEAT_PROMPT_UTTERANCE"
        private const val FALLBACK_UTTERANCE_ID = "SEAT_FALLBACK_UTTERANCE"
        private const val CONFIRM_UTTERANCE_ID = "SEAT_CONFIRM_UTTERANCE"
        private const val TIMEOUT_MILLIS = 8000L
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false
    private var speechRecognizer: SpeechRecognizer? = null

    private var isPromptActive = false
    private var currentTotalSeats = 4
    private var activeOnSeatsUpdated: ((Int) -> Unit)? = null
    private var activeOnFallbackRequested: (() -> Unit)? = null

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "Speech recognition timed out after ${TIMEOUT_MILLIS}ms")
        handleRecognitionFailure()
    }

    init {
        initTts()
    }

    private fun initTts() {
        mainHandler.post {
            tts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = tts?.setLanguage(Locale.getDefault())
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        tts?.setLanguage(Locale.ENGLISH)
                    }
                    isTtsReady = true
                    setupTtsListener()
                } else {
                    Log.e(TAG, "TextToSpeech initialization failed with status $status")
                }
            }
        }
    }

    private fun setupTtsListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}

            override fun onDone(utteranceId: String?) {
                if (utteranceId == PROMPT_UTTERANCE_ID) {
                    mainHandler.post {
                        startListening()
                    }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == PROMPT_UTTERANCE_ID) {
                    mainHandler.post {
                        startListening()
                    }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == PROMPT_UTTERANCE_ID) {
                    mainHandler.post {
                        startListening()
                    }
                }
            }
        })
    }

    /**
     * Prompts the driver for available seat count.
     */
    fun promptForSeats(
        totalSeats: Int,
        onSeatsUpdated: (Int) -> Unit,
        onFallbackRequested: () -> Unit
    ) {
        if (isPromptActive) {
            Log.d(TAG, "Prompt already in progress. Ignoring duplicate request.")
            return
        }

        isPromptActive = true
        currentTotalSeats = totalSeats
        activeOnSeatsUpdated = onSeatsUpdated
        activeOnFallbackRequested = onFallbackRequested

        mainHandler.post {
            if (isTtsReady && tts != null) {
                val params = Bundle()
                tts?.speak(
                    "How many seats available?",
                    TextToSpeech.QUEUE_FLUSH,
                    params,
                    PROMPT_UTTERANCE_ID
                )
            } else {
                // If TTS is not ready or failed, directly start speech recognizer
                startListening()
            }
        }
    }

    private fun startListening() {
        if (!isPromptActive) return

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.e(TAG, "SpeechRecognizer is not available on this device.")
            handleRecognitionFailure()
            return
        }

        try {
            cleanupRecognizer()

            // Use on-device speech recognizer on Android 13+ (API 33+) if available
            speechRecognizer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            } else {
                SpeechRecognizer.createSpeechRecognizer(context)
            }

            speechRecognizer?.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d(TAG, "SpeechRecognizer ready for speech.")
                }

                override fun onBeginningOfSpeech() {
                    // Driver started speaking, reset timeout to allow speaking
                    mainHandler.removeCallbacks(timeoutRunnable)
                    mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MILLIS)
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}

                override fun onError(error: Int) {
                    Log.w(TAG, "SpeechRecognizer error code: $error")
                    mainHandler.removeCallbacks(timeoutRunnable)
                    handleRecognitionFailure()
                }

                override fun onResults(results: Bundle?) {
                    mainHandler.removeCallbacks(timeoutRunnable)
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val parsed = SpeechNumberParser.parse(matches, currentTotalSeats)

                    if (parsed != null) {
                        handleRecognitionSuccess(parsed)
                    } else {
                        Log.w(TAG, "Could not parse number from transcripts: $matches")
                        handleRecognitionFailure()
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {}
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
            }

            speechRecognizer?.startListening(intent)

            // Arm 8-second watchdog timer
            mainHandler.removeCallbacks(timeoutRunnable)
            mainHandler.postDelayed(timeoutRunnable, TIMEOUT_MILLIS)

        } catch (e: Exception) {
            Log.e(TAG, "Exception starting speech recognition: ${e.localizedMessage}")
            handleRecognitionFailure()
        }
    }

    private fun handleRecognitionSuccess(seatCount: Int) {
        isPromptActive = false
        val callback = activeOnSeatsUpdated
        activeOnSeatsUpdated = null
        activeOnFallbackRequested = null

        cleanupRecognizer()

        // Short audible confirmation
        if (isTtsReady && tts != null) {
            tts?.speak(
                "$seatCount seats updated",
                TextToSpeech.QUEUE_FLUSH,
                null,
                CONFIRM_UTTERANCE_ID
            )
        }

        callback?.invoke(seatCount)
    }

    private fun handleRecognitionFailure() {
        if (!isPromptActive) return
        isPromptActive = false

        val fallbackCallback = activeOnFallbackRequested
        activeOnSeatsUpdated = null
        activeOnFallbackRequested = null

        cleanupRecognizer()

        // Short audible fallback prompt
        if (isTtsReady && tts != null) {
            tts?.speak(
                "Didn't catch that, please tap to update seats manually",
                TextToSpeech.QUEUE_FLUSH,
                null,
                FALLBACK_UTTERANCE_ID
            )
        }

        fallbackCallback?.invoke()
    }

    private fun cleanupRecognizer() {
        mainHandler.removeCallbacks(timeoutRunnable)
        try {
            speechRecognizer?.stopListening()
            speechRecognizer?.cancel()
            speechRecognizer?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning up SpeechRecognizer: ${e.localizedMessage}")
        }
        speechRecognizer = null
    }

    /**
     * Releases TextToSpeech and SpeechRecognizer resources.
     */
    fun destroy() {
        isPromptActive = false
        mainHandler.removeCallbacks(timeoutRunnable)
        cleanupRecognizer()

        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TextToSpeech: ${e.localizedMessage}")
        }
        tts = null
        isTtsReady = false
    }
}
