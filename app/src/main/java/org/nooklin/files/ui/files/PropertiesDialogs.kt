package org.nooklin.files.ui.files

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import org.nooklin.files.ui.common.NooklinTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.files.R
import org.nooklin.files.fs.FileInfo
import org.nooklin.files.fs.FolderTotals
import org.nooklin.files.ui.common.GlassPill
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Details of one file or folder, with the folder totals and the checksums on request. */
@Composable
fun PropertiesDialog(path: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val file = remember(path) { File(path) }
    val dateFormat = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
    var totals by remember(path) { mutableStateOf<FolderTotals?>(null) }
    var sha by remember(path) { mutableStateOf<String?>(null) }
    var md5 by remember(path) { mutableStateOf<String?>(null) }
    var hashing by remember(path) { mutableStateOf<String?>(null) }
    var hashFailed by remember(path) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var hashJob by remember(path) { mutableStateOf<Job?>(null) }

    LaunchedEffect(path) {
        if (file.isDirectory) totals = withContext(Dispatchers.IO) { FileInfo.folderTotals(file) }
    }

    fun startHash(algorithm: String) {
        hashFailed = false
        hashing = algorithm
        hashJob = scope.launch {
            try {
                val value = withContext(Dispatchers.IO) { FileInfo.checksum(file, algorithm) }
                if (algorithm == "MD5") md5 = value else sha = value
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                hashFailed = true
            } finally {
                hashing = null
            }
        }
    }

    AlertDialog(
        onDismissRequest = { hashJob?.cancel(); onDismiss() },
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        title = { Text(stringResource(R.string.properties_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SelectionContainer {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        PropertyRow(stringResource(R.string.properties_name), file.name)
                        PropertyRow(stringResource(R.string.properties_path), file.parent.orEmpty())
                        PropertyRow(
                            stringResource(R.string.properties_type),
                            if (file.isDirectory) stringResource(R.string.properties_type_folder)
                            else file.extension.uppercase().ifEmpty { stringResource(R.string.properties_type_file) }
                        )
                        if (file.isDirectory) {
                            val t = totals
                            PropertyRow(
                                stringResource(R.string.properties_size),
                                if (t == null) stringResource(R.string.properties_counting) else formatSize(context, t.bytes)
                            )
                            if (t != null) {
                                PropertyRow(
                                    stringResource(R.string.properties_contains),
                                    stringResource(R.string.properties_contains_value, t.files.toInt(), t.folders.toInt())
                                )
                            }
                        } else {
                            PropertyRow(
                                stringResource(R.string.properties_size),
                                formatSize(context, file.length()) + " (" + file.length() + " B)"
                            )
                        }
                        PropertyRow(stringResource(R.string.properties_modified), dateFormat.format(Date(file.lastModified())))
                        PropertyRow(
                            stringResource(R.string.properties_access),
                            stringResource(
                                when {
                                    file.canRead() && file.canWrite() -> R.string.properties_access_rw
                                    file.canRead() -> R.string.properties_access_r
                                    else -> R.string.properties_access_none
                                }
                            )
                        )
                        sha?.let { PropertyRow("SHA-256", it) }
                        md5?.let { PropertyRow("MD5", it) }
                    }
                }
                if (hashFailed) Text(stringResource(R.string.properties_hash_failed), color = GlassPill.destructiveText)
                if (file.isFile) {
                    if (hashing != null) {
                        Text(stringResource(R.string.properties_hash_busy, hashing.orEmpty()))
                        TextButton(onClick = { hashJob?.cancel() }) { Text(stringResource(R.string.common_cancel)) }
                    } else {
                        if (sha == null) TextButton(onClick = { startHash("SHA-256") }) { Text(stringResource(R.string.properties_hash_sha)) }
                        if (md5 == null) TextButton(onClick = { startHash("MD5") }) { Text(stringResource(R.string.properties_hash_md5)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { hashJob?.cancel(); onDismiss() }) { Text(stringResource(R.string.common_ok)) } }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** One new name for all selected items: "name 1.ext", "name 2.ext" and so on. */
fun batchNames(items: List<File>, base: String): List<String> {
    val digits = items.size.toString().length
    return items.mapIndexed { index, file ->
        val number = (index + 1).toString().padStart(digits, '0')
        val ext = if (file.isDirectory) "" else file.extension.let { if (it.isEmpty()) "" else ".$it" }
        "${base.trim()} $number$ext"
    }
}

@Composable
fun BatchRenameDialog(items: List<File>, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var base by remember { mutableStateOf("") }
    val names = remember(base, items) { batchNames(items, base) }
    val selected = remember(items) { items.map { it.path }.toSet() }
    val invalid = base.isBlank() || base.contains('/') || base.contains('\u0000')
    val clash = !invalid && items.zip(names).any { (item, name) ->
        val target = File(item.parentFile, name)
        target.exists() && target.path !in selected
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        title = { Text(stringResource(R.string.batch_rename_title, items.size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NooklinTextField(
                    value = base,
                    onValueChange = { base = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.batch_rename_label)) },
                    isError = clash,
                    modifier = Modifier.fillMaxWidth()
                )
                if (!invalid) Text(stringResource(R.string.batch_rename_example, names.first()), style = MaterialTheme.typography.bodySmall)
                if (clash) Text(stringResource(R.string.name_exists), color = GlassPill.destructiveText, modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(base) }, enabled = !invalid && !clash) { Text(stringResource(R.string.dialog_rename_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}
