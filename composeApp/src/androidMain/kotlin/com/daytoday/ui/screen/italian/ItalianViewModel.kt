package com.daytoday.ui.screen.italian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.network.DayTodayApi
import com.daytoday.network.ItalianAnswerDto
import com.daytoday.network.ItalianCourseDto
import com.daytoday.network.ItalianExerciseDto
import com.daytoday.network.ItalianGrading
import com.daytoday.network.ItalianLessonDto
import com.daytoday.network.ItalianPathProgressDto
import com.daytoday.network.ItalianSubmitResultDto
import com.daytoday.network.friendlyNetworkError
import com.daytoday.settings.SettingsManager
import com.daytoday.settings.recordItalianLessonCompleted
import com.daytoday.ui.screen.workout.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class LessonSession(
    val lesson: ItalianLessonDto,
    /** Exercises still to ask, in order. Missed ones are appended so they get asked again. */
    val queue: List<ItalianExerciseDto> = lesson.exercises,
    val index: Int = 0,
    val hearts: Int = 5,
    val answers: List<ItalianAnswerDto> = emptyList(),
    val feedbackCorrect: Boolean? = null,
    val submitting: Boolean = false,
)

data class ItalianUiState(
    val course: ItalianCourseDto? = null,
    val progress: ItalianPathProgressDto? = null,
    val session: LessonSession? = null,
    val lastResult: ItalianSubmitResultDto? = null,
    /** True when the last lesson ended because the learner ran out of hearts. */
    val lastOutOfHearts: Boolean = false,
)

@HiltViewModel
class ItalianViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val api: DayTodayApi,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<ItalianUiState>>(UiState.Loading)
    val uiState: StateFlow<UiState<ItalianUiState>> = _uiState

    private var state = ItalianUiState()

    init {
        refresh()
    }

    private fun setState(transform: (ItalianUiState) -> ItalianUiState) {
        state = transform(state)
        _uiState.value = UiState.Success(state)
    }

    fun refresh() {
        _uiState.value = UiState.Loading
        viewModelScope.launch {
            try {
                val token = settingsManager.authToken.first()
                val course = api.getItalianCourse(token)
                val progress = api.getItalianProgress(token)
                state = ItalianUiState(course = course, progress = progress)
                _uiState.value = UiState.Success(state)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(friendlyNetworkError(e))
            }
        }
    }

    fun startLesson(lessonId: String) {
        val lesson = state.course?.units?.flatMap { it.lessons }?.find { it.id == lessonId }?.takeIf { it.exercises.isNotEmpty() } ?: return
        val queue = lesson.exercises + speakingExercisesFor(lesson)
        setState { it.copy(session = LessonSession(lesson = lesson, queue = queue), lastResult = null) }
    }

    fun dismissResult() {
        setState { it.copy(lastResult = null) }
    }

    fun exitSession() {
        setState { it.copy(session = null) }
    }

    fun currentExercise(): ItalianExerciseDto? {
        val session = state.session ?: return null
        return session.queue.getOrNull(session.index)
    }

    fun submitCurrent(answer: ItalianAnswerDto) {
        val session = state.session ?: return
        val exercise = session.queue.getOrNull(session.index) ?: return
        if (session.feedbackCorrect != null || session.submitting) return
        val correct = ItalianGrading.grade(exercise, answer)
        setState {
            it.copy(
                session = session.copy(
                    answers = session.answers + answer,
                    // Missed exercises come back at the end, except speaking: recognition is
                    // too unreliable to make a learner repeat it until it passes.
                    queue = if (correct || exercise is ItalianExerciseDto.Speak) {
                        session.queue
                    } else {
                        session.queue + exercise
                    },
                    feedbackCorrect = correct,
                    hearts = if (correct) session.hearts else session.hearts - 1,
                )
            )
        }
    }

    /** "Can't speak now": moves on without a heart penalty or any record of an answer. */
    fun skipCurrent() {
        val session = state.session ?: return
        val exercise = session.queue.getOrNull(session.index) ?: return
        if (exercise !is ItalianExerciseDto.Speak || session.feedbackCorrect != null || session.submitting) return
        advance(
            session.copy(
                answers = session.answers + ItalianAnswerDto.Speak(exercise.id, heard = ""),
                feedbackCorrect = true,
            )
        )
    }

    fun continueSession() {
        val session = state.session ?: return
        if (session.submitting || session.feedbackCorrect == null) return
        advance(session)
    }

    private fun advance(session: LessonSession) {
        val finished = session.index + 1 >= session.queue.size || session.hearts <= 0
        if (finished) {
            finishSession(session)
        } else {
            setState {
                it.copy(session = session.copy(index = session.index + 1, feedbackCorrect = null))
            }
        }
    }

    private fun finishSession(session: LessonSession) {
        setState { it.copy(session = session.copy(submitting = true)) }
        viewModelScope.launch {
            try {
                val token = settingsManager.authToken.first()
                val result = api.submitItalianLesson(
                    token,
                    session.lesson.id,
                    // Speaking is local-only; the server knows nothing about those exercises.
                    session.answers.filter { it !is ItalianAnswerDto.Speak },
                )
                if (result.lessonCompleted) {
                    recordItalianLessonCompleted(settingsManager)
                }
                state = state.copy(
                    session = null,
                    lastResult = result,
                    lastOutOfHearts = session.hearts <= 0,
                    progress = result.progress,
                )
                _uiState.value = UiState.Success(state)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(friendlyNetworkError(e))
            }
        }
    }
}

/** Up to two read-aloud exercises, built from the lesson's own Italian phrases. */
private fun speakingExercisesFor(lesson: ItalianLessonDto): List<ItalianExerciseDto> {
    val phrases = lesson.exercises.mapNotNull { ex ->
        if (ex !is ItalianExerciseDto.Choice) return@mapNotNull null
        val correct = ex.choices.getOrNull(ex.answerIndex) ?: return@mapNotNull null
        when (ex.direction) {
            "en_to_it" -> ex.prompt to correct
            "listen" -> ex.speak?.let { italian -> correct to italian }
            else -> null
        }
    }.distinctBy { it.second }
    return phrases.take(2).mapIndexed { i, (english, italian) ->
        ItalianExerciseDto.Speak(id = "${lesson.id}-speak-$i", prompt = english, target = italian)
    }
}
