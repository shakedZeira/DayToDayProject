package com.daytoday.ui.screen.italian

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.daytoday.network.ItalianLessonDto
import com.daytoday.network.ItalianPathProgressDto
import com.daytoday.network.ItalianSubmitResultDto
import com.daytoday.ui.navigation.Screen
import com.daytoday.ui.screen.workout.UiState
import com.daytoday.ui.theme.DayTodayTopAppBar
import com.daytoday.ui.theme.ErrorState
import com.daytoday.ui.theme.LoadingOverlay

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ItalianScreen(
    navController: NavHostController,
    viewModel: ItalianViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // A running lesson takes the whole screen, like Duolingo: no app bar, just the lesson chrome.
    val inLesson = (uiState as? UiState.Success)?.data?.session != null
    Scaffold(
        topBar = {
            if (!inLesson) {
                DayTodayTopAppBar(
                    title = "Italian",
                    actions = {
                        IconButton(onClick = { navController.navigate(Screen.ItalianVoice.route) }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voce")
                        }
                        IconButton(onClick = { navController.navigate(Screen.ItalianTutor.route) }) {
                            Icon(Icons.Default.Forum, contentDescription = "Tutor chat")
                        }
                    },
                )
            }
        },
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
                    val result = state.data.lastResult
                    when {
                        session != null -> LessonRunner(
                            session = session,
                            onAnswer = viewModel::submitCurrent,
                            onContinue = viewModel::continueSession,
                            onSkip = viewModel::skipCurrent,
                            onQuit = viewModel::exitSession,
                        )
                        result != null -> LessonCompleteScreen(
                            result = result,
                            outOfHearts = state.data.lastOutOfHearts,
                            onContinue = viewModel::dismissResult,
                        )
                        else -> PathContent(
                            state = state.data,
                            onLessonClick = viewModel::startLesson,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
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
    val completed = progress.completedLessonIds.toSet()
    val currentId = course.units.flatMap { it.lessons }.firstOrNull { it.id !in completed }?.id

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        stickyHeader { StatsBar(progress = progress) }
        var lessonIndex = 0
        course.units.forEachIndexed { unitIndex, unit ->
            val accent = Duo.UnitAccents[unitIndex % Duo.UnitAccents.size]
            item(key = "unit-${unit.id}") {
                UnitBanner(
                    section = "UNIT ${unitIndex + 1}",
                    title = unit.title,
                    color = accent.first,
                )
            }
            val firstIndex = lessonIndex
            lessonIndex += unit.lessons.size
            items(unit.lessons, key = { it.id }) { lesson ->
                val index = firstIndex + unit.lessons.indexOf(lesson)
                PathNode(
                    lesson = lesson,
                    status = when {
                        lesson.id in completed -> NodeStatus.Done
                        lesson.id == currentId -> NodeStatus.Current
                        else -> NodeStatus.Locked
                    },
                    accent = accent.first,
                    ledge = accent.second,
                    offsetX = ZigZag[index % ZigZag.size].dp,
                    onClick = { onLessonClick(lesson.id) },
                )
            }
        }
    }
}

/** Horizontal wobble (dp) applied to successive nodes so the path winds like Duolingo's. */
private val ZigZag = listOf(0, 36, 56, 36, 0, -36, -56, -36)

private enum class NodeStatus { Done, Current, Locked }

@Composable
private fun StatsBar(progress: ItalianPathProgressDto) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat(Icons.Default.LocalFireDepartment, progress.streak.toString(), Duo.Orange, "day streak")
            Stat(Icons.Default.Bolt, progress.xp.toString(), Duo.Gold, "XP")
            Stat(
                Icons.Default.Check,
                "${progress.completedLessonIds.size}/${progress.totalLessons}",
                Duo.Green,
                "lessons",
            )
        }
    }
}

@Composable
private fun Stat(icon: ImageVector, value: String, tint: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(26.dp))
        Text(
            text = value,
            color = tint,
            fontWeight = FontWeight.ExtraBold,
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun UnitBanner(section: String, title: String, color: Color) {
    Surface(
        color = color,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = section,
                color = Color.White.copy(alpha = 0.8f),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.headlineSmall,
            )
        }
    }
}

@Composable
private fun PathNode(
    lesson: ItalianLessonDto,
    status: NodeStatus,
    accent: Color,
    ledge: Color,
    offsetX: Dp,
    onClick: () -> Unit,
) {
    val fill = when (status) {
        NodeStatus.Done -> Duo.Gold
        NodeStatus.Current -> accent
        NodeStatus.Locked -> Duo.LockedFill
    }
    val edge = when (status) {
        NodeStatus.Done -> Duo.GoldDark
        NodeStatus.Current -> ledge
        NodeStatus.Locked -> Duo.LockedLedge
    }
    val icon = when (status) {
        NodeStatus.Done -> Icons.Default.Check
        NodeStatus.Current -> Icons.Default.Star
        NodeStatus.Locked -> Icons.Default.Lock
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.offset(x = offsetX),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (status == NodeStatus.Current) StartBubble(accent)
            // 3D button: a darker disc peeking out below a lighter disc.
            Box(modifier = Modifier.size(width = 84.dp, height = 90.dp)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(edge),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(fill)
                        .clickable(enabled = status != NodeStatus.Locked, onClick = onClick),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = lesson.title,
                        tint = if (status == NodeStatus.Locked) Color(0xFF9A9AA5) else Color.White,
                        modifier = Modifier.size(38.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = lesson.title,
                color = if (status == NodeStatus.Locked) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** The bouncing "START" label above the lesson to do next. */
@Composable
private fun StartBubble(color: Color) {
    val bounce by rememberInfiniteTransition(label = "start").animateFloat(
        initialValue = 0f,
        targetValue = -6f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "start-bounce",
    )
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .offset(y = bounce.dp)
            .padding(bottom = 8.dp),
    ) {
        Text(
            text = "START",
            color = color,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun LessonCompleteScreen(
    result: ItalianSubmitResultDto,
    outOfHearts: Boolean,
    onContinue: () -> Unit,
) {
    val total = result.results.size
    val correct = result.results.count { it.isCorrect }
    val accuracy = if (total == 0) 0 else (correct * 100) / total
    val passed = !outOfHearts
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(text = if (passed) "🎉" else "💔", fontSize = 72.sp)
            Text(
                text = if (passed) "Lesson complete!" else "Out of hearts",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = if (passed) Duo.Gold else Duo.Red,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 12.dp, bottom = 28.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ResultTile("TOTAL XP", "+${result.xpGained}", Duo.Gold, Modifier.weight(1f))
                ResultTile("ACCURACY", "$accuracy%", Duo.Green, Modifier.weight(1f))
                ResultTile("STREAK", "${result.progress.streak}", Duo.Orange, Modifier.weight(1f))
            }
            if (!passed) {
                Text(
                    text = "Practice makes perfect. Try this lesson again.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }
        }
        DuoButton(
            text = "Continue",
            onClick = onContinue,
            color = if (passed) Duo.Green else Duo.Blue,
        )
    }
}

@Composable
private fun ResultTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.14f),
        border = BorderStroke(2.dp, color),
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, color = color, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
            Text(value, color = color, fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.headlineSmall)
        }
    }
}
