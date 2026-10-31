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
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
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
            AIProvider.entries.forEach { provider -> put(provider, if (provider.keyName.isBlank()) "" else store.getString(provider.keyName)) }
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
    var useAvai by remember { mutableStateOf(store.getString("use_avai", "true") == "true") }
    var chatListOpen by remember { mutableStateOf(false) }
    var activeChatId by remember { mutableStateOf(store.getString("active_chat_id", "main")) }
    var providerOverrideForChat by remember(activeChatId) {
        mutableStateOf(store.getString("provider_override_" + activeChatId, "false") == "true")
    }
    var modelOverrideForChat by remember(activeChatId) {
        mutableStateOf(store.getString("model_override_" + activeChatId, "false") == "true")
    }
    var chatSessions by remember {
        val saved = loadFriendSessions(store.getString("chat_sessions", "[]"))
        val legacy = loadHistory(store.getString("chat_history", "[]"))
        val legacyId = store.getString("active_chat_id", "main")
        val title = legacy.firstOrNull { it.role == "user" }?.text?.replace("\n", " ")?.take(38) ?: "Previous chat"
        mutableStateOf(if (saved.isNotEmpty()) saved else if (legacy.isNotEmpty()) listOf(FriendChatSession(legacyId, title, System.currentTimeMillis())) else emptyList())
    }
    var memoryOpen by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    val messages = remember(activeChatId) {
        val savedHistory = store.getString(
            "chat_history_" + activeChatId,
            if (activeChatId == "main") store.getString("chat_history", "[]") else "[]"
        )
        mutableStateListOf<ChatMessage>().apply { addAll(loadHistory(savedHistory)) }
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

    fun writeChatSessions() {
        val array = JSONArray()
        chatSessions.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("updatedAt", item.updatedAt)
                .put("provider", item.providerName.orEmpty())
                .put("model", item.model.orEmpty())
                .put("useAvai", item.useAvai)
                .put("providerOverride", item.providerOverride)
                .put("modelOverride", item.modelOverride))
        }
        store.putString("chat_sessions", array.toString())
        store.putString("active_chat_id", activeChatId)
    }

    fun saveHistory() {
        val array = JSONArray()
        messages.takeLast(150).forEach { message ->
            array.put(JSONObject()
                .put("role", message.role)
                .put("text", message.text)
                .put("timestamp", message.timestamp))
        }
        val json = array.toString()
        store.putString("chat_history_" + activeChatId, json)
        store.putString("chat_history", json)
        val old = chatSessions.firstOrNull { it.id == activeChatId }
        val firstPrompt = messages.firstOrNull { it.role == "user" }?.text?.replace("\n", " ")?.take(38)
        val updated = FriendChatSession(
            id = activeChatId,
            title = if (old == null || old.title == "New chat") firstPrompt?.ifBlank { "New chat" } ?: "New chat" else old.title,
            updatedAt = System.currentTimeMillis(),
            providerName = activeProvider.name,
            model = model,
            useAvai = useAvai,
            providerOverride = providerOverrideForChat,
            modelOverride = modelOverrideForChat
        )
        chatSessions = (chatSessions.filterNot { it.id == activeChatId } + updated).sortedByDescending { it.updatedAt }
        writeChatSessions()
    }

    fun startNewFriendChat() {
        if (messages.isNotEmpty()) saveHistory()
        val id = UUID.randomUUID().toString()
        activeChatId = id
        store.putString("active_chat_id", id)
        providerOverrideForChat = false
        modelOverrideForChat = false
        store.putString("provider_override_" + id, "false")
        store.putString("model_override_" + id, "false")
        val item = FriendChatSession(
            id, "New chat", System.currentTimeMillis(),
            providerName = activeProvider.name,
            model = model,
            useAvai = useAvai,
            providerOverride = false,
            modelOverride = false
        )
        chatSessions = (listOf(item) + chatSessions.filterNot { it.id == id }).sortedByDescending { it.updatedAt }
        writeChatSessions()
        draft = ""
        chatListOpen = false
        notice = "New conversation started"
    }

    fun openFriendChat(item: FriendChatSession) {
        if (item.id != activeChatId && messages.isNotEmpty()) saveHistory()
        activeChatId = item.id
        store.putString("active_chat_id", item.id)
        item.providerName?.takeIf { it.isNotBlank() }?.let { activeProvider = AIProvider.fromStored(it) }
        item.model?.takeIf { it.isNotBlank() }?.let { model = it }
        useAvai = item.useAvai
        providerOverrideForChat = item.providerOverride
        modelOverrideForChat = item.modelOverride
        store.putString("provider_override_" + item.id, providerOverrideForChat.toString())
        store.putString("model_override_" + item.id, modelOverrideForChat.toString())
        chatListOpen = false
        notice = item.title
    }

    fun deleteFriendChat(item: FriendChatSession) {
        store.remove("chat_history_" + item.id)
        chatSessions = chatSessions.filterNot { it.id == item.id }
        writeChatSessions()
        if (item.id == activeChatId) {
            activeChatId = UUID.randomUUID().toString()
            providerOverrideForChat = false
            modelOverrideForChat = false
            store.putString("active_chat_id", activeChatId)
        }
        notice = "Conversation deleted"
    }

    fun sendMessage(raw: String) {
        val text = raw.trim()
        if (text.isBlank() || busy) return
        if (!useAvai && activeProvider == AIProvider.LOCAL) {
            notice = "Offline models run through AVai. Switch to AI: AVai to use the local model."
            useAvai = true
            store.putString("use_avai", "true")
            return
        }
        if (!useAvai && apiKeys[activeProvider].isNullOrBlank()) {
            notice = "Add a " + activeProvider.label + " API key in Settings, or switch to AVai mode."
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
                val answer = if (useAvai) {
                    AiBridgeClient.reply(
                        context = context,
                        prompt = text,
                        tone = tone,
                        memory = memoryNotes,
                        importedHistory = importedHistory,
                        history = messages.toList(),
                        chatId = activeChatId,
                        provider = activeProvider,
                        model = model,
                        providerOverride = providerOverrideForChat,
                        modelOverride = modelOverrideForChat
                    )
                } else {
                    AiClient.reply(
                        provider = activeProvider,
                        apiKey = apiKeys[activeProvider].orEmpty(),
                        model = model,
                        tone = tone,
                        memoryNotes = memoryNotes,
                        importedHistory = importedHistory,
                        history = messages.toList()
                    )
                }
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
                val friendly = (if (useAvai) "I couldn't get a response through AVai. " else "I couldn't connect to " + activeProvider.label + ". ") +
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

    BoxWithConstraints(Modifier.fillMaxSize().background(Ink)) {
        val desktopWindow = maxWidth >= 840.dp
        Column(
            modifier = Modifier.fillMaxWidth()
                .widthIn(max = if (desktopWindow) 1200.dp else 840.dp)
                .fillMaxHeight()
                .align(Alignment.TopCenter)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                        false
                    } else when (event.key) {
                        Key.Enter -> { sendMessage(draft); true }
                        Key.N -> { startNewFriendChat(); true }
                        else -> false
                    }
                }
                .imePadding()
                .padding(horizontal = if (desktopWindow) 24.dp else 14.dp, vertical = 10.dp)
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
                TextButton(onClick = {
                    chatSessions = loadFriendSessions(store.getString("chat_sessions", "[]"))
                    chatListOpen = true
                }) { Text("Chats", color = Gold, fontSize = 12.sp) }
                TextButton(onClick = { memoryOpen = true }) { Text("Memory", color = Gold, fontSize = 12.sp) }
                TextButton(onClick = { settingsOpen = true }) { Text("Settings", color = WarmWhite, fontSize = 12.sp) }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                useAvai = !useAvai
                store.putString("use_avai", useAvai.toString())
                saveHistory()
                notice = if (useAvai) "Friend will use AVai's selected provider; connect to AVai to grant access." else "Direct mode uses keys stored in Friend."
            }) { Text(if (useAvai) "AI: AVai" else "AI: Direct", color = if (useAvai) Gold else WarmWhite, fontSize = 11.sp) }
        }
        OutlinedButton(
            onClick = {
                try {
                    context.startActivity(Intent("com.cgl.avai.action.CONSENT").setPackage("com.cgl.avai"))
                } catch (_: Exception) {
                    notice = "Install AVai first, then connect both apps using the same signed build."
                }
            },
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
        ) { Text(if (useAvai) "Connect / approve AVai access" else "Open AVai settings", fontSize = 12.sp) }

        Card(
            colors = CardDefaults.cardColors(containerColor = Panel),
            shape = RoundedCornerShape(23.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                FriendAvatar(state = avatarState, size = if (desktopWindow) 154.dp else 190.dp)
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
            Text(
                if (useAvai && !providerOverrideForChat) "AVai-selected provider" else activeProvider.label,
                color = Gold, fontSize = 11.sp
            )
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
            Text(if (desktopWindow) "Ctrl+Enter send · Ctrl+N new chat" else "Local memory · provider API use may be billed", color = Quiet, fontSize = 9.sp)
        }
        Text(
            "Friend is an AI companion, not a therapist. Your chosen provider receives the messages and memory sent to it.",
            color = Quiet, fontSize = 9.sp, lineHeight = 12.sp, modifier = Modifier.padding(top = 1.dp, bottom = 2.dp)
        )
        }
    }

    if (chatListOpen) {
        AlertDialog(
            onDismissRequest = { chatListOpen = false },
            title = { Text("Friend conversations", color = WarmWhite) },
            text = {
                Column(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (chatSessions.isEmpty()) Text("Your saved conversations will appear here.", color = Quiet, fontSize = 12.sp)
                    chatSessions.sortedByDescending { it.updatedAt }.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            TextButton(onClick = { openFriendChat(item) }, modifier = Modifier.weight(1f)) {
                                Text((if (item.id == activeChatId) "• " else "") + item.title, color = if (item.id == activeChatId) Gold else WarmWhite, maxLines = 2)
                            }
                            TextButton(onClick = { deleteFriendChat(item) }) { Text("Delete", color = Quiet, fontSize = 10.sp) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { chatListOpen = false }) { Text("Close", color = Gold) } },
            dismissButton = { TextButton(onClick = { startNewFriendChat() }) { Text("New chat", color = Gold) } },
            containerColor = Panel
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
                model = if (selectedProvider == AIProvider.LOCAL) selectedProvider.defaultModel
                    else newModel.ifBlank { selectedProvider.defaultModel }
                providerOverrideForChat = true
                modelOverrideForChat = selectedProvider != AIProvider.LOCAL && model.isNotBlank()
                store.putString("provider", selectedProvider.name)
                store.putString("model_" + selectedProvider.name, model)
                store.putString("provider_override_" + activeChatId, "true")
                store.putString("model_override_" + activeChatId, modelOverrideForChat.toString())
                if (selectedProvider != AIProvider.LOCAL && newKey.isNotBlank()) {
                    apiKeys[selectedProvider] = newKey.trim()
                    store.putString(selectedProvider.keyName, newKey.trim())
                }
                voiceName = newVoice
                speakingRate = newRate
                store.putString("voice_name", voiceName)
                store.putString("speech_rate", speakingRate.toString())
                saveHistory()
                settingsOpen = false
                notice = if (selectedProvider == AIProvider.LOCAL)
                    "Offline model selected in Friend. Choose AI: AVai and approve the connection."
                else "Settings saved on this device."
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
private fun FriendAvatar(state: String, size: Dp = 190.dp) {
    val transition = rememberInfiniteTransition(label = "friend-features")
    val floatY by transition.animateFloat(
        initialValue = -3f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(2400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "features-float"
    )
    val blinkScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 120, delayMillis = 2700),
            repeatMode = RepeatMode.Reverse
        ),
        label = "eye-blink"
    )
    val speakingMouthHeight by transition.animateFloat(
        initialValue = 4f,
        targetValue = 13f,
        animationSpec = infiniteRepeatable(
            animation = tween(190),
            repeatMode = RepeatMode.Reverse
        ),
        label = "speaking-mouth"
    )

    val listening = state == "listening"
    val speaking = state == "speaking"
    val thinking = state == "thinking"

    Canvas(Modifier.size(size)) {
        val cx = size.width / 2f
        val baseY = size.height / 2f + floatY * (size.minDimension / 160f)
        val unit = size.minDimension / 160f
        fun u(v: Float) = v * unit

        // Nothing is drawn except the eyes and mouth: no head, shell, ring, brows, or particles.
        val eyeY = baseY - u(12f)
        val eyeOffset = u(32f)
        val eyeWidth = u(18f)
        val naturalEyeHeight = when {
            thinking -> 7f
            listening -> 13f
            else -> 10f
        }
        val eyeHeight = u(naturalEyeHeight) * blinkScale
        val featureColor = if (listening) Color(0xFF9CF5FA) else Gold

        for (side in listOf(-1f, 1f)) {
            val eyeX = cx + side * eyeOffset
            drawRoundRect(
                color = featureColor,
                topLeft = Offset(eyeX - eyeWidth / 2f, eyeY - eyeHeight / 2f),
                size = Size(eyeWidth, eyeHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(8f), u(8f))
            )
        }

        if (speaking) {
            val mouthHeight = u(speakingMouthHeight)
            drawRoundRect(
                color = Gold,
                topLeft = Offset(cx - u(16f), baseY + u(27f) - mouthHeight / 2f),
                size = Size(u(32f), mouthHeight),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(u(9f), u(9f))
            )
        } else {
            val smile = Path().apply {
                moveTo(cx - u(16f), baseY + u(25f))
                cubicTo(
                    cx - u(8f), baseY + u(if (listening) 36f else 34f),
                    cx + u(8f), baseY + u(if (listening) 36f else 34f),
                    cx + u(16f), baseY + u(25f)
                )
            }
            drawPath(
                path = smile,
                color = featureColor,
                style = Stroke(width = u(2.6f), cap = StrokeCap.Round)
            )
        }
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
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
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
                if (selectedProvider == AIProvider.LOCAL) {
                    Text(
                        "Friend will ask AVai to run its imported .litertlm model on-device. The local model must be configured in AVai first; no API key is needed.",
                        color = Quiet, fontSize = 11.sp, lineHeight = 15.sp
                    )
                } else {
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
                        label = { Text("Model name (per chat)") },
                        singleLine = true
                    )
                }
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

private data class FriendChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val providerName: String? = null,
    val model: String? = null,
    val useAvai: Boolean = true,
    val providerOverride: Boolean = false,
    val modelOverride: Boolean = false
)

private fun loadFriendSessions(source: String): List<FriendChatSession> = try {
    val array = JSONArray(source)
    (0 until array.length()).mapNotNull { index ->
        val item = array.optJSONObject(index) ?: return@mapNotNull null
        val id = item.optString("id")
        if (id.isBlank()) null else FriendChatSession(
            id = id,
            title = item.optString("title", "Chat").ifBlank { "Chat" }.take(80),
            updatedAt = item.optLong("updatedAt", 0L),
            providerName = item.optString("provider").ifBlank { null },
            model = item.optString("model").ifBlank { null },
            useAvai = item.optBoolean("useAvai", true),
            providerOverride = item.optBoolean("providerOverride", false),
            modelOverride = item.optBoolean("modelOverride", false)
        )
    }.take(100)
} catch (_: Exception) { emptyList() }

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
