package com.daytoday.ui.screen.italian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.network.ChatMessageDto
import com.daytoday.network.DayTodayApi
import com.daytoday.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class VoiceStatus { Idle, Listening, Thinking, Speaking }

data class SpeechChunk(val text: String, val lang: String)

data class VoiceTurn(
    val role: String,
    val text: String,
    val segments: List<SpeechChunk> = emptyList(),
)

data class VoiceUiState(
    val status: VoiceStatus = VoiceStatus.Idle,
    val turns: List<VoiceTurn> = emptyList(),
    val partial: String = "",
    val error: String? = null,
    val thinkingText: String = "",
)

private const val GREETING = "Ciao! Sono Sofia, la tua tutor. Come stai oggi?"
private const val MAX_CHUNK_LENGTH = 120

private val NON_WORD = Regex("[^a-zàèéìòùç']+")

private val IT_WORDS = setOf(
    "il", "lo", "la", "i", "gli", "le", "un", "uno", "una", "che", "chi", "cui", "di", "del", "dello",
    "della", "dei", "degli", "delle", "a", "al", "allo", "alla", "ai", "agli", "alle", "da", "dal",
    "dallo", "dalla", "dai", "dagli", "dalle", "in", "nel", "nello", "nella", "nei", "negli", "nelle",
    "con", "su", "sul", "sullo", "sulla", "sui", "sugli", "sulle", "per", "tra", "fra", "e", "ed", "o",
    "od", "ma", "se", "non", "è", "sono", "sei", "siamo", "siete", "ciao", "grazie", "prego", "salve",
    "oggi", "domani", "ieri", "come", "cosa", "dove", "quando", "perché", "molto", "bene", "male", "più",
    "meno", "anche", "ancora", "sempre", "mai", "adesso", "ora", "qui", "qua", "tutto", "tutti", "tutta",
    "tutte", "questo", "questa", "questi", "queste", "quello", "quella", "quelli", "quelle", "mi", "ti",
    "si", "ci", "vi", "tu", "io", "lui", "lei", "noi", "voi", "loro", "mio", "mia", "tuo", "tua", "suo",
    "sua", "nostro", "vostra", "parliamo", "parlare", "parli", "parla", "senti", "senta", "ecco",
    "significa", "vuol", "dire", "puoi", "posso", "voglio", "vorrei", "piace", "piacere", "preferito",
    "preferita", "parola", "frase", "buongiorno", "buonasera", "buonanotte", "arrivederci", "favore",
    "scusa", "scusi", "essere", "avere", "fare", "andare", "venire", "davvero", "prova", "magari",
    "forse", "solo", "dopo", "prima", "mentre", "quindi", "però", "allora", "sì", "no", "niente",
    "qualcosa", "qualcuno", "ogni", "volevo"
)

private val EN_WORDS = setOf(
    "the", "a", "an", "is", "are", "am", "was", "were", "be", "been", "being", "to", "of", "and", "or",
    "but", "if", "not", "no", "yes", "you", "your", "yours", "i", "he", "she", "it", "we", "they", "me",
    "him", "her", "us", "them", "my", "his", "its", "our", "their", "this", "that", "these", "those",
    "in", "on", "at", "for", "with", "from", "by", "about", "here", "there", "what", "how", "when",
    "where", "why", "who", "which", "means", "mean", "say", "said", "can", "could", "will", "would",
    "should", "do", "does", "did", "have", "has", "had", "want", "like", "good", "morning", "night",
    "hello", "hi", "thanks", "thank", "please", "quick", "explanation", "common", "greeting", "casual",
    "way", "ask", "someone", "feeling", "reply", "talk", "food", "travel", "hobbies", "anything",
    "prefer", "italian", "english", "translate", "word", "sentence", "today", "also", "just", "so",
    "very", "well", "useful", "used", "use", "great", "question", "yourself", "literally", "actually",
    "basically", "then", "something", "everyone", "again", "okay", "ok", "yeah", "sure"
)

