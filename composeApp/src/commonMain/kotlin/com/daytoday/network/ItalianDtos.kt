package com.daytoday.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer

@Serializable
data class ItalianPairDto(val left: String, val right: String)

@Serializable
@JsonClassDiscriminator("kind")
@OptIn(ExperimentalSerializationApi::class)
sealed interface ItalianExerciseDto {
    val id: String

    @Serializable
    @SerialName("choice")
    data class Choice(
        override val id: String,
        val direction: String,
        val prompt: String,
        val choices: List<String>,
        val answerIndex: Int,
        val speak: String? = null,
    ) : ItalianExerciseDto

    @Serializable
    @SerialName("match")
    data class Match(
        override val id: String,
        val pairs: List<ItalianPairDto>,
    ) : ItalianExerciseDto

    /**
     * Read-aloud exercise built on the device from a lesson's own phrases. The server
     * never sends it (see [KnownExercisesSerializer]) and never sees its answers.
     */
    @Serializable
    @SerialName("speak")
    data class Speak(
        override val id: String,
        /** English meaning shown under the sentence. */
        val prompt: String,
        /** The Italian sentence the learner has to say. */
        val target: String,
    ) : ItalianExerciseDto
}

/**
 * Drops exercises whose `kind` this app version cannot render (e.g. the removed
 * "type" writing exercises still served by an older backend), so one unknown
 * exercise cannot make the whole course fail to load.
 */
object KnownExercisesSerializer : JsonTransformingSerializer<List<ItalianExerciseDto>>(
    ListSerializer(ItalianExerciseDto.serializer())
) {
    private val knownKinds = setOf("choice", "match")

    override fun transformDeserialize(element: JsonElement): JsonElement =
        JsonArray(
            (element as? JsonArray).orEmpty().filter {
                ((it as? JsonObject)?.get("kind") as? JsonPrimitive)?.content in knownKinds
            }
        )
}

@Serializable
data class ItalianLessonDto(
    val id: String,
    val title: String,
    @Serializable(with = KnownExercisesSerializer::class)
    val exercises: List<ItalianExerciseDto>,
)

@Serializable
data class ItalianUnitDto(val id: String, val title: String, val lessons: List<ItalianLessonDto>)

@Serializable
data class ItalianCourseDto(val language: String, val units: List<ItalianUnitDto>)

@Serializable
@JsonClassDiscriminator("kind")
@OptIn(ExperimentalSerializationApi::class)
sealed interface ItalianAnswerDto {
    val exerciseId: String

    @Serializable
    @SerialName("choice")
    data class Choice(override val exerciseId: String, val choiceIndex: Int) : ItalianAnswerDto

    @Serializable
    @SerialName("match")
    data class Match(override val exerciseId: String, val pairs: List<ItalianPairDto>) : ItalianAnswerDto

    /** What speech recognition heard; blank when the learner skipped. Never sent to the server. */
    @Serializable
    @SerialName("speak")
    data class Speak(override val exerciseId: String, val heard: String) : ItalianAnswerDto
}

@Serializable
data class ItalianReviewDueDto(val lessonId: String, val exerciseId: String)

@Serializable
data class ItalianPathProgressDto(
    val xp: Int,
    val streak: Int,
    val completedLessonIds: List<String>,
    val totalLessons: Int,
    val reviewDue: List<ItalianReviewDueDto>,
)

@Serializable
data class ItalianSubmitResultDto(
    val results: List<ItalianExerciseResultDto>,
    val xpGained: Int,
    val lessonCompleted: Boolean,
    val progress: ItalianPathProgressDto,
)

@Serializable
data class ItalianExerciseResultDto(val exerciseId: String, val isCorrect: Boolean)

@Serializable
data class ItalianSubmitRequest(val answers: List<ItalianAnswerDto>)

@Serializable
data class ChatMessageDto(val role: String, val content: String)

@Serializable
data class ItalianChatRequest(val messages: List<ChatMessageDto>)

@Serializable
data class ItalianChatResponse(val reply: String, val correction: String = "", val translation: String = "")

@Serializable
data class ChatStreamLine(val delta: String? = null, val lang: String? = null, val done: Boolean = false)

object ItalianGrading {
    private val precomposedAccents = mapOf(
        "à" to "a", "á" to "a", "â" to "a", "ã" to "a", "ä" to "a", "å" to "a",
        "è" to "e", "é" to "e", "ê" to "e", "ë" to "e",
        "ì" to "i", "í" to "i", "î" to "i", "ï" to "i",
        "ò" to "o", "ó" to "o", "ô" to "o", "õ" to "o", "ö" to "o",
        "ù" to "u", "ú" to "u", "û" to "u", "ü" to "u",
        "ç" to "c", "ñ" to "n",
    )

    fun normalize(s: String): String {
        var out = s.lowercase()
        for ((from, to) in precomposedAccents) out = out.replace(from, to)
        return out
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[.,!?;:\"'’…]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Speech recognition is imperfect, so a spoken answer passes when it is the target
     * allowing small slips: accents and punctuation are ignored, and at most about one
     * wrong letter in five is forgiven.
     */
    fun speakingMatches(target: String, heard: String): Boolean {
        val want = normalize(target)
        val got = normalize(heard)
        if (want.isEmpty() || got.isEmpty()) return false
        if (want == got) return true
        val distance = editDistance(want, got)
        return distance <= maxOf(1, want.length / 5)
    }

    private fun editDistance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    fun grade(ex: ItalianExerciseDto, answer: ItalianAnswerDto): Boolean = when (ex) {
        is ItalianExerciseDto.Speak ->
            answer is ItalianAnswerDto.Speak && speakingMatches(ex.target, answer.heard)
        is ItalianExerciseDto.Choice ->
            answer is ItalianAnswerDto.Choice && answer.choiceIndex == ex.answerIndex
        is ItalianExerciseDto.Match -> {
            if (answer !is ItalianAnswerDto.Match) false
            else {
                if (answer.pairs.size != ex.pairs.size) false
                else {
                    val key = { p: ItalianPairDto -> "${normalize(p.left)}|${normalize(p.right)}" }
                    val have = answer.pairs.map(key).toSet()
                    ex.pairs.all { have.contains(key(it)) }
                }
            }
        }
    }
}
