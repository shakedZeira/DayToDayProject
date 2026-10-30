package com.daytoday.ui.screen.italian

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.daytoday.network.ItalianAnswerDto
import com.daytoday.network.ItalianExerciseDto
import com.daytoday.network.ItalianPairDto
import java.util.Locale
import kotlinx.coroutines.delay

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

private fun TextToSpeech.say(text: String, slow: Boolean = false) {
    setSpeechRate(if (slow) 0.55f else 1f)
    speak(text, TextToSpeech.QUEUE_FLUSH, null, "italian")
}

/** The instruction line shown above each exercise, like Duolingo's "Select the correct meaning". */
private fun instructionFor(ex: ItalianExerciseDto): String = when (ex) {
    is ItalianExerciseDto.Choice -> when (ex.direction) {
        "listen" -> "Tap what you hear"
        "it_to_en" -> "What does this mean?"
        else -> "How do you say this in Italian?"
    }
    is ItalianExerciseDto.Match -> "Tap the matching pairs"
    is ItalianExerciseDto.Speak -> "Say this in Italian"
}

@Composable
fun ExerciseContent(
    ex: ItalianExerciseDto,
    selection: ItalianAnswerDto?,
    onSelect: (ItalianAnswerDto) -> Unit,
    locked: Boolean,
) {
    val tts = rememberItalianTts()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = instructionFor(ex),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        when (ex) {
            is ItalianExerciseDto.Choice -> ChoiceExercise(ex, selection, onSelect, locked, tts)
            is ItalianExerciseDto.Match -> MatchExercise(ex, selection, onSelect, locked)
            is ItalianExerciseDto.Speak -> SpeakExercise(ex, selection, onSelect, locked, tts)
        }
    }
}

@Composable
private fun SpeakerButton(onClick: () -> Unit, size: Int = 64, tint: Color = Duo.Blue) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = tint.copy(alpha = 0.16f),
        border = BorderStroke(2.dp, tint),
        modifier = Modifier
            .size(size.dp)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.VolumeUp,
                contentDescription = "Play audio",
                tint = tint,
                modifier = Modifier.size((size * 0.5f).dp),
            )
        }
    }
}

