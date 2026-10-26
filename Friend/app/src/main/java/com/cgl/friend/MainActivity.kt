package com.cgl.friend

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

private val Ink = Color(0xFF10100F)
private val Panel = Color(0xFF1B1B19)
private val PanelRaised = Color(0xFF242420)
private val Gold = Color(0xFFF2C94C)
private val WarmWhite = Color(0xFFF5F2E8)
private val Quiet = Color(0xFFA9A69B)
private val Border = Color(0xFF383832)

class MainActivity : ComponentActivity() {
    lateinit var secureStore: SecureStore
        private set

    var ttsReady by mutableStateOf(false)
        private set
    var ttsVoiceNames by mutableStateOf<List<String>>(emptyList())
        private set

    private var textToSpeech: TextToSpeech? = null
    private var speechRecognizer: SpeechRecognizer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStore = SecureStore(applicationContext)
        window.statusBarColor = android.graphics.Color.rgb(16, 16, 15)
        window.navigationBarColor = android.graphics.Color.rgb(16, 16, 15)
        window.decorView.systemUiVisibility = 0

        val mainHandler = Handler(Looper.getMainLooper())
        textToSpeech = TextToSpeech(applicationContext) { result ->
            if (result == TextToSpeech.SUCCESS) {
                mainHandler.post {
                    val engine = textToSpeech
                    engine?.language = Locale.getDefault()
                    ttsReady = engine != null
                    ttsVoiceNames = engine?.voices
                        ?.map { voice -> voice.name }
                        ?.distinct()
                        ?.sorted()
                        .orEmpty()
                }
            }
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Gold,
                    onPrimary = Ink,
                    secondary = Gold,
                    background = Ink,
                    surface = Panel,
                    onSurface = WarmWhite,
                    onBackground = WarmWhite
                )
            ) {
                FriendApp(this)
            }
        }
    }

    fun startSpeechInput(
        onListening: (Boolean) -> Unit,
        onTranscript: (String) -> Unit,
        onProblem: (String) -> Unit
    ) {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            onProblem("Speech recognition is not available on this device. You can still type.")
            return
        }
        speechRecognizer?.destroy()
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer = recognizer
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) { onListening(true) }
            override fun onBeginningOfSpeech() { onListening(true) }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { onListening(false) }
            override fun onError(error: Int) {
                onListening(false)
                if (error != SpeechRecognizer.ERROR_NO_MATCH &&
                    error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT &&
                    error != SpeechRecognizer.ERROR_CLIENT
                ) {
                    onProblem("I couldn't hear that clearly. Please try again or type your message.")
                }
                if (speechRecognizer === recognizer) {
                    recognizer.destroy()
                    speechRecognizer = null
                }
            }
            override fun onResults(results: Bundle?) {
                onListening(false)
                val spoken = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) onTranscript(spoken)
                if (speechRecognizer === recognizer) {
                    recognizer.destroy()
                    speechRecognizer = null
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val spoken = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull().orEmpty()
                if (spoken.isNotBlank()) onTranscript(spoken)
            }
            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Talk to Friend")
        }
        try {
            recognizer.startListening(intent)
            onListening(true)
        } catch (_: Exception) {
            onListening(false)
            onProblem("Could not start speech recognition. Check microphone permission and try again.")
        }
    }

    fun stopSpeechInput() {
        try {
            speechRecognizer?.stopListening()
        } catch (_: Exception) {
            speechRecognizer?.destroy()
            speechRecognizer = null
        }
    }

    fun speak(text: String, voiceName: String, rate: Float, onSpeaking: (Boolean) -> Unit) {
        val engine = textToSpeech
        if (engine == null || !ttsReady) {
            onSpeaking(false)
            return
        }
        try {
            engine.stop()
            engine.setSpeechRate(rate.coerceIn(0.7f, 1.3f))
            if (voiceName.isNotBlank()) {
                engine.voices?.firstOrNull { it.name == voiceName }?.let { engine.voice = it }
            }
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    runOnUiThread { onSpeaking(true) }
                }
                override fun onDone(utteranceId: String?) {
                    runOnUiThread { onSpeaking(false) }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    runOnUiThread { onSpeaking(false) }
                }
                override fun onError(utteranceId: String?, errorCode: Int) {
                    runOnUiThread { onSpeaking(false) }
                }
            })
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
        } catch (_: Exception) {
            onSpeaking(false)
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        textToSpeech = null
        super.onDestroy()
    }
}

