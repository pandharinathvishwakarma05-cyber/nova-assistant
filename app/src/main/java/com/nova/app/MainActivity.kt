package com.nova.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

data class ChatMessage(val text: String, val isUser: Boolean)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ChatScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Nova") }) }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(8.dp)) {
                items(messages) { msg ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(tonalElevation = if (msg.isUser) 2.dp else 0.dp) {
                            Text(msg.text, Modifier.padding(12.dp))
                        }
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message Nova") }
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (input.isNotBlank()) {
                        messages.add(ChatMessage(input, isUser = true))
                        messages.add(ChatMessage("You said: $input", isUser = false))
                        input = ""
                    }
                }) { Text("Send") }
            }
        }
    }
}
