package com.cgl.friend

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Friend's client for AVai. No credential material is placed in the intent or request bundle. */
object AiBridgeClient {
    suspend fun reply(
        context: Context,
        prompt: String,
        tone: String,
        memory: String,
        importedHistory: String,
        history: List<ChatMessage>
    ): String = withTimeout(70_000L) {
        suspendCancellableCoroutine { continuation ->
            val appContext = context.applicationContext
            val mainHandler = Handler(Looper.getMainLooper())
            val completed = AtomicBoolean(false)
            var bound = false
            var connectedMessenger: Messenger? = null
            lateinit var connection: ServiceConnection

            fun finish(result: String? = null, error: String? = null) {
                if (!completed.compareAndSet(false, true)) return
                if (bound) {
                    try { appContext.unbindService(connection) } catch (_: Exception) { }
                    bound = false
                }
                if (!continuation.isActive) return
                if (!error.isNullOrBlank()) continuation.resumeWithException(IllegalStateException(error))
                else if (result.isNullOrBlank()) continuation.resumeWithException(IllegalStateException("AVai returned an empty response."))
                else continuation.resume(result)
            }

            val responseMessenger = Messenger(Handler(Looper.getMainLooper()) { message ->
                if (message.what == AiBridgeProtocol.MESSAGE_RESULT) {
                    val data = message.data ?: Bundle()
                    finish(data.getString(AiBridgeProtocol.KEY_RESULT), data.getString(AiBridgeProtocol.KEY_ERROR))
                }
                true
            })

            connection = object : ServiceConnection {
                override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                    if (binder == null) {
                        finish(error = "AVai's AI service did not start.")
                        return
                    }
                    connectedMessenger = Messenger(binder)
                    val json = JSONArray()
                    history.takeLast(24).forEach { item ->
                        json.put(JSONObject().put("role", item.role).put("text", item.text.take(12000)))
                    }
                    val request = Message.obtain(null, AiBridgeProtocol.MESSAGE_INFER)
                    request.replyTo = responseMessenger
                    request.data = Bundle().apply {
                        putInt(AiBridgeProtocol.KEY_PROTOCOL_VERSION, 1)
                        putString(AiBridgeProtocol.KEY_PROMPT, prompt.take(12000))
                        putString(AiBridgeProtocol.KEY_TONE, tone.take(80))
                        putString(AiBridgeProtocol.KEY_MEMORY, memory.take(8000))
                        putString(AiBridgeProtocol.KEY_IMPORTED_HISTORY, importedHistory.takeLast(10000))
                        putString(AiBridgeProtocol.KEY_HISTORY_JSON, json.toString())
                    }
                    try {
                        connectedMessenger?.send(request)
                    } catch (error: RemoteException) {
                        finish(error = "Could not send the request to AVai. Update both apps and try again.")
                    }
                }

                override fun onServiceDisconnected(name: ComponentName?) {
                    connectedMessenger = null
                    finish(error = "AVai disconnected before replying.")
                }

                override fun onBindingDied(name: ComponentName?) {
                    connectedMessenger = null
                    finish(error = "AVai's service binding expired. Reopen AVai and retry.")
                }

                override fun onNullBinding(name: ComponentName?) {
                    finish(error = "AVai denied the service connection. Check its permission and your consent.")
                }
            }

            val intent = Intent().setComponent(ComponentName("com.cgl.avai", "com.cgl.avai.AiBridgeService"))
            try {
                bound = appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                if (!bound) finish(error = "AVai is not installed or cannot be reached. Install AVai and open Connect to AVai.")
            } catch (error: Exception) {
                finish(error = error.message ?: "Could not connect to AVai. Install or update AVai.")
            }

            continuation.invokeOnCancellation {
                mainHandler.post {
                    if (bound && completed.compareAndSet(false, true)) {
                        try { appContext.unbindService(connection) } catch (_: Exception) { }
                        bound = false
                    }
                }
            }
        }
    }
}