@Composable
private fun FriendApp(activity: MainActivity) {
    val store = activity.secureStore
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeProvider by remember {
        mutableStateOf(AIProvider.fromStored(store.getString("provider", AIProvider.GEMINI.name)))
    }
    val apiKeys = remember {
        mutableStateMapOf<AIProvider, String>().apply {
            AIProvider.entries.forEach { provider -> put(provider, store.getString(provider.keyName)) }
        }
    }
    var model by remember {
        mutableStateOf(store.getString("model_" + activeProvider.name, activeProvider.defaultModel))
    }
    var tone by remember { mutableStateOf(store.getString("tone", "Gentle")) }
    var voiceReplies by remember { mutableStateOf(store.getString("voice_replies", "true") == "true") }
    var speakingRate by remember {
        mutableStateOf(store.getString("speech_rate", "1.0").toFloatOrNull() ?: 1f)
    }
    var voiceName by remember { mutableStateOf(store.getString("voice_name", "")) }
    var memoryNotes by remember { mutableStateOf(store.getString("memory_notes", "")) }
    var importedHistory by remember { mutableStateOf(store.getString("imported_history", "")) }
    var draft by remember { mutableStateOf("") }
    var listening by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var thinking by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("Ready when you are") }
    var settingsOpen by remember { mutableStateOf(false) }
    var memoryOpen by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val messages = remember {
        mutableStateListOf<ChatMessage>().apply { addAll(loadHistory(store.getString("chat_history", "[]"))) }
    }

    val micPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            activity.startSpeechInput(
                onListening = { listening = it; if (it) notice = "Listening to you…" else if (!thinking) notice = "Ready when you are" },
                onTranscript = { draft = it },
                onProblem = { notice = it }
            )
        } else {
            notice = "Microphone permission was not granted. You can still type."
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                notice = "Reading your selected file…"
                try {
                    val raw = withContext(Dispatchers.IO) {
                        val stream = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Android could not open that file.")
                        MemoryImporter.readBounded(stream)
                    }
                    val extracted = withContext(Dispatchers.Default) { MemoryImporter.extract(raw) }
                    if (extracted.isBlank()) {
                        notice = "No text was found in that file."
                    } else {
                        importedHistory = (importedHistory + "\n\n" + extracted)
                            .trim().takeLast(MemoryImporter.MAX_IMPORTED_CHARS)
                        store.putString("imported_history", importedHistory)
                        notice = "Imported conversation text. Review it in Memory before relying on it."
                    }
                } catch (error: Exception) {
                    notice = error.message ?: "Import failed. Try an extracted conversations.json or a plain-text transcript."
                } finally {
                    importing = false
                }
            }
        }
    }

    fun saveHistory() {
        val array = JSONArray()
        messages.takeLast(150).forEach { message ->
            array.put(JSONObject()
                .put("role", message.role)
                .put("text", message.text)
                .put("timestamp", message.timestamp))
        }
        store.putString("chat_history", array.toString())
    }

    fun sendMessage(raw: String) {
        val text = raw.trim()
        if (text.isBlank() || busy) return
        if (apiKeys[activeProvider].isNullOrBlank()) {
            notice = "Add a " + activeProvider.label + " API key in Settings before asking Friend to reply."
            settingsOpen = true
            return
        }
        activity.stopSpeaking()
        speaking = false
        val userMessage = ChatMessage("user", text)
        messages.add(userMessage)
        while (messages.size > 150) messages.removeAt(0)
        draft = ""
        saveHistory()
        busy = true
        thinking = true
        notice = "Thinking with you…"
        scope.launch {
            try {
                val answer = AiClient.reply(
                    provider = activeProvider,
                    apiKey = apiKeys[activeProvider].orEmpty(),
                    model = model,
                    tone = tone,
                    memoryNotes = memoryNotes,
                    importedHistory = importedHistory,
                    history = messages.toList()
                )
                messages.add(ChatMessage("assistant", answer))
                while (messages.size > 150) messages.removeAt(0)
                saveHistory()
                thinking = false
                notice = "Ready when you are"
                if (voiceReplies) {
                    activity.speak(answer, voiceName, speakingRate) { speaking = it }
                }
            } catch (error: Exception) {
                thinking = false
                speaking = false
                val friendly = "I couldn't connect to " + activeProvider.label + ". " +
                    (error.message ?: "Check your connection, model, and API key in Settings.")
                messages.add(ChatMessage("assistant", friendly))
                while (messages.size > 150) messages.removeAt(0)
                saveHistory()
                notice = friendly
            } finally {
                busy = false
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            activity.stopSpeechInput()
            activity.stopSpeaking()
        }
    }

    val avatarState = when {
        listening -> "listening"
        thinking -> "thinking"
        speaking -> "speaking"
        else -> "idle"
    }

    Column(
        modifier = Modifier.fillMaxSize().background(Ink).imePadding().padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(43.dp).clip(RoundedCornerShape(14.dp)).background(Gold),
                    contentAlignment = Alignment.Center
                ) {
                    Text("CGL+", color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Friend", color = WarmWhite, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                    Text("by CGL+ · AI wellbeing companion", color = Quiet, fontSize = 11.sp)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { memoryOpen = true }) { Text("Memory", color = Gold, fontSize = 12.sp) }
                TextButton(onClick = { settingsOpen = true }) { Text("Settings", color = WarmWhite, fontSize = 12.sp) }
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(23.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                FriendAvatar(state = avatarState)
                Text(
                    when (avatarState) {
                        "listening" -> "I'm listening."
                        "thinking" -> "Let me think that through."
                        "speaking" -> "Talking with you."
                        else -> "I'm here with you."
                    },
                    color = WarmWhite, fontSize = 17.sp, fontWeight = FontWeight.SemiBold
                )
                Text(notice, color = Quiet, fontSize = 11.sp, maxLines = 2)
                Spacer(Modifier.height(5.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    listOf("Gentle", "Calm", "Encouraging", "Neutral", "Reassuring", "To the point").forEach { option ->
                        FilterChip(
                            selected = tone == option,
                            onClick = { tone = option; store.putString("tone", option) },
                            label = { Text(option, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp, bottom = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Conversation", color = WarmWhite, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(activeProvider.label, color = Gold, fontSize = 11.sp)
            TextButton(onClick = {
                activity.stopSpeaking()
                messages.clear()
                saveHistory()
                notice = "A new conversation. Your saved memory stays."
            }) {
                Text("New chat", color = Quiet, fontSize = 11.sp)
            }
        }

        if (messages.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Panel),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Column(Modifier.padding(15.dp)) {
                    Text("You don't need the perfect words.", color = Gold, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(5.dp))
                    Text(
                        "Tell me what's on your mind, or start small. I'm an AI companion, and I can help you organise your thoughts.",
                        color = WarmWhite, fontSize = 13.sp, lineHeight = 19.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(onClick = { draft = "I don't know where to start." }) { Text("Start small", fontSize = 11.sp) }
                        OutlinedButton(onClick = { draft = "Can you help me sort through my feelings?" }) { Text("Sort my feelings", fontSize = 11.sp) }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(messages) { message ->
                    MessageBubble(message)
                }
                if (thinking) {
                    item {
                        Text("Friend is thinking…", color = Gold, fontSize = 12.sp, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }

        if (messages.isEmpty()) Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            OutlinedButton(
                onClick = {
                    if (listening) activity.stopSpeechInput()
                    else if (ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        activity.startSpeechInput(
                            onListening = { listening = it; notice = if (it) "Listening to you…" else "Ready when you are" },
                            onTranscript = { draft = it },
                            onProblem = { notice = it }
                        )
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 54.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = if (listening) Gold else WarmWhite),
                shape = RoundedCornerShape(16.dp)
            ) { Text(if (listening) "■" else "🎙", fontSize = 17.sp) }

            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Say what's on your mind…", fontSize = 13.sp) },
                shape = RoundedCornerShape(16.dp),
                maxLines = 4,
                enabled = !busy
            )

            Button(
                onClick = { sendMessage(draft) },
                enabled = draft.isNotBlank() && !busy,
                modifier = Modifier.defaultMinSize(minWidth = 52.dp, minHeight = 54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Ink)
            ) { Text("➤", fontSize = 18.sp, fontWeight = FontWeight.Bold) }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                voiceReplies = !voiceReplies
                store.putString("voice_replies", voiceReplies.toString())
                if (!voiceReplies) { activity.stopSpeaking(); speaking = false }
            }) { Text(if (voiceReplies) "🔊 Voice replies on" else "🔇 Voice replies off", color = Quiet, fontSize = 11.sp) }
            Text("Local memory · provider API use may be billed", color = Quiet, fontSize = 9.sp)
        }
        Text(
            "Friend is an AI companion, not a therapist. Your chosen provider receives the messages and memory sent to it.",
            color = Quiet, fontSize = 9.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 1.dp, bottom = 2.dp)
        )
    }

    if (settingsOpen) {
        SettingsDialog(
            currentProvider = activeProvider,
            storedKeys = apiKeys.toMap(),
            currentModel = model,
            voices = activity.ttsVoiceNames,
            currentVoice = voiceName,
            currentRate = speakingRate,
            onDismiss = { settingsOpen = false },
            onSave = { selectedProvider, newKey, newModel, newVoice, newRate ->
                activeProvider = selectedProvider
                model = newModel.ifBlank { selectedProvider.defaultModel }
                store.putString("provider", selectedProvider.name)
                store.putString("model_" + selectedProvider.name, model)
                if (newKey.isNotBlank()) {
                    apiKeys[selectedProvider] = newKey.trim()
                    store.putString(selectedProvider.keyName, newKey.trim())
                }
                voiceName = newVoice
                speakingRate = newRate
                store.putString("voice_name", voiceName)
                store.putString("speech_rate", speakingRate.toString())
                settingsOpen = false
                notice = "Settings saved on this device."
            },
            onClearKey = { selectedProvider ->
                apiKeys[selectedProvider] = ""
                store.remove(selectedProvider.keyName)
                notice = selectedProvider.label + " key deleted from this device."
            }
        )
    }

    if (memoryOpen) {
        MemoryDialog(
            notes = memoryNotes,
            importedHistory = importedHistory,
            importing = importing,
            onNotesChange = { memoryNotes = it },
            onImport = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
            onClearImported = {
                importedHistory = ""
                store.remove("imported_history")
                notice = "Imported conversation excerpts deleted."
            },
            onSave = {
                store.putString("memory_notes", memoryNotes)
                store.putString("imported_history", importedHistory)
                memoryOpen = false
                notice = "Memory saved securely on this device."
            },
            onDismiss = { memoryOpen = false }
        )
    }
}

@Composable
private fun FriendAvatar(state: String) {
    val transition = rememberInfiniteTransition(label = "friend-avatar")
    val pulse by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "avatar-pulse"
    )
    val isActive = state == "listening" || state == "speaking"
    val eyeHeight = when (state) {
        "thinking" -> 5.5f
        "listening" -> 13.5f
        else -> 10.5f
    }

    Canvas(Modifier.size(176.dp)) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val unit = size.minDimension / 160f
        fun u(value: Float) = value * unit
        val shellLeft = cx - u(49f)
        val shellTop = cy - u(56f)
        val shellSize = Size(u(98f), u(112f))
        val visorLeft = cx - u(41f)
        val visorTop = cy - u(40f)
        val visorSize = Size(u(82f), u(72f))
        val glow = if (isActive) 0.22f * pulse else 0.10f * pulse

        // Soft halo and a restrained orbital ring keep the face readable on dark UI.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Gold.copy(alpha = glow), Color(0xFF70DCE8).copy(alpha = glow * 0.20f), Color.Transparent),
                center = Offset(cx, cy),
                radius = u(83f)
            ),
            radius = u(83f),
            center = Offset(cx, cy)
        )
        drawCircle(
            color = Gold.copy(alpha = if (isActive) 0.48f else 0.20f),
            radius = u(64f),
            center = Offset(cx, cy),
            style = Stroke(width = u(1f))
        )

        // Compact rounded robot shell, with a dark glass display instead of a flat emoji face.
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF35352F), Color(0xFF171815), Color(0xFF292920)),
                start = Offset(shellLeft, shellTop),
                end = Offset(shellLeft + shellSize.width, shellTop + shellSize.height)
            ),
            topLeft = Offset(shellLeft, shellTop),
            size = shellSize,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(25f), u(25f))
        )
        drawRoundRect(
            color = Gold.copy(alpha = if (isActive) 0.95f else 0.64f),
            topLeft = Offset(shellLeft, shellTop),
            size = shellSize,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(25f), u(25f)),
            style = Stroke(width = u(1.5f))
        )

        // Small side sensor pods make the silhouette feel like a designed device.
        for (side in listOf(-1f, 1f)) {
            drawRoundRect(
                color = Color(0xFF44443A),
                topLeft = Offset(cx + side * u(48f) - u(3f), cy - u(15f)),
                size = Size(u(6f), u(25f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(3f), u(3f))
            )
            drawCircle(
                color = if (state == "listening") Color(0xFF8BE9F0) else Gold.copy(alpha = 0.82f),
                radius = u(1.8f),
                center = Offset(cx + side * u(51f), cy - u(3f))
            )
        }

        // Top status light and fine casing details.
        drawRoundRect(
            color = Color(0xFF0D0E0D),
            topLeft = Offset(cx - u(15f), cy - u(48f)),
            size = Size(u(30f), u(4f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(2f), u(2f))
        )
        drawRoundRect(
            color = if (state == "listening") Color(0xFF8BE9F0) else Gold.copy(alpha = pulse),
            topLeft = Offset(cx - u(7f), cy - u(48f)),
            size = Size(u(14f), u(4f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(2f), u(2f))
        )

        // Glossy visor with a subtle upper reflection.
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF080C0D), Color(0xFF111919), Color(0xFF090B0A)),
                start = Offset(visorLeft, visorTop),
                end = Offset(visorLeft + visorSize.width, visorTop + visorSize.height)
            ),
            topLeft = Offset(visorLeft, visorTop),
            size = visorSize,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(19f), u(19f))
        )
        drawRoundRect(
            color = Color(0xFF8BE9F0).copy(alpha = 0.16f),
            topLeft = Offset(visorLeft + u(5f), visorTop + u(5f)),
            size = Size(u(72f), u(62f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(15f), u(15f)),
            style = Stroke(width = u(0.8f))
        )
        drawLine(
            color = Color.White.copy(alpha = 0.10f),
            start = Offset(visorLeft + u(13f), visorTop + u(7f)),
            end = Offset(visorLeft + u(39f), visorTop + u(7f)),
            strokeWidth = u(1.2f),
            cap = StrokeCap.Round
        )

        // Expressive luminous eyes: attentive, calm while thinking, and brighter when listening.
        val eyeY = cy - u(9f)
        val eyeWidth = u(if (state == "listening") 16f else 14f)
        val eyeH = u(eyeHeight)
        val eyeOffset = u(20f)
        val eyeColor = if (state == "listening") Color(0xFF9CF5FA) else Color(0xFFF5D96B)
        for (side in listOf(-1f, 1f)) {
            val eyeCenter = Offset(cx + side * eyeOffset, eyeY)
            drawRoundRect(
                color = eyeColor.copy(alpha = 0.13f * pulse),
                topLeft = Offset(eyeCenter.x - eyeWidth * 0.72f, eyeCenter.y - eyeH * 1.15f),
                size = Size(eyeWidth * 1.44f, eyeH * 2.3f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(8f), u(8f))
            )
            drawRoundRect(
                color = eyeColor,
                topLeft = Offset(eyeCenter.x - eyeWidth / 2f, eyeCenter.y - eyeH / 2f),
                size = Size(eyeWidth, eyeH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(6f), u(6f))
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.82f),
                radius = u(1.4f),
                center = Offset(eyeCenter.x - u(3f), eyeCenter.y - eyeH * 0.12f)
            )
        }

        // The mouth changes shape while speaking; listening gets a tiny friendly smile.
        if (state == "speaking") {
            drawRoundRect(
                color = Color(0xFF8BE9F0).copy(alpha = 0.20f),
                topLeft = Offset(cx - u(19f), cy + u(18f)),
                size = Size(u(38f), u(15f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(7f), u(7f))
            )
            val bars = listOf(4f, 8f, 12f, 7f, 10f, 5f, 3f)
            bars.forEachIndexed { index, height ->
                val x = cx - u(12f) + index * u(4f)
                drawLine(
                    color = if (index % 2 == 0) Color(0xFF9CF5FA) else Gold,
                    start = Offset(x, cy + u(25f) - u(height) / 2f),
                    end = Offset(x, cy + u(25f) + u(height) / 2f),
                    strokeWidth = u(2f),
                    cap = StrokeCap.Round
                )
            }
        } else {
            val mouth = Path().apply {
                moveTo(cx - u(13f), cy + u(24f))
                cubicTo(
                    cx - u(7f), cy + u(if (state == "listening") 31f else 29f),
                    cx + u(7f), cy + u(if (state == "listening") 31f else 29f),
                    cx + u(13f), cy + u(24f)
                )
            }
            drawPath(
                path = mouth,
                color = if (state == "listening") Color(0xFF9CF5FA) else Gold,
                style = Stroke(width = u(2.2f), cap = StrokeCap.Round)
            )
        }

        // Tiny chin indicator gives the face a finished, product-like detail.
        drawRoundRect(
            color = Gold.copy(alpha = 0.45f),
            topLeft = Offset(cx - u(9f), cy + u(43f)),
            size = Size(u(18f), u(2f)),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(1f), u(1f))
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            color = if (isUser) Gold else PanelRaised,
            contentColor = if (isUser) Ink else WarmWhite,
            shape = RoundedCornerShape(
                topStart = 17.dp,
                topEnd = 17.dp,
                bottomEnd = if (isUser) 5.dp else 17.dp,
                bottomStart = if (isUser) 17.dp else 5.dp
            ),
            modifier = Modifier.fillMaxWidth(0.88f)
        ) {
            Text(
                text = message.text,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 10.dp),
                fontSize = 13.sp,
                lineHeight = 19.sp
            )
        }
    }
}

