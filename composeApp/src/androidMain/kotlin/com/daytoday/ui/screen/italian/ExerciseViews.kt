package com.daytoday.ui.screen.italian

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.daytoday.network.ItalianAnswerDto
import com.daytoday.network.ItalianExerciseDto
import com.daytoday.network.ItalianPairDto
import com.daytoday.ui.theme.DayTodayButton
import java.util.Locale

@Composable
fun rememberItalianTts(): TextToSpeech {
    val context = LocalContext.current
    val tts = remember {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine.setLanguage(Locale.ITALIAN)
            }
        }
        engine
    }
    DisposableEffect(Unit) {
        onDispose { tts.shutdown() }
    }
    return tts
}

@Composable
fun ExerciseContent(
    ex: ItalianExerciseDto,
    answer: ItalianAnswerDto?,
    onAnswer: (ItalianAnswerDto) -> Unit,
    locked: Boolean,
) {
    val tts = rememberItalianTts()
    when (ex) {
        is ItalianExerciseDto.Choice -> ChoiceExercise(ex, answer, onAnswer, locked, tts)
        is ItalianExerciseDto.Type -> TypeExercise(ex, answer, onAnswer, locked)
        is ItalianExerciseDto.Match -> MatchExercise(ex, answer, onAnswer, locked)
    }
}

@Composable
private fun ChoiceExercise(
    ex: ItalianExerciseDto.Choice,
    answer: ItalianAnswerDto?,
    onAnswer: (ItalianAnswerDto) -> Unit,
    locked: Boolean,
    tts: TextToSpeech,
) {
    val selected = (answer as? ItalianAnswerDto.Choice)?.choiceIndex
    val speak = ex.speak
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = ex.prompt,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (ex.direction == "listen" && speak != null) {
            Button(
                onClick = { tts.speak(speak, TextToSpeech.QUEUE_FLUSH, null, "italian") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("🔊 Play")
            }
        }
        ex.choices.forEachIndexed { index, choice ->
            val container = when {
                locked && index == ex.answerIndex -> Color(0xFFD1FAE5)
                locked && index == selected -> Color(0xFFFEE2E2)
                index == selected -> Color(0xFFDBEAFE)
                else -> Color.Transparent
            }
            OutlinedButton(
                onClick = { onAnswer(ItalianAnswerDto.Choice(ex.id, index)) },
                enabled = !locked,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = container),
            ) {
                Text(text = choice, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
private fun TypeExercise(
    ex: ItalianExerciseDto.Type,
    answer: ItalianAnswerDto?,
    onAnswer: (ItalianAnswerDto) -> Unit,
    locked: Boolean,
) {
    var text by remember(ex.id) {
        mutableStateOf((answer as? ItalianAnswerDto.Type)?.text.orEmpty())
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = ex.prompt,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            enabled = !locked,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onAnswer(ItalianAnswerDto.Type(ex.id, text)) },
            enabled = !locked && text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Check")
        }
    }
}

@Composable
private fun MatchExercise(
    ex: ItalianExerciseDto.Match,
    answer: ItalianAnswerDto?,
    onAnswer: (ItalianAnswerDto) -> Unit,
    locked: Boolean,
) {
    var matched by remember(ex.id) {
        mutableStateOf((answer as? ItalianAnswerDto.Match)?.pairs.orEmpty())
    }
    var selectedLeft by remember(ex.id) { mutableStateOf<String?>(null) }
    val matchedLefts = matched.mapTo(HashSet()) { it.left }
    val matchedRights = matched.mapTo(HashSet()) { it.right }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = "Match the pairs",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ex.pairs.forEach { pair ->
                    val done = pair.left in matchedLefts
                    OutlinedButton(
                        onClick = { if (!locked) selectedLeft = pair.left },
                        enabled = !locked && !done,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = when {
                                done -> Color(0xFFD1FAE5)
                                selectedLeft == pair.left -> Color(0xFFDBEAFE)
                                else -> Color.Transparent
                            },
                        ),
                    ) {
                        Text(text = pair.left, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ex.pairs.forEach { pair ->
                    val done = pair.right in matchedRights
                    OutlinedButton(
                        onClick = {
                            val left = selectedLeft
                            if (!locked && !done && left != null) {
                                val updated = matched + ItalianPairDto(left, pair.right)
                                matched = updated
                                selectedLeft = null
                                if (updated.size == ex.pairs.size) {
                                    onAnswer(ItalianAnswerDto.Match(ex.id, updated))
                                }
                            }
                        },
                        enabled = !locked && !done && selectedLeft != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (done) Color(0xFFD1FAE5) else Color.Transparent,
                        ),
                    ) {
                        Text(text = pair.right, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

private fun correctAnswerText(ex: ItalianExerciseDto): String = when (ex) {
    is ItalianExerciseDto.Choice -> ex.choices.getOrElse(ex.answerIndex) { "" }
    is ItalianExerciseDto.Type -> ex.accepted.joinToString(" / ")
    is ItalianExerciseDto.Match -> ex.pairs.joinToString(", ") { "${it.left} = ${it.right}" }
}

@Composable
fun LessonRunner(
    session: LessonSession,
    onAnswer: (ItalianAnswerDto) -> Unit,
    onContinue: () -> Unit,
    onQuit: () -> Unit,
) {
    val exercises = session.lesson.exercises
    val exercise = exercises.getOrNull(session.index)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LinearProgressIndicator(
                progress = if (exercises.isEmpty()) 0f else (session.index + 1f) / exercises.size,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )
            TextButton(onClick = onQuit) {
                Text("Quit")
            }
        }
        Text(
            text = "❤️".repeat(session.hearts),
            fontSize = 20.sp,
            color = MaterialTheme.colorScheme.error,
        )
        Text(
            text = session.lesson.title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (exercise == null) {
            Text(
                text = "Loading lesson...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            ExerciseContent(
                ex = exercise,
                answer = session.answers.getOrNull(session.index),
                onAnswer = onAnswer,
                locked = session.feedbackCorrect != null || session.submitting,
            )
            val feedback = session.feedbackCorrect
            if (feedback != null) {
                Surface(
                    color = if (feedback) Color(0xFFD1FAE5) else Color(0xFFFEE2E2),
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = if (feedback) "Correct!" else "Not quite...",
                            fontWeight = FontWeight.Bold,
                            color = if (feedback) Color(0xFF065F46) else Color(0xFF991B1B),
                        )
                        if (!feedback) {
                            Text(
                                text = "Correct answer: " + correctAnswerText(exercise),
                                color = Color(0xFF991B1B),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
                DayTodayButton(
                    onClick = onContinue,
                    text = if (session.submitting) "Saving..." else "Continue",
                    enabled = !session.submitting,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
