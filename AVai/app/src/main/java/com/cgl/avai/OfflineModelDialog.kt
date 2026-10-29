package com.cgl.avai

import android.app.ActivityManager
import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

private data class ImportedModel(val path: String, val name: String, val sizeBytes: Long, val sha256: String)

@Composable
fun OfflineModelDialog(
    store: SecureStore,
    onDismiss: () -> Unit,
    onUseModel: () -> Unit,
    onStatus: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var path by remember { mutableStateOf(store.getString("local_model_path")) }
    var name by remember { mutableStateOf(store.getString("local_model_name")) }
    var size by remember { mutableStateOf(store.getString("local_model_size").toLongOrNull() ?: 0L) }
    var sha by remember { mutableStateOf(store.getString("local_model_sha256")) }
    var status by remember { mutableStateOf(if (path.isBlank()) "No offline model imported yet." else "Offline model ready to load.") }
    var busy by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val memory = remember {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                status = "Copying model into AVai's private storage…"
                try {
                    val previousPath = path
                    val model = withContext(Dispatchers.IO) { importModel(context, uri) }
                    if (previousPath.isNotBlank() && previousPath != model.path) {
                        LocalModelEngine.unload()
                        withContext(Dispatchers.IO) {
                            File(previousPath).delete()
                            File(File(previousPath).parentFile, "litert-cache").deleteRecursively()
                        }
                    }
                    path = model.path
                    name = model.name
                    size = model.sizeBytes
                    sha = model.sha256
                    store.putString("local_model_path", model.path)
                    store.putString("local_model_name", model.name)
                    store.putString("local_model_size", model.sizeBytes.toString())
                    store.putString("local_model_sha256", model.sha256)
                    status = "Imported. A SHA-256 fingerprint was calculated locally. Tap Use offline model to select it."
                    onStatus("Offline model imported")
                } catch (error: Exception) {
                    status = error.message ?: "Model import failed."
                    onStatus(status)
                } finally {
                    busy = false
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Offline model (on-device)") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 560.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Import a .litertlm model file you have downloaded from a model publisher you trust. AVai runs it locally with LiteRT-LM on the CPU; the model is not sent to an AI provider.",
                    fontSize = 12.sp
                )
                val totalGb = memory.totalMem / (1024.0 * 1024.0 * 1024.0)
                val availableGb = memory.availMem / (1024.0 * 1024.0 * 1024.0)
                Text(
                    String.format(Locale.US, "Device RAM: %.1f GB total · %.1f GB currently available", totalGb, availableGb),
                    fontSize = 11.sp
                )
                Text(
                    "Runtime memory can be much larger than the model file. Check the model card for recommended RAM, license, and supported devices. Large models may run slowly or overheat a phone.",
                    fontSize = 11.sp
                )
                if (path.isBlank()) {
                    Text("No model selected", fontSize = 12.sp)
                } else {
                    Text(name.ifBlank { File(path).name }, fontSize = 13.sp)
                    Text("File size: " + formatBytes(size), fontSize = 11.sp)
                    if (size > memory.availMem) {
                        Text("Warning: this model file is larger than currently available RAM. Free memory first; runtime overhead may still prevent loading.", fontSize = 11.sp)
                    }
                    Text("SHA-256: " + sha, fontSize = 10.sp)
                    Text("Stored privately at: " + File(path).parentFile?.name + "/" + File(path).name, fontSize = 10.sp)
                }
                Text(status, fontSize = 11.sp)
                OutlinedButton(
                    onClick = {
                        try {
                            context.startActivity(android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                Uri.parse("https://huggingface.co/litert-community")
                            ))
                        } catch (_: Exception) {
                            status = "Could not open the model catalogue. Visit huggingface.co/litert-community in a browser."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Browse LiteRT model catalogue") }
                Button(
                    onClick = { picker.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (busy) "Importing…" else "Import .litertlm file") }
                if (path.isNotBlank()) {
                    OutlinedButton(
                        onClick = { onUseModel(); onDismiss() },
                        enabled = !busy && File(path).isFile,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Use offline model") }
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Delete imported model") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete local model?") },
            text = { Text("This removes the imported model file from AVai's app storage. You can import it again later.") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        busy = true
                        try {
                            LocalModelEngine.unload()
                            withContext(Dispatchers.IO) {
                                File(path).delete()
                                File(File(path).parentFile, "litert-cache").deleteRecursively()
                            }
                            listOf("local_model_path", "local_model_name", "local_model_size", "local_model_sha256")
                                .forEach(store::remove)
                            path = ""
                            name = ""
                            size = 0L
                            sha = ""
                            status = "Offline model deleted."
                            onStatus("Offline model deleted")
                        } catch (error: Exception) {
                            status = error.message ?: "Could not delete the loaded model."
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

private fun importModel(context: Context, uri: Uri): ImportedModel {
    val resolver = context.contentResolver
    var displayName = "offline-model.litertlm"
    var reportedSize = -1L
    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (nameIndex >= 0) displayName = cursor.getString(nameIndex) ?: displayName
            if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) reportedSize = cursor.getLong(sizeIndex)
        }
    }
    require(displayName.endsWith(".litertlm", ignoreCase = true)) {
        "Choose a LiteRT-LM .litertlm file. Other model formats are not compatible with this runtime."
    }
    require(reportedSize != 0L) { "The selected model file is empty." }
    require(reportedSize <= MAX_MODEL_BYTES || reportedSize < 0L) { "This prototype limits imported models to 16 GB." }

    val destinationDir = File(context.filesDir, "offline-models").apply { mkdirs() }
    val freeBytes = StatFs(destinationDir.absolutePath).availableBytes
    if (reportedSize > 0L) {
        require(reportedSize + STORAGE_RESERVE_BYTES < freeBytes) {
            "Not enough free internal storage. Free at least " + formatBytes(reportedSize + STORAGE_RESERVE_BYTES) + " and retry."
        }
    }
    val temporary = File(destinationDir, "model-importing.tmp")
    if (temporary.exists()) temporary.delete()
    val digest = MessageDigest.getInstance("SHA-256")
    var copied = 0L
    try {
        val input = resolver.openInputStream(uri) ?: throw IllegalStateException("Android could not open the selected model file.")
        input.use { source ->
            temporary.outputStream().buffered().use { output ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val count = source.read(buffer)
                    if (count < 0) break
                    copied += count
                    require(copied <= MAX_MODEL_BYTES) { "This prototype limits imported models to 16 GB." }
                    require(copied + STORAGE_RESERVE_BYTES < freeBytes) { "Internal storage ran out of space while importing the model." }
                    digest.update(buffer, 0, count)
                    output.write(buffer, 0, count)
                }
            }
        }
        require(copied > 0L) { "The selected model file is empty." }
        if (reportedSize > 0L) require(copied == reportedSize) { "The model import was incomplete. Please select the file again." }
        val hash = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val safeLabel = displayName.replace(Regex("[^A-Za-z0-9._-]"), "_").take(70)
        val destination = File(destinationDir, "${hash.take(16)}-$safeLabel")
        if (destination.exists()) temporary.delete()
        else require(temporary.renameTo(destination)) { "Could not move the imported model into AVai storage." }
        return ImportedModel(destination.absolutePath, displayName, copied, hash)
    } catch (error: Exception) {
        temporary.delete()
        throw error
    }
}

private fun formatBytes(value: Long): String {
    if (value < 1024L) return "$value B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var amount = value.toDouble()
    var index = -1
    while (amount >= 1024 && index < units.lastIndex) {
        amount /= 1024.0
        index++
    }
    return String.format(Locale.US, "%.2f %s", amount, units[index])
}

private const val MAX_MODEL_BYTES = 16L * 1024L * 1024L * 1024L
private const val STORAGE_RESERVE_BYTES = 200L * 1024L * 1024L
