package com.cgl.avai

import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * AVai's on-device text inference runtime.
 *
 * The engine lives on one dedicated worker thread. Conversations are short-lived and seeded with
 * the selected chat's history so switching chats cannot leak context between conversations.
 * Model files are imported by the user into app-private storage; they are not bundled in the APK.
 */
object LocalModelEngine {
    private val worker = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "AVai-LiteRT-LM").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val lock = Mutex()
    private var loadedPath: String? = null
    private var engine: Engine? = null

    suspend fun reply(
        modelPath: String,
        systemInstruction: String,
        history: List<ChatMessage>
    ): String = withContext(worker) {
        lock.withLock {
            val file = File(modelPath)
            require(file.isFile && file.length() > 0L && file.extension.equals("litertlm", ignoreCase = true)) {
                "Choose a valid .litertlm model file in AVai → Settings → Offline model."
            }
            ensureLoaded(file.canonicalPath)

            val lastUserIndex = history.indexOfLast { it.role == "user" }
            require(lastUserIndex >= 0) { "Send a message before choosing an offline model." }
            val prompt = history[lastUserIndex].text
            val prior = history.take(lastUserIndex).takeLast(20).mapNotNull { message ->
                when (message.role) {
                    "user" -> Message.user(message.text.take(12000))
                    "assistant" -> Message.model(message.text.take(12000))
                    else -> null
                }
            }
            val config = ConversationConfig(
                systemInstruction = Contents.of(systemInstruction.take(14000)),
                initialMessages = prior
            )
            val activeEngine = checkNotNull(engine)
            activeEngine.createConversation(config).use { conversation ->
                conversation.sendMessage(prompt.take(12000)).toString().trim().ifBlank {
                    throw IllegalStateException("The offline model returned an empty response.")
                }
            }
        }
    }

    private fun ensureLoaded(path: String) {
        if (loadedPath == path && engine != null) return
        releaseEngine()
        val cacheDirectory = File(File(path).parentFile, "litert-cache").apply { mkdirs() }
        val candidate = Engine(EngineConfig(modelPath = path, cacheDir = cacheDirectory.absolutePath))
        // Do not close the engine if initialization throws; LiteRT-LM explicitly forbids that.
        candidate.initialize()
        engine = candidate
        loadedPath = path
    }

    private fun releaseEngine() {
        val loaded = engine
        if (loaded != null) {
            loaded.close()
            engine = null
            loadedPath = null
        }
    }

    suspend fun unload() = withContext(worker) {
        lock.withLock { releaseEngine() }
    }
}
