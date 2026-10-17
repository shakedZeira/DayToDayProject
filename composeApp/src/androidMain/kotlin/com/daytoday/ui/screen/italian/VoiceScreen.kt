package com.daytoday.ui.screen.italian

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.daytoday.ui.theme.DayTodayTopAppBar

@Composable
fun VoiceScreen(
    onBack: () -> Unit,
    viewModel: VoiceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    var permissionDenied by remember { mutableStateOf(false) }
    var micLang by remember { mutableStateOf("it-IT") }
    val stt = rememberItalianStt(
        onResult = { viewModel.onUserSaid(it) },
        onPartial = { viewModel.onUserPartial(it) },
    )
    val tts = rememberVoiceTts(onIdle = { viewModel.onSpeechFinished() })
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionDenied = !granted
        if (granted) viewModel.startSession()
    }

    val requestStart: () -> Unit = {
        if (
            context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            permissionDenied = false
            viewModel.startSession()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.listenRequests.collect {
            if (viewModel.uiState.value.status == VoiceStatus.Listening) {
                Log.i("VoiceLoop", "listenRequest -> stt.start available=${stt.available} lang=$micLang")
                stt.start()
            }
        }
    }
    LaunchedEffect(Unit) {
        viewModel.speechQueue.collect { chunk -> tts.speak(chunk.text, chunk.lang) }
    }
    LaunchedEffect(state.status) {
        if (state.status != VoiceStatus.Listening) stt.stop()
    }
    LaunchedEffect(state.turns.size, state.partial) {
        val count = state.turns.size + if (state.partial.isNotBlank()) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Scaffold(
        topBar = {
            DayTodayTopAppBar(
                title = "Tutor vocale",
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopSession()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            StatusLine(state.status)
            MicLanguageToggle(
                selected = micLang,
                onSelect = { tag ->
                    micLang = tag
                    stt.setLanguage(tag)
                },
            )
            state.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (permissionDenied) {
                Text(
                    text = "Serve il permesso del microfono per parlare.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (!stt.available) {
                Text(
                    text = "Riconoscimento vocale non disponibile su questo dispositivo.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.turns) { turn ->
                    VoiceBubble(turn)
                }
                if (state.partial.isNotBlank()) {
                    item { VoicePartialBubble(state.partial) }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                StartStopOrb(
                    status = state.status,
                    onStart = requestStart,
                    onStop = {
                        viewModel.stopSession()
                        stt.stop()
                        tts.stop()
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusLine(status: VoiceStatus) {
    val label = when (status) {
        VoiceStatus.Idle -> "Premi per iniziare"
        VoiceStatus.Listening -> "Ascolto…"
        VoiceStatus.Thinking -> "Sto pensando…"
        VoiceStatus.Speaking -> "Parlo…"
    }
    val dot by animateColorAsState(
        targetValue = when (status) {
            VoiceStatus.Idle -> MaterialTheme.colorScheme.outline
            VoiceStatus.Listening -> MaterialTheme.colorScheme.primary
            VoiceStatus.Thinking -> MaterialTheme.colorScheme.tertiary
            VoiceStatus.Speaking -> MaterialTheme.colorScheme.secondary
        },
        label = "statusDot",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(dot),
        )
        Spacer(modifier = Modifier.size(8.dp))
        Crossfade(targetState = label, label = "statusText") { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun MicLanguageToggle(
    selected: String,
    onSelect: (String) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Parlo in:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.size(12.dp))
        FilterChip(
            selected = selected == "it-IT",
            onClick = { onSelect("it-IT") },
            label = { Text("IT") },
        )
        Spacer(modifier = Modifier.size(8.dp))
        FilterChip(
            selected = selected == "en-US",
            onClick = { onSelect("en-US") },
            label = { Text("EN") },
        )
    }
}

@Composable
private fun StartStopOrb(
    status: VoiceStatus,
    onStart: () -> Unit,
    onStop: () -> Unit,
) {
    val running = status != VoiceStatus.Idle
    val container by animateColorAsState(
        targetValue = when (status) {
            VoiceStatus.Idle -> MaterialTheme.colorScheme.primary
            VoiceStatus.Listening -> MaterialTheme.colorScheme.primary
            VoiceStatus.Thinking -> MaterialTheme.colorScheme.tertiary
            VoiceStatus.Speaking -> MaterialTheme.colorScheme.secondary
        },
        label = "orbColor",
    )
    Box(
        modifier = Modifier
            .size(120.dp)
            .clip(CircleShape)
            .background(container)
            .clickable { if (running) onStop() else onStart() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = if (running) Icons.Filled.Stop else Icons.Filled.Mic,
            contentDescription = if (running) "Ferma" else "Inizia",
            tint = Color.White,
            modifier = Modifier.size(48.dp),
        )
    }
}

@Composable
private fun VoiceBubble(turn: VoiceTurn) {
    val isUser = turn.role == "user"
    val englishColor = MaterialTheme.colorScheme.onSurfaceVariant
    val content = if (turn.segments.isEmpty()) {
        AnnotatedString(turn.text)
    } else {
        buildAnnotatedString {
            turn.segments.forEach { segment ->
                if (!isUser && segment.lang == "en") {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = englishColor)) {
                        append(segment.text)
                    }
                } else {
                    append(segment.text)
                }
            }
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Surface(
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            shape = if (isUser) {
                RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
            } else {
                RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
            },
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}

@Composable
private fun VoicePartialBubble(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                fontStyle = FontStyle.Italic,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
        }
    }
}
