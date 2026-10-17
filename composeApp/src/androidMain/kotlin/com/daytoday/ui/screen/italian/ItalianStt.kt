package com.daytoday.ui.screen.italian

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext

private const val TAG = "ItalianStt"

class ItalianStt internal constructor(private val context: Context) {
    var isListening by mutableStateOf(false)
        private set

    val available: Boolean = try {
        SpeechRecognizer.isRecognitionAvailable(context)
    } catch (t: Throwable) {
        false
    }

    private val handler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var disposed = false
    private var continuous = false
    private var retries = 0
    private var onResult: (String) -> Unit = {}
    private var onPartial: (String) -> Unit = {}
    private var language: String = "it-IT"

    init {
        Log.i(TAG, "created available=$available")
    }

    internal fun setOnResult(callback: (String) -> Unit) {
        onResult = callback
    }

    internal fun setOnPartial(callback: (String) -> Unit) {
        onPartial = callback
    }

    fun setLanguage(tag: String) {
        if (tag.isNotBlank()) language = tag
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.i(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.i(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            Log.i(TAG, "onEndOfSpeech")
        }

        override fun onError(error: Int) {
            Log.i(TAG, "onError code=$error continuous=$continuous")
            isListening = false
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> restart(delayMs = 200L)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
                SpeechRecognizer.ERROR_CLIENT -> {
                    if (retries < 3) {
                        retries++
                        restart(delayMs = 350L)
                    }
                }
            }
        }

        override fun onResults(results: Bundle?) {
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            Log.i(TAG, "onResults len=${text.length}")
            isListening = false
            continuous = false
            if (text.isNotBlank()) onResult(text)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val text = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                .orEmpty()
            if (text.isNotBlank()) onPartial(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun ensureRecognizer(): SpeechRecognizer? {
        if (disposed) return null
        recognizer?.let { return it }
        val created = try {
            SpeechRecognizer.createSpeechRecognizer(context)
        } catch (t: Throwable) {
            Log.i(TAG, "createSpeechRecognizer failed: ${t.message}")
            return null
        }
        created.setRecognitionListener(listener)
        recognizer = created
        return created
    }

    private fun recycleRecognizer() {
        try {
            recognizer?.destroy()
        } catch (t: Throwable) {
        }
        recognizer = null
    }

    private fun restart(delayMs: Long) {
        if (disposed || !continuous) return
        handler.postDelayed(
            {
                if (!disposed && continuous) start()
            },
            delayMs,
        )
    }

    fun start() {
        if (disposed || !available) {
            Log.i(TAG, "start ignored disposed=$disposed available=$available")
            return
        }
        val active = ensureRecognizer() ?: return
        continuous = true
        if (isListening) {
            try {
                active.cancel()
            } catch (t: Throwable) {
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            )
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
        try {
            active.startListening(intent)
            isListening = true
            Log.i(TAG, "startListening lang=$language")
        } catch (t: Throwable) {
            isListening = false
            Log.i(TAG, "startListening failed: ${t.message}")
        }
    }

    fun stop() {
        if (disposed) return
        continuous = false
        retries = 0
        if (!isListening) return
        try {
            recognizer?.stopListening()
        } catch (t: Throwable) {
        }
        isListening = false
    }

    internal fun destroy() {
        disposed = true
        continuous = false
        isListening = false
        handler.removeCallbacksAndMessages(null)
        recycleRecognizer()
    }
}

@Composable
fun rememberItalianStt(
    onResult: (String) -> Unit,
    onPartial: (String) -> Unit = {},
): ItalianStt {
    val context = LocalContext.current
    val currentOnResult by rememberUpdatedState(onResult)
    val currentOnPartial by rememberUpdatedState(onPartial)
    val stt = remember {
        ItalianStt(context.applicationContext).also {
            it.setOnResult { text -> currentOnResult(text) }
            it.setOnPartial { text -> currentOnPartial(text) }
        }
    }
    DisposableEffect(Unit) {
        onDispose { stt.destroy() }
    }
    return stt
}