@Composable
private fun SettingsDialog(
    currentProvider: AIProvider,
    storedKeys: Map<AIProvider, String>,
    currentModel: String,
    voices: List<String>,
    currentVoice: String,
    currentRate: Float,
    onDismiss: () -> Unit,
    onSave: (AIProvider, String, String, String, Float) -> Unit,
    onClearKey: (AIProvider) -> Unit
) {
    var selectedProvider by remember { mutableStateOf(currentProvider) }
    var model by remember { mutableStateOf(currentModel) }
    var newKey by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    var voiceMenuOpen by remember { mutableStateOf(false) }
    var selectedVoice by remember { mutableStateOf(currentVoice) }
    var rate by remember { mutableStateOf(currentRate) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Friend settings", color = WarmWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("AI provider", color = Quiet, fontSize = 12.sp)
                Box {
                    OutlinedButton(onClick = { menuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(selectedProvider.label + "  ▾")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        AIProvider.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedProvider = option
                                    model = if (option == currentProvider) currentModel else option.defaultModel
                                    newKey = ""
                                    menuOpen = false
                                }
                            )
                        }
                    }
                }
                Text(
                    if (storedKeys[selectedProvider].orEmpty().isNotBlank()) "A key is saved encrypted on this device. Paste another to replace it." else "No key is saved for this provider yet.",
                    color = Quiet, fontSize = 11.sp, lineHeight = 15.sp
                )
                OutlinedTextField(
                    value = newKey,
                    onValueChange = { newKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key (optional until you save)") },
                    placeholder = { Text("Paste your own key") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                OutlinedButton(
                    onClick = { onClearKey(selectedProvider); newKey = "" },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Delete saved " + selectedProvider.label + " key", fontSize = 11.sp) }
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model name") },
                    singleLine = true
                )
                Divider(color = Border)
                Text("Device speaking voice", color = Quiet, fontSize = 12.sp)
                Box {
                    OutlinedButton(onClick = { voiceMenuOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        Text((selectedVoice.ifBlank { "System default voice" }).take(33) + "  ▾", fontSize = 11.sp)
                    }
                    DropdownMenu(expanded = voiceMenuOpen, onDismissRequest = { voiceMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("System default voice") }, onClick = { selectedVoice = ""; voiceMenuOpen = false })
                        voices.take(80).forEach { voice ->
                            DropdownMenuItem(text = { Text(voice, fontSize = 12.sp) }, onClick = { selectedVoice = voice; voiceMenuOpen = false })
                        }
                    }
                }
                Text("Speaking speed: " + String.format(Locale.US, "%.2f×", rate), color = Quiet, fontSize = 12.sp)
                Slider(value = rate, onValueChange = { rate = it }, valueRange = 0.7f..1.3f)
                Text(
                    "API usage may cost money. Keys are encrypted with Android Keystore on this device, but a rooted or compromised device can still expose app data at runtime.",
                    color = Quiet, fontSize = 10.sp, lineHeight = 14.sp
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(selectedProvider, newKey, model, selectedVoice, rate) }) {
                Text("Save settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = Quiet) }
        }
    )
}

@Composable
private fun MemoryDialog(
    notes: String,
    importedHistory: String,
    importing: Boolean,
    onNotesChange: (String) -> Unit,
    onImport: () -> Unit,
    onClearImported: () -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Friend's memory", color = WarmWhite, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(510.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text(
                    "Write what you want Friend to remember across chats and providers. This is encrypted and saved locally on this device.",
                    color = Quiet, fontSize = 11.sp, lineHeight = 16.sp
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    label = { Text("Your saved notes") },
                    placeholder = { Text("Preferences, ongoing topics, things you asked Friend to remember…") }
                )
                Divider(color = Border)
                Text("Imported older chats", color = WarmWhite, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Text(
                    if (importedHistory.isBlank()) "No older chat excerpts imported." else "Saved excerpts: " + importedHistory.length + " characters. You can clear them below.",
                    color = Quiet, fontSize = 11.sp
                )
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedButton(onClick = onImport, enabled = !importing, modifier = Modifier.weight(1f)) {
                        Text(if (importing) "Importing…" else "Import .json / .txt", fontSize = 11.sp)
                    }
                    OutlinedButton(onClick = onClearImported, enabled = importedHistory.isNotBlank()) {
                        Text("Clear import", fontSize = 11.sp)
                    }
                }
                Text(
                    "For ChatGPT exports, unzip the download and select conversations.json. Import only data you are comfortable keeping on this device and sending to your chosen AI provider.",
                    color = Quiet, fontSize = 10.sp, lineHeight = 14.sp
                )
            }
        },
        confirmButton = { Button(onClick = onSave) { Text("Save memory") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Quiet) } }
    )
}

private fun loadHistory(source: String): List<ChatMessage> {
    return try {
        val array = JSONArray(source)
        val messages = mutableListOf<ChatMessage>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            val role = item.optString("role", "")
            val text = item.optString("text", "")
            if ((role == "user" || role == "assistant") && text.isNotBlank()) {
                messages.add(ChatMessage(role, text, item.optLong("timestamp", System.currentTimeMillis())))
            }
        }
        messages.takeLast(150)
    } catch (_: Exception) {
        emptyList()
    }
}
