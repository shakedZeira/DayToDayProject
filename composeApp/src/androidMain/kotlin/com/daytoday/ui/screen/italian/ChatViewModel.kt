package com.daytoday.ui.screen.italian

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daytoday.network.ChatMessageDto
import com.daytoday.network.DayTodayApi
import com.daytoday.settings.SettingsManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ChatTurn(
    val role: String,
    val text: String,
    val correction: String = "",
    val translation: String = "",
)

data class ChatUiState(
    val messages: List<ChatTurn> = emptyList(),
    val sending: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val api: DayTodayApi,
    private val settings: SettingsManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ChatUiState(
            messages = listOf(
                ChatTurn(
                    role = "assistant",
                    text = "Ciao! Sono Sofia, la tua tutor di italiano. Come posso aiutarti oggi?",
                    translation = "Hi! I'm Sofia, your Italian tutor. How can I help you today?",
                ),
            ),
        ),
    )
    val uiState: StateFlow<ChatUiState> = _uiState

    fun send(text: String) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        if (_uiState.value.sending) return
        _uiState.value = _uiState.value.copy(
            messages = _uiState.value.messages + ChatTurn(role = "user", text = trimmed),
            sending = true,
            error = null,
        )
        viewModelScope.launch {
            try {
                val token = settings.authToken.first()
                val history = _uiState.value.messages.map { ChatMessageDto(it.role, it.text) }
                val response = api.sendItalianChat(token, history)
                _uiState.value = _uiState.value.copy(
                    messages = _uiState.value.messages + ChatTurn(
                        role = "assistant",
                        text = response.reply,
                        correction = response.correction,
                        translation = response.translation,
                    ),
                    sending = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(sending = false, error = e.message)
            }
        }
    }
}
