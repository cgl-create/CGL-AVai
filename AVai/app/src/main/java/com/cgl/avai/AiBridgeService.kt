package com.cgl.avai

import android.app.Service
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Narrow inference bridge. API keys never cross Binder: the request carries only text/context and
 * AVai performs the provider request internally. The manifest signature permission protects bind;
 * this service additionally verifies caller UID/package/signing certificate and explicit user consent.
 */
class AiBridgeService : Service() {
    private lateinit var store: SecureStore
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val messenger by lazy { Messenger(IncomingHandler()) }

    override fun onCreate() {
        super.onCreate()
        store = SecureStore(applicationContext)
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            if (message.what != AiBridgeProtocol.MESSAGE_INFER) return
            val callback = message.replyTo ?: return
            val callerUid = message.sendingUid
            val input = message.data ?: Bundle()
            if (!isTrustedFriend(callerUid)) {
                reply(callback, error = "The calling app could not be verified. Reinstall AVai and Friend from the same signed build.")
                return
            }
            if (input.getInt(AiBridgeProtocol.KEY_PROTOCOL_VERSION, 0) != 1) {
                reply(callback, error = "Friend and AVai use incompatible bridge versions. Update both apps together.")
                return
            }
            val prompt = input.getString(AiBridgeProtocol.KEY_PROMPT).orEmpty().take(12000)
            if (prompt.isBlank()) {
                reply(callback, error = "The message was empty.")
                return
            }
            val grantAllowed = consumeOneRequestOrCheckPersistentGrant()
            if (!grantAllowed) {
                reply(callback, error = "Friend is not authorised yet. Open Friend → Connect to AVai and approve access.")
                return
            }
            val friendChatId = input.getString(AiBridgeProtocol.KEY_CHAT_ID).orEmpty().take(64)
            val history = parseHistory(input.getString(AiBridgeProtocol.KEY_HISTORY_JSON).orEmpty()).takeLast(24)
            val tone = input.getString(AiBridgeProtocol.KEY_TONE).orEmpty().take(80).ifBlank { "clear, calm and helpful" }
            val friendMemory = input.getString(AiBridgeProtocol.KEY_MEMORY).orEmpty().take(8000)
            val imported = input.getString(AiBridgeProtocol.KEY_IMPORTED_HISTORY).orEmpty().takeLast(10000)

            scope.launch {
                try {
                    val provider = AIProvider.fromStored(store.getString("provider", AIProvider.GEMINI.name))
                    val activeProfileId = store.getString("active_profile_" + provider.name)
                    val key = if (provider == AIProvider.LOCAL) "" else
                        (if (activeProfileId.isNotBlank()) store.getString("profile_key_" + activeProfileId) else "")
                            .ifBlank { store.getString("key_" + provider.name) }
                            .ifBlank { store.getString(provider.keyName) }
                    if (provider != AIProvider.LOCAL && key.isBlank()) {
                        throw IllegalStateException("Open AVai → Settings and add an API key for the selected provider.")
                    }
                    if (provider == AIProvider.LOCAL && store.getString("local_model_path").isBlank()) {
                        throw IllegalStateException("Choose a .litertlm offline model in AVai → Settings before asking Friend.")
                    }
                    val model = store.getString("model_" + provider.name)
                        .ifBlank { store.getString("model", provider.defaultModel) }
                        .ifBlank { provider.defaultModel }
                    val memory = listOf(store.getString("memory"), friendMemory)
                        .filter { it.isNotBlank() }
                        .joinToString("\n\n")
                        .take(8000)
                    val answer = AiClient.reply(
                        provider, key, model, tone, memory, imported, history,
                        localModelPath = store.getString("local_model_path")
                    )
                    persistFriendConversation(friendChatId, history, answer, provider, model)
                    reply(callback, result = answer)
                } catch (error: Exception) {
                    reply(callback, error = error.message ?: "AVai could not complete the request.")
                }
            }
        }
    }

    /** "Allow once" is consumed by the first valid inference request. Persistent access is revocable. */
    @Synchronized
    private fun consumeOneRequestOrCheckPersistentGrant(): Boolean {
        if (store.getString("friend_access_always", "false") == "true") return true
        val until = store.getString("friend_access_once_until", "0").toLongOrNull() ?: 0L
        if (until > System.currentTimeMillis()) {
            store.putString("friend_access_once_until", "0")
            return true
        }
        return false
    }

    private fun isTrustedFriend(uid: Int): Boolean {
        if (uid <= 0) return false
        val packages = packageManager.getPackagesForUid(uid)?.toSet() ?: return false
        if (!packages.contains(FRIEND_PACKAGE)) return false
        return try {
            val ownSignatures = signerSet(signaturePackageInfo(packageName))
            val friendSignatures = signerSet(signaturePackageInfo(FRIEND_PACKAGE))
            ownSignatures.isNotEmpty() && ownSignatures.intersect(friendSignatures).isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    @Suppress("DEPRECATION")
    private fun signaturePackageInfo(name: String): PackageInfo {
        val flag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return packageManager.getPackageInfo(name, flag)
    }

    @Suppress("DEPRECATION")
    private fun signerSet(info: PackageInfo): Set<String> {
        val signingInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.signingInfo else null
        val signatures = if (signingInfo != null) signingInfo.apkContentsSigners else info.signatures
        return signatures?.map { it.toCharsString() }?.toSet().orEmpty()
    }

    private fun parseHistory(json: String): List<ChatMessage> = try {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val role = item.optString("role")
            val text = item.optString("text").take(12000)
            val timestamp = item.optLong("timestamp", System.currentTimeMillis())
            if ((role == "user" || role == "assistant") && text.isNotBlank()) ChatMessage(role, text, timestamp) else null
        }.takeLast(150)
    } catch (_: Exception) {
        emptyList()
    }

    /** Save a mirrored Friend conversation inside AVai's encrypted on-device chat store. */
    private fun persistFriendConversation(
        friendChatId: String,
        incoming: List<ChatMessage>,
        answer: String,
        provider: AIProvider,
        model: String
    ) {
        if (!friendChatId.matches(Regex("^[A-Za-z0-9_-]{1,64}$"))) return
        val sessionId = "friend_" + friendChatId
        val previous = parseHistory(store.getString("chat_" + sessionId, "[]"))
        val merged = (previous + incoming)
            .distinctBy { "${it.timestamp}|${it.role}|${it.text}" }
            .sortedBy { it.timestamp }
            .takeLast(149)
            .toMutableList()
        merged.add(ChatMessage("assistant", answer))
        val conversation = JSONArray()
        merged.takeLast(150).forEach { item ->
            conversation.put(JSONObject()
                .put("role", item.role)
                .put("text", item.text)
                .put("timestamp", item.timestamp))
        }
        store.putString("chat_" + sessionId, conversation.toString())

        val now = System.currentTimeMillis()
        val firstUser = merged.firstOrNull { it.role == "user" }?.text
            ?.replace("\n", " ")?.take(34)?.ifBlank { "Conversation" } ?: "Conversation"
        val updatedSessions = JSONArray()
        val existingSessions = JSONArray(store.getString("chat_sessions", "[]"))
        for (index in 0 until existingSessions.length()) {
            val item = existingSessions.optJSONObject(index) ?: continue
            if (item.optString("id") != sessionId) updatedSessions.put(item)
        }
        updatedSessions.put(JSONObject()
            .put("id", sessionId)
            .put("title", "Friend · " + firstUser)
            .put("updatedAt", now)
            .put("provider", provider.name)
            .put("model", model))
        while (updatedSessions.length() > 100) {
            // Oldest IDs are not guaranteed to be first in older JSON; UI sorts by updatedAt.
            updatedSessions.remove(0)
        }
        store.putString("chat_sessions", updatedSessions.toString())
    }

    private fun reply(target: Messenger, result: String? = null, error: String? = null) {
        val response = Message.obtain(null, AiBridgeProtocol.MESSAGE_RESULT)
        response.data = Bundle().apply {
            putString(AiBridgeProtocol.KEY_RESULT, result.orEmpty())
            putString(AiBridgeProtocol.KEY_ERROR, error.orEmpty())
        }
        try {
            target.send(response)
        } catch (_: RemoteException) {
            // Friend may have closed while the model was responding.
        }
    }

    private companion object {
        const val FRIEND_PACKAGE = "com.cgl.friend"
    }
}
