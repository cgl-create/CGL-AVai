package com.cgl.avai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

private val Ink = Color(0xFF10100F)
private val Panel = Color(0xFF1B1B19)
private val Gold = Color(0xFFF2C94C)
private val Paper = Color(0xFFF5F3ED)
private val Muted = Color(0xFFAAA79D)

class MainActivity : ComponentActivity() {
    private lateinit var store: SecureStore
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Gold, background = Ink, surface = Panel,
                onPrimary = Ink, onBackground = Paper, onSurface = Paper
            )) {
                AVaiApp(store, intent?.action == "com.cgl.avai.action.CONSENT")
            }
        }
    }
}

@Composable
private fun AVaiApp(store: SecureStore, launchedForConsent: Boolean) {
    val scope = rememberCoroutineScope()
    var providerName by remember { mutableStateOf(store.getString("provider", AIProvider.GEMINI.name)) }
    var model by remember { mutableStateOf(store.getString("model_" + providerName, AIProvider.fromStored(providerName).defaultModel)) }
    var apiProfiles by remember { mutableStateOf(loadApiProfiles(store.getString("api_profiles", "[]"))) }
    var prompt by remember { mutableStateOf("") }
    var settingsOpen by remember { mutableStateOf(false) }
    var localModelOpen by remember { mutableStateOf(false) }
    var accountOpen by remember { mutableStateOf(false) }
    var accountLabel by remember { mutableStateOf("Account") }
    var historyOpen by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Your AI workspace · keys stay on this device") }
    var sessions by remember {
        val savedSessions = loadSessions(store.getString("chat_sessions", "[]"))
        val legacyMessages = loadMessages(store.getString("chat", "[]"))
        val legacyId = store.getString("active_chat", "main")
        val legacyTitle = legacyMessages.firstOrNull { it.role == "user" }?.text
            ?.replace("\n", " ")?.take(38)?.ifBlank { "Previous chat" } ?: "Previous chat"
        val migratedSessions = if (savedSessions.isEmpty() && legacyMessages.isNotEmpty()) {
            listOf(ChatSession(legacyId, legacyTitle, System.currentTimeMillis()))
        } else savedSessions
        mutableStateOf(migratedSessions)
    }
    var activeChatId by remember { mutableStateOf(store.getString("active_chat", "main")) }
    var chats by remember { mutableStateOf(loadMessages(store.getString("chat_" + activeChatId, store.getString("chat", "[]")))) }
    var showConsent by remember { mutableStateOf(launchedForConsent) }
    var consentAnswer by remember { mutableStateOf("") }
    val provider = AIProvider.fromStored(providerName)

    fun saveApiProfiles() {
        val array = JSONArray()
        apiProfiles.forEach { item ->
            array.put(JSONObject().put("id", item.id).put("name", item.name).put("provider", item.provider.name))
        }
        store.putString("api_profiles", array.toString())
    }

    fun selectedApiKey(forProvider: AIProvider): String {
        val activeId = store.getString("active_profile_" + forProvider.name)
        val namedKey = if (activeId.isNotBlank()) store.getString("profile_key_" + activeId) else ""
        return namedKey.ifBlank {
            store.getString("key_" + forProvider.name).ifBlank { store.getString(forProvider.keyName) }
        }
    }

    fun writeSessions() {
        val array = JSONArray()
        sessions.forEach { item ->
            array.put(JSONObject()
                .put("id", item.id)
                .put("title", item.title)
                .put("updatedAt", item.updatedAt)
                .put("provider", item.providerName.orEmpty())
                .put("model", item.model.orEmpty()))
        }
        store.putString("chat_sessions", array.toString())
    }

    fun persistChats() {
        val array = JSONArray()
        chats.forEach { array.put(JSONObject().put("role", it.role).put("text", it.text).put("timestamp", it.timestamp)) }
        store.putString("chat_" + activeChatId, array.toString())
        store.putString("chat", array.toString())
        val old = sessions.firstOrNull { it.id == activeChatId }
        val firstPrompt = chats.firstOrNull { it.role == "user" }?.text?.replace("\n", " ")?.take(38)
        val updated = ChatSession(
            id = activeChatId,
            title = if (old == null || old.title == "New chat") firstPrompt?.ifBlank { "New chat" } ?: "New chat" else old.title,
            updatedAt = System.currentTimeMillis(),
            providerName = providerName,
            model = model
        )
        val savedSessions = loadSessions(store.getString("chat_sessions", "[]"))
        sessions = (savedSessions.filterNot { it.id == activeChatId } + updated).sortedByDescending { it.updatedAt }
        writeSessions()
        store.putString("active_chat", activeChatId)
    }

    fun startNewChat() {
        if (chats.isNotEmpty()) persistChats()
        activeChatId = UUID.randomUUID().toString()
        chats = emptyList()
        prompt = ""
        val item = ChatSession(activeChatId, "New chat", System.currentTimeMillis(), providerName, model)
        val savedSessions = loadSessions(store.getString("chat_sessions", "[]"))
        sessions = (listOf(item) + savedSessions.filterNot { it.id == item.id }).sortedByDescending { it.updatedAt }
        store.putString("active_chat", activeChatId)
        writeSessions()
        status = "New conversation started"
    }

    fun openChat(item: ChatSession) {
        if (item.id != activeChatId && chats.isNotEmpty()) persistChats()
        activeChatId = item.id
        store.putString("active_chat", item.id)
        item.providerName?.takeIf { it.isNotBlank() }?.let { providerName = AIProvider.fromStored(it).name }
        val selectedProvider = AIProvider.fromStored(providerName)
        model = item.model?.takeIf { it.isNotBlank() }
            ?: store.getString("model_" + selectedProvider.name, selectedProvider.defaultModel)
        store.putString("provider", providerName)
        store.putString("model", model)
        chats = loadMessages(store.getString("chat_" + item.id, "[]"))
        historyOpen = false
        status = item.title
    }

    fun deleteChat(item: ChatSession) {
        store.remove("chat_" + item.id)
        sessions = loadSessions(store.getString("chat_sessions", "[]")).filterNot { it.id == item.id }
        writeSessions()
        if (item.id == activeChatId) {
            activeChatId = UUID.randomUUID().toString()
            chats = emptyList()
            store.putString("active_chat", activeChatId)
        }
        status = "Conversation deleted"
    }

    fun send() {
        val text = prompt.trim()
        if (text.isEmpty() || busy) return
        val key = if (provider == AIProvider.LOCAL) "" else selectedApiKey(provider)
        if (provider == AIProvider.LOCAL && store.getString("local_model_path").isBlank()) {
            status = "Import a .litertlm file before using the offline model."
            localModelOpen = true
            return
        }
        if (provider != AIProvider.LOCAL && key.isBlank()) {
            status = "Connect ${provider.label} before chatting."
            settingsOpen = true
            return
        }
        chats = chats + ChatMessage("user", text)
        prompt = ""
        persistChats()
        busy = true
        status = "Thinking with ${provider.label}…"
        scope.launch {
            try {
                val answer = AiClient.reply(
                    provider, key, model, "clear, calm and helpful",
                    store.getString("memory"), "", chats.takeLast(24),
                    localModelPath = store.getString("local_model_path")
                )
                chats = chats + ChatMessage("assistant", answer)
                status = "Connected to ${provider.label}"
                persistChats()
            } catch (error: Exception) {
                chats = chats + ChatMessage(
                    "assistant",
                    "I couldn't complete that request: ${error.message ?: "unknown error"}. Check your API key, model ID, connection and provider quota."
                )
                status = "Request failed"
                persistChats()
            } finally {
                busy = false
            }
        }
    }

    if (showConsent) {
        AlertDialog(
            onDismissRequest = {
                store.putString("friend_access_once_until", "0")
                showConsent = false
                consentAnswer = "No access was granted."
                status = consentAnswer
            },
            title = { Text("Friend wants to use AVai") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Allow Friend to use AVai with your AI configurations and connections?")
                    Text("AVai will send your messages to the provider selected here. Your API keys stay inside AVai and are never given to Friend.", color = Muted, fontSize = 12.sp)
                    if (consentAnswer.isNotBlank()) Text(consentAnswer, color = Gold)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    store.putString("friend_access_always", "true")
                    store.putString("friend_access_once_until", "0")
                    consentAnswer = "Friend is always allowed. Revoke this in AVai Settings."
                    showConsent = false
                    status = consentAnswer
                }) { Text("Always allow") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = {
                        store.putString("friend_access_always", "false")
                        store.putString("friend_access_once_until", (System.currentTimeMillis() + 300_000L).toString())
                        consentAnswer = "Friend may make one AI request."
                        showConsent = false
                        status = consentAnswer
                    }) { Text("Allow once") }
                    TextButton(onClick = {
                        store.putString("friend_access_always", "false")
                        store.putString("friend_access_once_until", "0")
                        consentAnswer = "Friend access denied."
                        showConsent = false
                        status = consentAnswer
                    }) { Text("Deny") }
                }
            }
        )
    }

    Column(Modifier.fillMaxSize().background(Ink).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.size(44.dp).background(Gold, RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
                Text("A", color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Black)
            }
            Column(Modifier.weight(1f)) {
                Text("AVai", color = Paper, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("CGL+ AI Workspace", color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = {
                sessions = loadSessions(store.getString("chat_sessions", "[]"))
                historyOpen = true
            }) { Text("History", color = Gold) }
            TextButton(onClick = { settingsOpen = true }) { Text("Settings", color = Gold) }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                OutlinedButton(onClick = { expanded = true }) { Text(provider.label, fontSize = 12.sp) }
                DropdownMenu(expanded, { expanded = false }) {
                    AIProvider.entries.forEach { item ->
                        DropdownMenuItem(text = { Text(item.label) }, onClick = {
                            providerName = item.name
                            model = store.getString("model_" + item.name, item.defaultModel)
                            store.putString("provider", item.name)
                            expanded = false
                        })
                    }
                }
            }
            if (provider == AIProvider.LOCAL) {
                Column(Modifier.weight(1f)) {
                    Text("ON-DEVICE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text(store.getString("local_model_name", "No offline model imported"), color = Muted, fontSize = 11.sp, maxLines = 2)
                }
                TextButton(onClick = { localModelOpen = true }) { Text("Model", color = Gold) }
            } else {
                OutlinedTextField(
                    value = model, onValueChange = {
                        model = it
                        store.putString("model_" + provider.name, it)
                    }, modifier = Modifier.weight(1f), label = { Text("Model ID") }, singleLine = true
                )
            }
        }
        Text(status, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (chats.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(top = 62.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Your AI. Your choice.", color = Paper, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("Connect a provider, then start chatting.", color = Muted)
                }
            }
            items(chats) { message ->
                Column(Modifier.fillMaxWidth(), horizontalAlignment = if (message.role == "user") Alignment.End else Alignment.Start) {
                    Text(if (message.role == "user") "YOU" else "AVAI", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Surface(color = if (message.role == "user") Color(0xFF34301F) else Panel, shape = RoundedCornerShape(16.dp)) {
                        Text(message.text, Modifier.padding(12.dp), color = Paper, fontSize = 14.sp)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = prompt, onValueChange = { prompt = it }, modifier = Modifier.weight(1f),
                label = { Text("Message AVai") }, maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send() })
            )
            Button(onClick = { send() }, enabled = !busy && prompt.isNotBlank(), modifier = Modifier.height(54.dp)) {
                Text(if (busy) "…" else "Send")
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { startNewChat() }) { Text("New chat", color = Gold) }
            TextButton(onClick = { accountOpen = true }) { Text(accountLabel.take(12), color = Gold, fontSize = 11.sp) }
            TextButton(onClick = { settingsOpen = true }) { Text("Memory & AI", color = Gold) }
        }
    }

    if (settingsOpen) {
        var chosen by remember { mutableStateOf(providerName) }
        var keyInput by remember { mutableStateOf("") }
        var selectedKeyProfileId by remember { mutableStateOf(store.getString("active_profile_" + providerName)) }
        var profileNameInput by remember {
            mutableStateOf(apiProfiles.firstOrNull { it.id == selectedKeyProfileId }?.name ?: "Default")
        }
        var modelInput by remember { mutableStateOf(model) }
        var memoryInput by remember { mutableStateOf(store.getString("memory")) }
        AlertDialog(
            onDismissRequest = { settingsOpen = false },
            title = { Text("AI connections & memory") },
            text = {
                Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("API keys and chat history are encrypted locally with Android Keystore. API use may cost money. A ChatGPT subscription does not include OpenAI API usage.", color = Muted, fontSize = 12.sp)
                    AIProvider.entries.forEach { item ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = chosen == item.name, onClick = {
                                chosen = item.name
                                modelInput = store.getString("model_" + item.name, item.defaultModel)
                                keyInput = ""
                                selectedKeyProfileId = store.getString("active_profile_" + item.name)
                                profileNameInput = apiProfiles.firstOrNull { it.id == selectedKeyProfileId }?.name ?: "Default"
                            })
                            Column {
                                Text(item.label)
                                Text(
                                    when {
                                        item == AIProvider.LOCAL && store.getString("local_model_path").isNotBlank() -> "Offline model imported"
                                        item == AIProvider.LOCAL -> "No API key required"
                                        apiProfiles.any { it.provider == item } || store.getString("key_" + item.name).isNotBlank() || store.getString(item.keyName).isNotBlank() -> "Configured on this device"
                                        else -> "Not connected"
                                    },
                                    color = Muted, fontSize = 11.sp
                                )
                            }
                        }
                    }
                    if (AIProvider.fromStored(chosen) != AIProvider.LOCAL) {
                    Text("Saved key profiles for " + AIProvider.fromStored(chosen).label, color = Gold, fontSize = 12.sp)
                    val savedProfiles = apiProfiles.filter { it.provider.name == chosen }
                    if (savedProfiles.isEmpty()) {
                        Text(if (store.getString("key_" + chosen).isNotBlank() || store.getString(AIProvider.fromStored(chosen).keyName).isNotBlank()) "Legacy default key is available. Add a named profile below to keep multiple keys." else "No named key profiles yet.", color = Muted, fontSize = 11.sp)
                    }
                    savedProfiles.forEach { profile ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            RadioButton(
                                selected = selectedKeyProfileId == profile.id,
                                onClick = {
                                    selectedKeyProfileId = profile.id
                                    profileNameInput = profile.name
                                    keyInput = ""
                                    store.putString("active_profile_" + chosen, profile.id)
                                    status = "Active key profile: " + profile.name
                                }
                            )
                            Text(profile.name, Modifier.weight(1f), fontSize = 12.sp)
                            TextButton(onClick = {
                                store.remove("profile_key_" + profile.id)
                                apiProfiles = apiProfiles.filterNot { it.id == profile.id }
                                if (selectedKeyProfileId == profile.id) {
                                    selectedKeyProfileId = ""
                                    store.remove("active_profile_" + chosen)
                                }
                                saveApiProfiles()
                                status = "API key profile deleted"
                            }) { Text("Delete", fontSize = 10.sp, color = Muted) }
                        }
                    }
                    OutlinedTextField(
                        value = profileNameInput,
                        onValueChange = { profileNameInput = it.take(48) },
                        label = { Text("Key profile name") }, singleLine = true
                    )
                    OutlinedTextField(value = keyInput, onValueChange = { keyInput = it }, label = { Text("API key for this profile (blank keeps it)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(value = modelInput, onValueChange = { modelInput = it }, label = { Text("Model ID") }, singleLine = true)
                    } else {
                        Text("Offline inference runs entirely on this device. Import a .litertlm model file; no API key is needed.", color = Muted, fontSize = 11.sp)
                        Button(onClick = { settingsOpen = false; localModelOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("Manage offline model") }
                    }
                    OutlinedTextField(value = memoryInput, onValueChange = { memoryInput = it }, label = { Text("Your AI memory notes") }, minLines = 2)
                    Text("CGL+ Accounts needs this app registered in Firebase and its real google-services.json.", color = Muted, fontSize = 11.sp)
                    Divider(color = Color(0xFF383832))
                    Text(
                        "Friend access: " + when {
                            store.getString("friend_access_always") == "true" -> "Always allowed"
                            (store.getString("friend_access_once_until").toLongOrNull() ?: 0L) > System.currentTimeMillis() -> "One-time grant pending"
                            else -> "Not allowed"
                        },
                        color = Muted, fontSize = 12.sp
                    )
                    TextButton(onClick = {
                        store.putString("friend_access_always", "false")
                        store.putString("friend_access_once_until", "0")
                        status = "Friend access revoked"
                        settingsOpen = false
                    }) { Text("Revoke Friend access", color = Gold) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    providerName = chosen
                    val selected = AIProvider.fromStored(chosen)
                    if (selected == AIProvider.LOCAL) {
                        model = selected.defaultModel
                        store.putString("model_" + selected.name, model)
                    } else {
                        model = modelInput.trim().ifBlank { selected.defaultModel }
                        if (keyInput.isNotBlank()) {
                            val existing = apiProfiles.firstOrNull { it.id == selectedKeyProfileId && it.provider == selected }
                            val profile = if (existing != null) existing.copy(name = profileNameInput.trim().ifBlank { existing.name })
                                else ApiKeyProfile(UUID.randomUUID().toString(), profileNameInput.trim().ifBlank { "Profile" }, selected)
                            store.putString("profile_key_" + profile.id, keyInput.trim())
                            apiProfiles = (apiProfiles.filterNot { it.id == profile.id } + profile)
                            saveApiProfiles()
                            selectedKeyProfileId = profile.id
                            store.putString("active_profile_" + selected.name, profile.id)
                        }
                        store.putString("model_" + selected.name, model)
                    }
                    store.putString("provider", providerName)
                    store.putString("model", model)
                    store.putString("memory", memoryInput.take(8000))
                    settingsOpen = false
                    status = "Settings saved on this device"
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { settingsOpen = false }) { Text("Cancel") }
            }
        )
    }
    if (localModelOpen) {
        OfflineModelDialog(
            store = store,
            onDismiss = { localModelOpen = false },
            onUseModel = {
                providerName = AIProvider.LOCAL.name
                model = AIProvider.LOCAL.defaultModel
                store.putString("provider", providerName)
                store.putString("model_" + providerName, model)
                store.putString("model", model)
                status = "Offline model selected · messages stay on-device"
            },
            onStatus = { status = it }
        )
    }

    if (accountOpen) {
        CglAccountsDialog(
            onDismiss = { accountOpen = false },
            onStatus = { status = it },
            onAccountChanged = { accountLabel = if (it == "Sign in") "Account" else it }
        )
    }

    if (historyOpen) {
        AlertDialog(
            onDismissRequest = { historyOpen = false },
            title = { Text("Chat history") },
            text = {
                Column(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sessions.isEmpty()) {
                        Text("Your saved conversations will appear here.", color = Muted)
                    }
                    sessions.sortedByDescending { it.updatedAt }.forEach { item ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            TextButton(onClick = { openChat(item) }, modifier = Modifier.weight(1f)) {
                                Text(
                                    (if (item.id == activeChatId) "• " else "") + item.title,
                                    maxLines = 2,
                                    color = if (item.id == activeChatId) Gold else Paper
                                )
                            }
                            TextButton(onClick = { deleteChat(item) }) { Text("Delete", color = Muted, fontSize = 11.sp) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { historyOpen = false }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { startNewChat(); historyOpen = false }) { Text("New chat") } }
        )
    }

}

private data class ChatSession(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val providerName: String? = null,
    val model: String? = null
)

private data class ApiKeyProfile(val id: String, val name: String, val provider: AIProvider)

private fun loadApiProfiles(source: String): List<ApiKeyProfile> = try {
    val array = JSONArray(source)
    (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        val id = item.optString("id")
        val name = item.optString("name").take(48)
        val provider = AIProvider.fromStored(item.optString("provider"))
        if (id.isBlank() || name.isBlank()) null else ApiKeyProfile(id, name, provider)
    }.take(60)
} catch (_: Exception) { emptyList() }

private fun loadSessions(source: String): List<ChatSession> = try {
    val array = JSONArray(source)
    (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        val id = item.optString("id")
        if (id.isBlank()) null else ChatSession(
            id = id,
            title = item.optString("title", "Chat").ifBlank { "Chat" },
            updatedAt = item.optLong("updatedAt", 0L),
            providerName = item.optString("provider").ifBlank { null },
            model = item.optString("model").ifBlank { null }
        )
    }.take(100)
} catch (_: Exception) { emptyList() }

private fun loadMessages(source: String): List<ChatMessage> = try {
    val array = JSONArray(source)
    (0 until array.length()).mapNotNull { i ->
        val item = array.optJSONObject(i) ?: return@mapNotNull null
        val role = item.optString("role")
        val text = item.optString("text")
        if ((role == "user" || role == "assistant") && text.isNotBlank()) ChatMessage(role, text, item.optLong("timestamp")) else null
    }.takeLast(300)
} catch (_: Exception) { emptyList() }
