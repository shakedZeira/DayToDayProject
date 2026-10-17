package com.daytoday.ui.screen.italian

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class VoiceTts internal constructor(
    private val context: Context,
    private val onIdle: () -> Unit,
) {
    var speaking by mutableStateOf(false)
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val lock = Any()

    private val queue = ArrayDeque<SpeechRequest>()
    private val ready = CompletableDeferred<Unit>()
    private var enginesRequested = 0
    private var enginesSettled = 0
    private var draining = false
    private var destroyed = false
    private var hadWork = false
    private var counter = 0
    private var pending: CompletableDeferred<Unit>? = null

    private var italian: TextToSpeech? = null
    private var english: TextToSpeech? = null

    private val listener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit

        override fun onDone(utteranceId: String?) {
            completePending()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            completePending()
        }
    }

    init {
        italian = createEngine(Locale.ITALIAN)
        english = createEngine(Locale.ENGLISH)
    }

    private fun createEngine(locale: Locale): TextToSpeech? {
        synchronized(lock) { enginesRequested++ }
        return try {
            val engine = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    if (locale == Locale.ITALIAN) {
                        italian?.setLanguage(Locale.ITALIAN)
                    } else {
                        english?.setLanguage(Locale.ENGLISH)
                    }
                }
                engineSettled()
            }
            engine.setOnUtteranceProgressListener(listener)
            engine
        } catch (t: Throwable) {
            engineSettled()
            null
        }
    }

    private fun engineSettled() {
        synchronized(lock) {
            enginesSettled++
            Log.i("VoiceTts", "engineSettled $enginesSettled/$enginesRequested")
            if (enginesRequested >= 2 && enginesSettled >= enginesRequested) {
                ready.complete(Unit)
            }
        }
    }

    fun speak(text: String, lang: String) {
        if (text.isBlank() || destroyed) return
        val normalized = if (lang.equals("en", ignoreCase = true)) "en" else "it"
        val start = synchronized(lock) {
            queue.addLast(SpeechRequest(text, normalized, "voice_${counter++}"))
            hadWork = true
            if (draining) {
                false
            } else {
                draining = true
                true
            }
        }
        if (start) scope.launch { runDrain() }
    }

    private suspend fun runDrain() {
        val readyInTime = withTimeoutOrNull(8000L) {
            ready.await()
            true
        } ?: false
        if (!readyInTime) Log.i("VoiceTts", "engines not ready after 8s; speaking anyway")
        while (true) {
            drainQueue()
            val more = synchronized(lock) {
                if (queue.isEmpty()) {
                    draining = false
                    false
                } else {
                    true
                }
            }
            if (!more) break
        }
    }

    private suspend fun drainQueue() {
        while (true) {
            val next = synchronized(lock) { queue.removeFirstOrNull() } ?: break
            val engine = if (next.lang == "en") english else italian
            if (engine == null) continue
            val done = CompletableDeferred<Unit>()
            synchronized(lock) { pending = done }
            speaking = true
            val result = try {
                engine.speak(next.text, TextToSpeech.QUEUE_ADD, null, next.id)
            } catch (t: Throwable) {
                TextToSpeech.ERROR
            }
            if (result == TextToSpeech.ERROR) {
                completePending()
            }
            done.await()
        }
        speaking = false
        val notify = synchronized(lock) {
            val had = hadWork
            hadWork = false
            had
        }
        if (notify && !destroyed) {
            Log.i("VoiceTts", "queue drained -> onIdle")
            onIdle()
        }
    }

    private fun completePending() {
        val deferred = synchronized(lock) {
            val current = pending
            pending = null
            current
        }
        deferred?.complete(Unit)
    }

    fun stop() {
        synchronized(lock) {
            queue.clear()
            hadWork = false
            val current = pending
            pending = null
            current?.complete(Unit)
        }
        try {
            italian?.stop()
        } catch (t: Throwable) {
        }
        try {
            english?.stop()
        } catch (t: Throwable) {
        }
        speaking = false
    }

    internal fun destroy() {
        destroyed = true
        scope.cancel()
        synchronized(lock) {
            queue.clear()
            hadWork = false
            val current = pending
            pending = null
            current?.complete(Unit)
        }
        try {
            italian?.stop()
        } catch (t: Throwable) {
        }
        try {
            english?.stop()
        } catch (t: Throwable) {
        }
        try {
            italian?.shutdown()
        } catch (t: Throwable) {
        }
        try {
            english?.shutdown()
        } catch (t: Throwable) {
        }
        italian = null
        english = null
    }

    private data class SpeechRequest(val text: String, val lang: String, val id: String)
}

@Composable
fun rememberVoiceTts(onIdle: () -> Unit): VoiceTts {
    val context = LocalContext.current
    val currentOnIdle by rememberUpdatedState(onIdle)
    val tts = remember {
        VoiceTts(context.applicationContext) { currentOnIdle() }
    }
    DisposableEffect(Unit) {
        onDispose { tts.destroy() }
    }
    return tts
}