@Composable
private fun ChoiceExercise(
    ex: ItalianExerciseDto.Choice,
    selection: ItalianAnswerDto?,
    onSelect: (ItalianAnswerDto) -> Unit,
    locked: Boolean,
    tts: TextToSpeech,
) {
    val selected = (selection as? ItalianAnswerDto.Choice)?.choiceIndex
    // Italian text the learner should be able to hear: the listening clip, or an Italian prompt.
    val spoken = ex.speak ?: ex.prompt.takeIf { ex.direction == "it_to_en" }

    if (ex.direction == "listen") {
        LaunchedEffect(ex.id) {
            delay(500) // give the TTS engine a moment after first init
            spoken?.let { tts.say(it) }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SpeakerButton(onClick = { spoken?.let { tts.say(it) } }, size = 96)
            SpeakerButton(onClick = { spoken?.let { tts.say(it, slow = true) } }, size = 56, tint = Duo.Orange)
            Text("slow", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (spoken != null) SpeakerButton(onClick = { tts.say(spoken) }, size = 44)
            Text(
                text = ex.prompt,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ex.choices.forEachIndexed { index, choice ->
            val state = when {
                locked && index == ex.answerIndex -> OptionState.Correct
                locked && index == selected -> OptionState.Wrong
                index == selected -> OptionState.Selected
                else -> OptionState.Idle
            }
            OptionCard(
                label = choice,
                number = index + 1,
                state = state,
                enabled = !locked,
                onClick = { onSelect(ItalianAnswerDto.Choice(ex.id, index)) },
            )
        }
    }
}

@Composable
private fun SpeakExercise(
    ex: ItalianExerciseDto.Speak,
    selection: ItalianAnswerDto?,
    onSelect: (ItalianAnswerDto) -> Unit,
    locked: Boolean,
    tts: TextToSpeech,
) {
    val context = LocalContext.current
    val heard = (selection as? ItalianAnswerDto.Speak)?.heard.orEmpty()
    var partial by remember(ex.id) { mutableStateOf("") }
    var permissionDenied by remember { mutableStateOf(false) }
    val stt = rememberItalianStt(
        onResult = { text ->
            partial = ""
            onSelect(ItalianAnswerDto.Speak(ex.id, text))
        },
        onPartial = { partial = it },
    )
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionDenied = !granted
        if (granted) stt.start()
    }
    // Stop listening as soon as the answer is checked or the exercise changes.
    DisposableEffect(ex.id, locked) {
        if (locked) stt.stop()
        onDispose { stt.stop() }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SpeakerButton(onClick = { tts.say(ex.target) }, size = 56)
            Column {
                Text(
                    text = ex.target,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = ex.prompt,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val listening = stt.isListening
            val tint = if (listening) Duo.Red else Duo.Blue
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = if (listening) 0.28f else 0.16f),
                border = BorderStroke(3.dp, tint),
                modifier = Modifier
                    .size(112.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !locked) {
                        if (listening) {
                            stt.stop()
                        } else if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            stt.start()
                        } else {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (listening) Icons.Default.Stop else Icons.Default.Mic,
                        contentDescription = if (listening) "Stop" else "Speak",
                        tint = tint,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
        }

        val caption = when {
            permissionDenied -> "Microphone access is off. Allow it in Settings, or skip."
            !stt.available -> "Speech recognition is not available on this device. You can skip."
            stt.isListening -> partial.ifBlank { "Listening..." }
            heard.isNotBlank() -> "You said: $heard"
            else -> "Tap the microphone and say the sentence"
        }
        Text(
            text = caption,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private enum class OptionState { Idle, Selected, Correct, Wrong }

@Composable
private fun OptionCard(
    label: String,
    number: Int?,
    state: OptionState,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val accent = when (state) {
        OptionState.Idle -> MaterialTheme.colorScheme.outline
        OptionState.Selected -> Duo.Blue
        OptionState.Correct -> Duo.Green
        OptionState.Wrong -> Duo.Red
    }
    val fill = if (state == OptionState.Idle) Color.Transparent else accent.copy(alpha = 0.16f)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = fill,
        border = BorderStroke(2.dp, accent),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (number != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.5.dp, accent),
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = number.toString(),
                            color = accent,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = if (state == OptionState.Idle) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    accent
                },
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MatchExercise(
    ex: ItalianExerciseDto.Match,
    selection: ItalianAnswerDto?,
    onSelect: (ItalianAnswerDto) -> Unit,
    locked: Boolean,
) {
    var matched by remember(ex.id) {
        mutableStateOf((selection as? ItalianAnswerDto.Match)?.pairs.orEmpty())
    }
    var selectedLeft by remember(ex.id) { mutableStateOf<String?>(null) }
    // The last wrong pairing, flashed red briefly so the learner sees the mistake.
    var wrongPair by remember(ex.id) { mutableStateOf<Pair<String, String>?>(null) }
    LaunchedEffect(wrongPair) {
        if (wrongPair != null) {
            delay(600)
            wrongPair = null
        }
    }
    val matchedLefts = matched.mapTo(HashSet()) { it.left }
    val matchedRights = matched.mapTo(HashSet()) { it.right }
    // Right-hand column is shuffled once per exercise so the pairs are not side by side.
    val rights = remember(ex.id) { ex.pairs.map { it.right }.shuffled() }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ex.pairs.forEach { pair ->
                val done = pair.left in matchedLefts
                OptionCard(
                    label = pair.left,
                    number = null,
                    state = when {
                        done -> OptionState.Correct
                        wrongPair?.first == pair.left -> OptionState.Wrong
                        selectedLeft == pair.left -> OptionState.Selected
                        else -> OptionState.Idle
                    },
                    enabled = !locked && !done,
                    onClick = { selectedLeft = pair.left },
                )
            }
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            rights.forEach { right ->
                val done = right in matchedRights
                OptionCard(
                    label = right,
                    number = null,
                    state = when {
                        done -> OptionState.Correct
                        wrongPair?.second == right -> OptionState.Wrong
                        else -> OptionState.Idle
                    },
                    enabled = !locked && !done && selectedLeft != null,
                    onClick = {
                        val left = selectedLeft ?: return@OptionCard
                        selectedLeft = null
                        if (ex.pairs.none { it.left == left && it.right == right }) {
                            wrongPair = left to right
                            return@OptionCard
                        }
                        val updated = matched + ItalianPairDto(left, right)
                        matched = updated
                        if (updated.size == ex.pairs.size) {
                            onSelect(ItalianAnswerDto.Match(ex.id, updated))
                        }
                    },
                )
            }
        }
    }
}

private fun correctAnswerText(ex: ItalianExerciseDto): String = when (ex) {
    is ItalianExerciseDto.Choice -> ex.choices.getOrElse(ex.answerIndex) { "" }
    is ItalianExerciseDto.Match -> ex.pairs.joinToString("\n") { "${it.left} = ${it.right}" }
    is ItalianExerciseDto.Speak -> ex.target
}

private val CorrectPraise = listOf("Nicely done!", "Great job!", "Perfetto!", "Bravo!", "Ottimo!")

@Composable
fun LessonRunner(
    session: LessonSession,
    onAnswer: (ItalianAnswerDto) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    onQuit: () -> Unit,
) {
    val exercises = session.queue
    val exercise = exercises.getOrNull(session.index)
    var pending by remember(session.index) { mutableStateOf<ItalianAnswerDto?>(null) }
    var confirmQuit by remember { mutableStateOf(false) }
    val feedback = session.feedbackCorrect
    val haptics = LocalHapticFeedback.current

    LaunchedEffect(feedback) {
        when (feedback) {
            true -> haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            false -> haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            null -> Unit
        }
    }

    // The bar fills as exercises are completed, so it only moves after feedback is dismissed.
    val answered = session.index + if (feedback != null) 1 else 0
    val progress = if (exercises.isEmpty()) 0f else answered.toFloat() / exercises.size

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconButton(onClick = { confirmQuit = true }) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Quit lesson",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DuoProgressBar(progress = progress, modifier = Modifier.weight(1f))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Icon(Icons.Default.Favorite, contentDescription = "Hearts", tint = Duo.Red)
                Text(
                    text = session.hearts.toString(),
                    color = Duo.Red,
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            if (exercise != null) {
                ExerciseContent(
                    ex = exercise,
                    selection = session.answers.getOrNull(session.index) ?: pending,
                    onSelect = { pending = it },
                    locked = feedback != null || session.submitting,
                )
            }
        }

        BottomBar(
            exercise = exercise,
            feedback = feedback,
            canCheck = pending != null,
            submitting = session.submitting,
            praise = remember(session.index) { CorrectPraise.random() },
            onCheck = { pending?.let(onAnswer) },
            onContinue = onContinue,
            onSkip = onSkip,
        )
    }

    if (confirmQuit) {
        AlertDialog(
            onDismissRequest = { confirmQuit = false },
            title = { Text("Quit lesson?") },
            text = { Text("You'll lose your progress in this lesson.") },
            confirmButton = {
                TextButton(onClick = onQuit) { Text("Quit", color = Duo.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmQuit = false }) { Text("Keep learning") }
            },
        )
    }
}

@Composable
private fun BottomBar(
    exercise: ItalianExerciseDto?,
    feedback: Boolean?,
    canCheck: Boolean,
    submitting: Boolean,
    praise: String,
    onCheck: () -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val accent = if (feedback == true) Duo.Green else Duo.Red
    val background = if (feedback == null) {
        MaterialTheme.colorScheme.surface
    } else {
        accent.copy(alpha = 0.18f).compositeOver(MaterialTheme.colorScheme.surface)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (feedback != null && exercise != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (feedback) Icons.Default.Check else Icons.Default.Close,
                        contentDescription = null,
                        tint = accent,
                    )
                }
                Column {
                    Text(
                        text = if (feedback) praise else "Correct solution:",
                        color = accent,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (!feedback) {
                        Text(
                            text = correctAnswerText(exercise),
                            color = accent,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        if (feedback == null) {
            DuoButton(text = "Check", onClick = onCheck, enabled = canCheck)
            if (exercise is ItalianExerciseDto.Speak) {
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "CAN'T SPEAK NOW",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        } else {
            DuoButton(
                text = if (submitting) "Saving..." else "Continue",
                onClick = onContinue,
                enabled = !submitting,
                color = accent,
            )
        }
    }
}
