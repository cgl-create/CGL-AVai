package com.cgl.friend

import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Best-effort text extraction for ChatGPT conversations.json exports and plain-text transcripts.
 * It deliberately imports only text; images, audio, files, and structured tool data are not imported.
 */
object MemoryImporter {
    const val MAX_FILE_BYTES = 8 * 1024 * 1024
    const val MAX_IMPORTED_CHARS = 24_000

    fun readBounded(input: InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        input.use { stream ->
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                total += count
                require(total <= MAX_FILE_BYTES) {
                    "That file is larger than 8 MB. Extract or split the text export, then import a smaller file."
                }
                output.write(buffer, 0, count)
            }
        }
        return output.toString(Charsets.UTF_8.name())
    }

    fun extract(source: String): String {
        val trimmed = source.trim()
        if (trimmed.isEmpty()) return ""
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return trimmed.take(MAX_IMPORTED_CHARS)
        }

        val extracted = mutableListOf<String>()
        val root: Any = try {
            if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONObject(trimmed)
        } catch (_: Exception) {
            return trimmed.take(MAX_IMPORTED_CHARS)
        }

        walk(root, extracted, 0)
        if (extracted.isEmpty()) {
            return "Imported JSON did not contain recognizable text messages. You can paste selected notes into Friend's Memory screen instead."
        }

        val output = StringBuilder()
        extracted.distinct().takeLast(180).forEach { line ->
            if (output.length >= MAX_IMPORTED_CHARS) return@forEach
            val remaining = MAX_IMPORTED_CHARS - output.length
            output.append(line.take(remaining)).append('\n')
        }
        return output.toString().trim()
    }

    private fun walk(value: Any?, output: MutableList<String>, depth: Int) {
        if (depth > 45 || output.size >= 500) return
        when (value) {
            is JSONArray -> {
                for (i in 0 until value.length()) walk(value.opt(i), output, depth + 1)
            }
            is JSONObject -> {
                val author = value.optJSONObject("author")
                val content = value.optJSONObject("content")
                if (author != null && content != null) {
                    val role = author.optString("role", "")
                    if (role == "user" || role == "assistant") {
                        val text = contentText(content).trim()
                        if (text.isNotEmpty()) {
                            output.add((if (role == "user") "You: " else "Assistant: ") + text.take(1400))
                        }
                    }
                }
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key == "author" || key == "content") continue
                    walk(value.opt(key), output, depth + 1)
                }
            }
        }
    }

    private fun contentText(content: JSONObject): String {
        val parts = content.optJSONArray("parts")
        if (parts != null) {
            val values = mutableListOf<String>()
            for (i in 0 until parts.length()) {
                when (val part = parts.opt(i)) {
                    is String -> values.add(part)
                    is JSONObject -> {
                        val text = part.optString("text", "")
                        if (text.isNotBlank()) values.add(text)
                    }
                }
            }
            return values.joinToString("\n")
        }
        return content.optString("text", "")
    }
}
