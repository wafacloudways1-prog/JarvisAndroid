package com.example.jarvis

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

private data class ChatMessage(
    val role: String,
    val content: String
)

@Composable
fun TextChatPanel(context: Context) {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }

    val executor = remember { Executors.newSingleThreadExecutor() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    DisposableEffect(executor) {
        onDispose { executor.shutdownNow() }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch {
                listState.animateScrollToItem(messages.lastIndex)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0B1728), RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            "JARVIS TEXT CHAT",
            color = Color(0xFF53D9FF),
            fontSize = 16.sp
        )

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Type a message below to start chatting with JARVIS.",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )
                }
            }

            items(messages) { message ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (message.role == "user") {
                        Arrangement.End
                    } else {
                        Arrangement.Start
                    }
                ) {
                    Surface(
                        color = if (message.role == "user") {
                            Color(0xFF124B68)
                        } else {
                            Color(0xFF17283D)
                        },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = message.content,
                            color = Color.White,
                            modifier = Modifier
                                .widthIn(max = 270.dp)
                                .padding(10.dp),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            if (sending) {
                item {
                    Text("JARVIS is thinking...", color = Color(0xFF53D9FF))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type your message...") },
                enabled = !sending,
                maxLines = 3
            )

            Button(
                enabled = input.isNotBlank() && !sending,
                onClick = {
                    val prompt = input.trim()
                    if (prompt.isEmpty()) return@Button

                    messages.add(ChatMessage("user", prompt))
                    input = ""
                    sending = true

                    val history = messages.toList()

                    executor.execute {
                        val answer = try {
                            requestJarvisChat(context, history)
                        } catch (_: Exception) {
                            "I couldn't reach the AI service. Please check your internet connection."
                        }

                        handler.post {
                            messages.add(ChatMessage("assistant", answer))
                            sending = false
                        }
                    }
                }
            ) {
                Text("SEND")
            }
        }
    }
}

private fun requestJarvisChat(
    context: Context,
    history: List<ChatMessage>
): String {
    val prefs = context.getSharedPreferences("jarvis", Context.MODE_PRIVATE)
    val endpoint = prefs.getString(
        "endpoint",
        "https://jarvis-gemini.facet4419.workers.dev/chat"
    ) ?: throw IOException("Missing endpoint")

    if (!endpoint.startsWith("https://")) {
        throw IOException("HTTPS endpoint required")
    }

    val messages = JSONArray()

    messages.put(
        JSONObject()
            .put("role", "system")
            .put(
                "content",
                "You are JARVIS, a helpful Android AI assistant. " +
                    "Answer clearly and naturally. Never claim to perform " +
                    "phone actions unless the app confirms they happened."
            )
    )

    history.forEach {
        messages.put(
            JSONObject()
                .put("role", it.role)
                .put("content", it.content)
        )
    }

    val body = JSONObject().put("messages", messages)
    var connection: HttpURLConnection? = null

    try {
        connection = URL(endpoint).openConnection() as HttpURLConnection

        connection.apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 30000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
        }

        connection.outputStream.use {
            it.write(body.toString().toByteArray(Charsets.UTF_8))
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val response = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (code !in 200..299) {
            throw IOException("AI service returned HTTP $code")
        }

        return JSONObject(response)
            .optJSONArray("choices")
            ?.optJSONObject(0)
            ?.optJSONObject("message")
            ?.optString("content")
            ?.takeIf { it.isNotBlank() }
            ?: throw IOException("Empty AI response")
    } finally {
        connection?.disconnect()
    }
}
