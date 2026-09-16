package com.nova.app

import android.content.Context
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

enum class PrivacyMode { PRIVATE, ONLINE }

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

// --- Local notes storage: plain SharedPreferences, on-device only, never leaves the phone ---
object NoteStorage {
    private const val PREFS = "nova_notes"
    private const val KEY = "notes_list"
    private const val DELIM = "|||NOTE|||"

    fun add(context: Context, note: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, "") ?: ""
        val updated = if (existing.isEmpty()) note else existing + DELIM + note
        prefs.edit().putString(KEY, updated).apply()
    }

    fun getAll(context: Context): List<String> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = prefs.getString(KEY, "") ?: ""
        return if (existing.isEmpty()) emptyList() else existing.split(DELIM)
    }

    fun clearAll(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY).apply()
    }
}

// Deterministic, fully offline responder. No cloud calls, no ML model.
fun localRespond(context: Context, input: String): String {
    val trimmed = input.trim()
    val lower = trimmed.lowercase()

    if (lower.startsWith("note:")) {
        val note = trimmed.substringAfter(":").trim()
        if (note.isEmpty()) return "Tell me what to note, like: note: buy milk"
        NoteStorage.add(context, note)
        return "Saved: \"$note\""
    }
    if (lower == "show notes" || lower == "list notes") {
        val notes = NoteStorage.getAll(context)
        if (notes.isEmpty()) return "You don't have any notes yet. Try: note: buy milk"
        return notes.mapIndexed { i, n -> "${i + 1}. $n" }.joinToString("\n")
    }
    if (lower == "delete notes" || lower == "clear notes") {
        NoteStorage.clearAll(context)
        return "All notes deleted."
    }

    // Calculator: only digits, spaces, + - * / ( )
    if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() || it in "+-*/(). " } && trimmed.any { it.isDigit() }) {
        val result = evalSimpleMath(trimmed)
        if (result != null) return "= $result"
    }

    if ("what time" in lower) {
        return "It's ${LocalDateTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))}"
    }
    if ("what date" in lower || "today's date" in lower || "what day" in lower) {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
    }

    if (lower.startsWith("convert ")) {
        return tryConvertUnits(lower) ?: "I couldn't parse that conversion. Try: convert 10 km to miles"
    }

    val onlineTriggers = listOf("who is", "what is the latest", "news", "search for", "explain")
    if (onlineTriggers.any { it in lower }) {
        return "That needs current info or general reasoning I don't have on-device. " +
                "Switch to Online Mode if you'd like me to look it up — you'll see what would be sent first."
    }

    return "I'm running fully offline in Private Mode. I can do calculations, unit conversions, " +
            "date/time, and notes (note: ..., show notes, delete notes)."
}

fun evalSimpleMath(expr: String): Double? {
    return try {
        Parser(expr.replace(" ", "")).parse()
    } catch (e: Exception) {
        null
    }
}

private class Parser(private val s: String) {
    private var pos = 0
    fun parse(): Double {
        val v = parseExpr()
        if (pos != s.length) throw IllegalArgumentException("bad expr")
        return v
    }
    private fun parseExpr(): Double {
        var v = parseTerm()
        while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
            val op = s[pos]; pos++
            val rhs = parseTerm()
            v = if (op == '+') v + rhs else v - rhs
        }
        return v
    }
    private fun parseTerm(): Double {
        var v = parseFactor()
        while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
            val op = s[pos]; pos++
            val rhs = parseFactor()
            v = if (op == '*') v * rhs else v / rhs
        }
        return v
    }
    private fun parseFactor(): Double {
        if (pos < s.length && s[pos] == '(') {
            pos++
            val v = parseExpr()
            if (pos < s.length && s[pos] == ')') pos++
            return v
        }
        val start = pos
        while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
        if (start == pos) throw IllegalArgumentException("bad number")
        return s.substring(start, pos).toDouble()
    }
}

fun tryConvertUnits(lower: String): String? {
    val regex = Regex("""convert\s+([\d.]+)\s*(km|mi|kg|lb|c|f)\s*to\s*(km|mi|kg|lb|c|f)""")
    val match = regex.find(lower) ?: return null
    val (valueStr, from, to) = match.destructured
    val value = valueStr.toDoubleOrNull() ?: return null
    val result = when {
        from == "km" && to == "mi" -> value * 0.621371
        from == "mi" && to == "km" -> value / 0.621371
        from == "kg" && to == "lb" -> value * 2.20462
        from == "lb" && to == "kg" -> value / 2.20462
        from == "c" && to == "f" -> value * 9 / 5 + 32
        from == "f" && to == "c" -> (value - 32) * 5 / 9
        else -> return null
    }
    return "%.2f".format(result)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen() {
    val context = LocalContext.current
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var input by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(PrivacyMode.PRIVATE) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nova") },
                actions = {
                    AssistChip(
                        onClick = { /* Private is default; Online Mode arrives in a later phase */ },
                        label = { Text(if (mode == PrivacyMode.PRIVATE) "Private mode" else "Online mode") }
                    )
                }
            )
        }
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
                        messages.add(ChatMessage(localRespond(context, input), isUser = false))
                        input = ""
                    }
                }) { Text("Send") }
            }
        }
    }
}
