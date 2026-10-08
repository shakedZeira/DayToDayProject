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
                _uiState.value = UiState.Error(e.message ?: "Unable to load Italian course")
            }
        }
    }

    fun startLesson(lessonId: String) {
        val lesson = state.course?.units?.flatMap { it.lessons }?.find { it.id == lessonId } ?: return
        setState { it.copy(session = LessonSession(lesson = lesson), lastResult = null) }
    }

    fun exitSession() {
        setState { it.copy(session = null) }
    }

    fun currentExercise(): ItalianExerciseDto? {
        val session = state.session ?: return null
        return session.lesson.exercises.getOrNull(session.index)
    }

    fun submitCurrent(answer: ItalianAnswerDto) {
        val session = state.session ?: return
        val exercise = session.lesson.exercises.getOrNull(session.index) ?: return
        if (session.feedbackCorrect != null || session.submitting) return
        val correct = ItalianGrading.grade(exercise, answer)
        setState {
            it.copy(
                session = session.copy(
                    answers = session.answers + answer,
                    feedbackCorrect = correct,
                    hearts = if (correct) session.hearts else session.hearts - 1,
                )
            )
        }
    }

    fun continueSession() {
        val session = state.session ?: return
        if (session.submitting || session.feedbackCorrect == null) return
        val finished = session.index + 1 >= session.lesson.exercises.size || session.hearts <= 0
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
                val result = api.submitItalianLesson(token, session.lesson.id, session.answers)
                if (result.lessonCompleted) {
                    recordItalianLessonCompleted(settingsManager)
                }
                state = state.copy(
                    session = null,
                    lastResult = result,
                    progress = result.progress,
                )
                _uiState.value = UiState.Success(state)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to save lesson")
            }
        }
    }
}
