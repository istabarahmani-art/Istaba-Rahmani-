package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.content
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.UUID

data class AssistantMessage(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val isError: Boolean = false
)

sealed interface AssistantStreamState {
    data object Idle : AssistantStreamState
    data object Loading : AssistantStreamState
    data class Streaming(val partialText: String) : AssistantStreamState
    data class Error(val message: String) : AssistantStreamState
}

class StudyAssistantViewModel : ViewModel() {

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    """
                    You are StudyPulse AI, a warm, encouraging, and ultra-clear student routine & study assistant.
                    You have two main jobs:
                    1. Answer questions accurately about the student's school routines, class times, rooms, assignment deadlines, and planned study sessions using the live schedule snapshot provided with each message.
                    2. Help the student study effectively: explain concepts clearly with examples, break down big assignments into manageable steps, quiz them when asked, or design focused study plans around their real schedule.
                    Keep answers concise, scannable (use short bullet points where helpful), and practical for mobile reading. If a schedule item is missing from their snapshot, say so kindly and suggest adding it.
                    """.trimIndent()
                )
            }
        )
    }

    private val _messages = MutableStateFlow<List<AssistantMessage>>(emptyList())
    val messages: StateFlow<List<AssistantMessage>> = _messages.asStateFlow()

    private val _streamState = MutableStateFlow<AssistantStreamState>(AssistantStreamState.Idle)
    val streamState: StateFlow<AssistantStreamState> = _streamState.asStateFlow()

    fun sendQuestion(userQuestion: String, scheduleSnapshot: String) {
        val trimmed = userQuestion.trim()
        if (trimmed.isEmpty()) return
        if (_streamState.value is AssistantStreamState.Loading ||
            _streamState.value is AssistantStreamState.Streaming
        ) return

        val previousHistory: List<Content> = _messages.value
            .filter { !it.isError && it.text.isNotBlank() }
            .takeLast(8)
            .map { msg ->
                content(if (msg.isUser) "user" else "model") {
                    text(msg.text)
                }
            }

        _messages.value = _messages.value + AssistantMessage(isUser = true, text = trimmed)
        _streamState.value = AssistantStreamState.Loading

        val promptWithContext = """
            [LIVE STUDENT SCHEDULE SNAPSHOT]
            $scheduleSnapshot
            [END SNAPSHOT]

            Student question: $trimmed
        """.trimIndent()

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                withTimeout(60_000L) {
                    val chat = model.startChat(history = previousHistory)
                    chat.sendMessageStream(promptWithContext).collect { chunk ->
                        val chunkText = chunk.text.orEmpty()
                        if (chunkText.isNotEmpty()) {
                            accumulated.append(chunkText)
                            _streamState.value = AssistantStreamState.Streaming(accumulated.toString())
                        }
                    }
                }
                val finalAnswer = accumulated.toString().trim()
                if (finalAnswer.isNotEmpty()) {
                    _messages.value = _messages.value + AssistantMessage(
                        isUser = false,
                        text = finalAnswer
                    )
                    _streamState.value = AssistantStreamState.Idle
                } else {
                    val emptyMsg = "I didn't receive a response. Please try asking again."
                    _streamState.value = AssistantStreamState.Error(emptyMsg)
                    _messages.value = _messages.value + AssistantMessage(
                        isUser = false,
                        text = emptyMsg,
                        isError = true
                    )
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Study assistant timed out", e)
                val msg = "The request timed out after 60 seconds. Please try again."
                _streamState.value = AssistantStreamState.Error(msg)
                _messages.value = _messages.value + AssistantMessage(
                    isUser = false,
                    text = msg,
                    isError = true
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Study assistant generation failed", e)
                val msg = e.localizedMessage ?: "Unable to reach StudyPulse AI right now."
                _streamState.value = AssistantStreamState.Error(msg)
                _messages.value = _messages.value + AssistantMessage(
                    isUser = false,
                    text = msg,
                    isError = true
                )
            }
        }
    }

    fun clearConversation() {
        _messages.value = emptyList()
        _streamState.value = AssistantStreamState.Idle
    }

    private companion object {
        const val TAG = "StudyAssistantVM"
    }
}
