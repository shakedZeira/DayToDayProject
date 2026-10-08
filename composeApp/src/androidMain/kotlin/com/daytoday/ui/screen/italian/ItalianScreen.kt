package com.daytoday.ui.screen.italian

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daytoday.network.ItalianLessonDto
import com.daytoday.ui.screen.workout.UiState
import com.daytoday.ui.theme.DayTodayButton
import com.daytoday.ui.theme.DayTodayCard
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.LoadingOverlay

@Composable
fun ItalianScreen(viewModel: ItalianViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        topBar = { DayTodayTopAppBar(title = "Italian") },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            when (val state = uiState) {
                UiState.Loading -> LoadingOverlay("Loading Italian...")
                is UiState.Error -> ErrorState(state.message, viewModel::refresh)
                is UiState.Success -> {
                    val session = state.data.session
                    if (session != null) {
                        LessonRunner(
                            session = session,
                            onAnswer = viewModel::submitCurrent,
                            onContinue = viewModel::continueSession,
                            onQuit = viewModel::exitSession,
                        )
                    } else {
                        PathContent(
                            state = state.data,
                            onLessonClick = viewModel::startLesson,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PathContent(
    state: ItalianUiState,
    onLessonClick: (String) -> Unit,
) {
    val course = state.course
    val progress = state.progress
    if (course == null || progress == null) {
        LoadingOverlay("Loading Italian...")
        return
    }
    val lessons = course.units.flatMap { it.lessons }
    val completed = progress.completedLessonIds.toSet()
    val nextLesson = lessons.firstOrNull { it.id !in completed }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            DayTodayCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "${progress.xp} XP",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "${progress.streak} day streak",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.tertiary,
                        )
                        Text(
                            text = "${completed.size}/${progress.totalLessons} lessons",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    LinearProgressIndicator(
                        progress = if (progress.totalLessons <= 0) 0f else completed.size.toFloat() / progress.totalLessons,
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                }
            }
        }
        state.lastResult?.let { result ->
            item {
                Surface(
                    color = Color(0xFFFFF3B0),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "Lesson complete! +${result.xpGained} XP",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF92400E),
                        )
                        val mistakes = result.results.count { !it.isCorrect }
                        if (mistakes > 0) {
                            Text(
                                text = if (mistakes == 1) "1 mistake" else "$mistakes mistakes",
                                color = Color(0xFF92400E),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }
        }
        if (nextLesson != null && isLessonUnlocked(lessons, completed, nextLesson.id)) {
            item {
                DayTodayButton(
                    onClick = { onLessonClick(nextLesson.id) },
                    text = "Continue: Next lesson",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        course.units.forEach { unit ->
            item {
                Text(
                    text = unit.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    unit.lessons.forEach { lesson ->
                        val done = lesson.id in completed
                        val unlocked = done || isLessonUnlocked(lessons, completed, lesson.id)
                        val nodeColor = when {
                            done -> Color(0xFFFFF3B0)
                            unlocked -> Color(0xFFD1FAE5)
                            else -> Color(0xFFF3F4F6)
                        }
                        val textColor = when {
                            done -> Color(0xFF92400E)
                            unlocked -> Color(0xFF065F46)
                            else -> Color(0xFF9CA3AF)
                        }
                        Surface(
                            shape = CircleShape,
                            color = nodeColor,
                            modifier = Modifier
                                .size(88.dp)
                                .clickable(enabled = unlocked) { onLessonClick(lesson.id) },
                        ) {
                            Column(
                                modifier = Modifier.padding(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = lesson.title,
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun isLessonUnlocked(
    lessons: List<ItalianLessonDto>,
    completed: Set<String>,
    lessonId: String,
): Boolean {
    val index = lessons.indexOfFirst { it.id == lessonId }
    if (index <= 0) return index == 0
    return completed.contains(lessons[index - 1].id)
}
