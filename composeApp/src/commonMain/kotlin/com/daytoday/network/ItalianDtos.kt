package com.daytoday.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

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
    @SerialName("type")
    data class Type(
        override val id: String,
        val prompt: String,
        val accepted: List<String>,
    ) : ItalianExerciseDto

    @Serializable
    @SerialName("match")
    data class Match(
        override val id: String,
        val pairs: List<ItalianPairDto>,
    ) : ItalianExerciseDto
}

@Serializable
data class ItalianLessonDto(val id: String, val title: String, val exercises: List<ItalianExerciseDto>)

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
    @SerialName("type")
    data class Type(override val exerciseId: String, val text: String) : ItalianAnswerDto

    @Serializable
    @SerialName("match")
    data class Match(override val exerciseId: String, val pairs: List<ItalianPairDto>) : ItalianAnswerDto
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

object ItalianGrading {
    private val precomposedAccents = mapOf(
        "à" to "a", "á" to "a", "â" to "a", "ã" to "a", "ä" to "a", "å" to "a",
        "è" to "e", "é" to "e", "ê" to "e", "ë" to "e",
        "ì" to "i", "í" to "i", "î" to "i", "ï" to "i",
        "ò" to "o", "ó" to "o", "ô" to "o", "õ" to "o", "ö" to "o",
        "ù" to "u", "ú" to "u", "û" to "u", "ü" to "u",
        "ç" to "c", "ñ" to "n", "ß" to "ss",
    )

    fun normalize(s: String): String {
        var out = s.lowercase()
        for ((from, to) in precomposedAccents) out = out.replace(from, to)
        return out
            .replace(Regex("\\p{M}"), "")
            .replace(Regex("[.,!?;:\"'’]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    fun grade(ex: ItalianExerciseDto, answer: ItalianAnswerDto): Boolean = when (ex) {
        is ItalianExerciseDto.Choice ->
            answer is ItalianAnswerDto.Choice && answer.choiceIndex == ex.answerIndex
        is ItalianExerciseDto.Type ->
            answer is ItalianAnswerDto.Type && ex.accepted.any { normalize(it) == normalize(answer.text) }
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