@HiltViewModel
class VoiceViewModel @Inject constructor(
    private val api: DayTodayApi,
    private val settings: SettingsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VoiceUiState())
    val uiState: StateFlow<VoiceUiState> = _uiState

    private val _listenRequests = MutableSharedFlow<Unit>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val listenRequests: SharedFlow<Unit> = _listenRequests

    private val _speechQueue = MutableSharedFlow<SpeechChunk>(extraBufferCapacity = 64)
    val speechQueue: SharedFlow<SpeechChunk> = _speechQueue

    private var streamJob: Job? = null

    @Volatile
    private var ttsIdle = true

    fun startSession() {
        streamJob?.cancel()
        streamJob = null
        ttsIdle = true
        _uiState.value = VoiceUiState(
            status = VoiceStatus.Speaking,
            turns = listOf(
                VoiceTurn("assistant", GREETING, listOf(SpeechChunk(GREETING, "it"))),
            ),
        )
        emitSpeech(SpeechChunk(GREETING, "it"))
    }

    fun onUserPartial(text: String) {
        if (_uiState.value.status != VoiceStatus.Listening) return
        _uiState.value = _uiState.value.copy(partial = text)
    }

    fun onUserSaid(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (_uiState.value.status != VoiceStatus.Listening) return

        val baseTurns = _uiState.value.turns + VoiceTurn("user", trimmed)
        val history = baseTurns.map { ChatMessageDto(it.role, it.text) }
        _uiState.value = _uiState.value.copy(
            status = VoiceStatus.Thinking,
            turns = baseTurns,
            partial = "",
            error = null,
            thinkingText = "",
        )

        streamJob = viewModelScope.launch {
            val reply = StringBuilder()
            val speech = StringBuilder()
            try {
                val token = settings.authToken.first()
                api.streamItalianChat(token, history) { delta, _ ->
                    if (delta.isNotEmpty()) {
                        reply.append(delta)
                        speech.append(delta)
                        flushChunks(speech)

                        val live = reply.toString()
                        _uiState.value = _uiState.value.copy(
                            turns = baseTurns + VoiceTurn("assistant", live, liveSegments(live)),
                            thinkingText = live,
                        )
                    }
                }
                val remainder = speech.toString().trim()
                speech.setLength(0)
                if (remainder.isNotBlank()) emitSpeech(SpeechChunk(remainder, detectLang(remainder)))
                _uiState.value = _uiState.value.copy(status = VoiceStatus.Speaking, thinkingText = "")
                if (ttsIdle) onSpeechFinished()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val turns = _uiState.value.turns
                val cleaned = if (
                    turns.lastOrNull()?.role == "assistant" && turns.last().text.isBlank()
                ) {
                    turns.dropLast(1)
                } else {
                    turns
                }
                _uiState.value = _uiState.value.copy(
                    turns = cleaned,
                    error = e.message ?: "Errore di rete",
                    thinkingText = "",
                )
                delay(1200)
                if (_uiState.value.status == VoiceStatus.Listening) {
                    _listenRequests.tryEmit(Unit)
                }
            }
        }
    }

    fun onSpeechFinished() {
        ttsIdle = true
        if (_uiState.value.status == VoiceStatus.Speaking) {
            _uiState.value = _uiState.value.copy(status = VoiceStatus.Listening, thinkingText = "")
            _listenRequests.tryEmit(Unit)
        }
    }

    fun stopSession() {
        streamJob?.cancel()
        streamJob = null
        ttsIdle = true
        _uiState.value = VoiceUiState()
    }

    private fun emitSpeech(chunk: SpeechChunk) {
        if (chunk.text.isBlank()) return
        ttsIdle = false
        if (!_speechQueue.tryEmit(chunk)) {
            ttsIdle = true
        }
    }

    private fun liveSegments(text: String): List<SpeechChunk> {
        val out = mutableListOf<SpeechChunk>()
        var start = 0
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '.' || c == '!' || c == '?' || c == '…') {
                addSegment(out, text.substring(start, i + 1))
                start = i + 1
            }
            i += 1
        }
        if (start < text.length) addSegment(out, text.substring(start))
        return out
    }

    private fun addSegment(out: MutableList<SpeechChunk>, piece: String) {
        if (piece.isBlank()) return
        val lang = detectLang(piece)
        val last = out.lastOrNull()
        if (last != null && last.lang == lang) {
            out[out.size - 1] = last.copy(text = last.text + piece)
        } else {
            out.add(SpeechChunk(piece, lang))
        }
    }

    private fun detectLang(text: String): String {
        var it = 0
        var en = 0
        for (w in text.lowercase().split(NON_WORD)) {
            if (w.isEmpty()) continue
            if (w in IT_WORDS) it++
            if (w in EN_WORDS) en++
        }
        if (text.any { it == 'à' || it == 'è' || it == 'é' || it == 'ì' || it == 'ò' || it == 'ù' }) it++
        return if (en > it) "en" else "it"
    }

    private fun flushChunks(buffer: StringBuilder) {
        while (true) {
            val cut = sentenceCut(buffer.toString()) ?: break
            val chunk = buffer.substring(0, cut).trim()
            buffer.delete(0, cut)
            if (chunk.isNotBlank() && chunk.any { it.isLetterOrDigit() }) {
                emitSpeech(SpeechChunk(chunk, detectLang(chunk)))
            }
        }
    }

    private fun sentenceCut(text: String): Int? {
        for (index in text.indices) {
            val c = text[index]
            if (c == '.' || c == '!' || c == '?' || c == '…') return index + 1
        }
        if (text.length > MAX_CHUNK_LENGTH) {
            val space = text.lastIndexOf(' ', (MAX_CHUNK_LENGTH - 1).coerceAtMost(text.length - 1))
            return if (space > 0) space + 1 else MAX_CHUNK_LENGTH
        }
        return null
    }
}
