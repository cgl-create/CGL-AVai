package com.cgl.friend

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

enum class AIProvider(val label: String, val keyName: String, val defaultModel: String) {
    GEMINI("Google Gemini", "api_key_gemini", "gemini-2.5-flash"),
    OPENAI("OpenAI API", "api_key_openai", "gpt-4.1-mini"),
    CLAUDE("Anthropic Claude", "api_key_claude", "claude-sonnet-4-5"),
    LOCAL("AVai offline model", "", "local");

    companion object {
        fun fromStored(value: String): AIProvider =
            entries.firstOrNull { it.name == value } ?: GEMINI
    }
}

data class ChatMessage(
    val role: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

object AiClient {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 45_000
    private const val MAX_RESPONSE_CHARS = 1_000_000

    suspend fun reply(
        provider: AIProvider,
        apiKey: String,
        model: String,
        tone: String,
        memoryNotes: String,
        importedHistory: String,
        history: List<ChatMessage>
    ): String = withContext(Dispatchers.IO) {
        if (provider == AIProvider.LOCAL) {
            throw IllegalStateException("Offline inference is handled by AVai. Switch Friend to AI: AVai and import a .litertlm model in AVai.")
        }
        require(apiKey.isNotBlank()) { "Add an API key for " + provider.label + " in Settings." }
        val safeHistory = history.takeLast(24)
        val latestUserText = safeHistory.lastOrNull { it.role == "user" }?.text.orEmpty()
        val instructions = CglPlusAiCore.buildSystemInstructions(
            CglPlusAiCore.Assistant.FRIEND, tone, memoryNotes, importedHistory, latestUserText
        )
        when (provider) {
            AIProvider.GEMINI -> gemini(apiKey, model, instructions, safeHistory)
            AIProvider.OPENAI -> openAi(apiKey, model, instructions, safeHistory)
            AIProvider.CLAUDE -> claude(apiKey, model, instructions, safeHistory)
            AIProvider.LOCAL -> error("Local inference must be routed through AVai.")
        }
    }


    private fun gemini(
        key: String,
        model: String,
        instructions: String,
        history: List<ChatMessage>
    ): String {
        val cleanModel = model.trim().ifBlank { AIProvider.GEMINI.defaultModel }
        val contents = JSONArray()
        history.forEach { message ->
            val item = JSONObject()
            item.put("role", if (message.role == "assistant") "model" else "user")
            item.put("parts", JSONArray().put(JSONObject().put("text", message.text)))
            contents.put(item)
        }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", instructions))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", 0.7).put("maxOutputTokens", 900))
        val result = request(
            "https://generativelanguage.googleapis.com/v1beta/models/" +
                cleanModel +
                ":generateContent",
            "POST",
            mapOf("Content-Type" to "application/json", "x-goog-api-key" to key),
            body.toString()
        )
        val candidates = result.optJSONArray("candidates")
        val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            ?: throw IllegalStateException("Gemini returned no text. Check the model and API key.")
        return (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optString("text") }
            .filter { it.isNotBlank() }.joinToString("\n").ifBlank {
                throw IllegalStateException("Gemini returned an empty reply.")
            }
    }

    private fun openAi(
        key: String,
        model: String,
        instructions: String,
        history: List<ChatMessage>
    ): String {
        val input = JSONArray()
        history.forEach { message ->
            input.put(JSONObject()
                .put("role", if (message.role == "assistant") "assistant" else "user")
                .put("content", message.text))
        }
        val body = JSONObject()
            .put("model", model.trim().ifBlank { AIProvider.OPENAI.defaultModel })
            .put("instructions", instructions)
            .put("input", input)
            .put("max_output_tokens", 900)
        val result = request(
            "https://api.openai.com/v1/responses",
            "POST",
            mapOf("Content-Type" to "application/json", "Authorization" to "Bearer " + key),
            body.toString()
        )
        val direct = result.optString("output_text", "")
        if (direct.isNotBlank()) return direct.trim()
        val output = result.optJSONArray("output") ?: JSONArray()
        val found = mutableListOf<String>()
        for (i in 0 until output.length()) {
            val content = output.optJSONObject(i)?.optJSONArray("content") ?: continue
            for (j in 0 until content.length()) {
                val part = content.optJSONObject(j) ?: continue
                val text = part.optString("text", "")
                if (text.isNotBlank()) found.add(text)
            }
        }
        return found.joinToString("\n").trim().ifBlank {
            throw IllegalStateException("OpenAI returned no text. Check your model and API key.")
        }
    }

    private fun claude(
        key: String,
        model: String,
        instructions: String,
        history: List<ChatMessage>
    ): String {
        val input = JSONArray()
        history.forEach { message ->
            input.put(JSONObject()
                .put("role", if (message.role == "assistant") "assistant" else "user")
                .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", message.text))))
        }
        val body = JSONObject()
            .put("model", model.trim().ifBlank { AIProvider.CLAUDE.defaultModel })
            .put("max_tokens", 900)
            .put("system", instructions)
            .put("messages", input)
        val result = request(
            "https://api.anthropic.com/v1/messages",
            "POST",
            mapOf(
                "Content-Type" to "application/json",
                "x-api-key" to key,
                "anthropic-version" to "2023-06-01"
            ),
            body.toString()
        )
        val content = result.optJSONArray("content") ?: JSONArray()
        val found = mutableListOf<String>()
        for (i in 0 until content.length()) {
            val part = content.optJSONObject(i) ?: continue
            if (part.optString("type") == "text") {
                val text = part.optString("text", "")
                if (text.isNotBlank()) found.add(text)
            }
        }
        return found.joinToString("\n").trim().ifBlank {
            throw IllegalStateException("Claude returned no text. Check your model and API key.")
        }
    }

    private fun request(
        url: String,
        method: String,
        headers: Map<String, String>,
        body: String
    ): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            doOutput = true
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            connection.outputStream.use { stream ->
                stream.write(body.toByteArray(Charsets.UTF_8))
            }
            val status = connection.responseCode
            val stream: InputStream? = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.use { it.readBoundedText() }.orEmpty()
            if (responseText.length > MAX_RESPONSE_CHARS) {
                throw IllegalStateException("The AI provider response was unexpectedly large.")
            }
            val json = try { JSONObject(responseText) } catch (_: Exception) {
                JSONObject().put("raw_error", responseText.take(1200))
            }
            if (status !in 200..299) {
                val errorObj = json.optJSONObject("error")
                val detail = errorObj?.optString("message").orEmpty()
                    .ifBlank { json.optString("message") }
                    .ifBlank { "HTTP " + status }
                throw IllegalStateException(providerError(status, detail))
            }
            return json
        } finally {
            connection.disconnect()
        }
    }

    private fun InputStream.readBoundedText(): String {
        val reader = BufferedReader(InputStreamReader(this, Charsets.UTF_8))
        val output = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val count = reader.read(buffer)
            if (count < 0) break
            if (output.length + count > MAX_RESPONSE_CHARS) {
                throw IllegalStateException("The AI provider response was unexpectedly large.")
            }
            output.append(buffer, 0, count)
        }
        return output.toString()
    }

    private fun providerError(status: Int, detail: String): String {
        val friendly = when (status) {
            401, 403 -> "Authentication failed. Check that this provider's API key is valid and enabled."
            429 -> "The provider rate limit or usage quota was reached. Check account limits and billing."
            400 -> "The provider rejected the request. Check the model name and API access."
            else -> "The AI provider returned HTTP $status."
        }
        return friendly + " " + detail.take(500)
    }
}
