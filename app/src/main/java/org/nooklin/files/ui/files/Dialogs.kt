package org.nooklin.files.ui.files

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import org.nooklin.files.ui.common.NooklinTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import org.nooklin.files.R
import org.nooklin.files.fs.Conflict
import org.nooklin.files.fs.OpError
import org.nooklin.files.fs.Resolution
import org.nooklin.files.ops.errorText
import org.nooklin.files.ui.NameProblem
import org.nooklin.files.ui.common.GlassPill
import java.io.File

enum class NameDialogKind { NEW_FOLDER, NEW_FILE, RENAME }

data class NameDialogState(val kind: NameDialogKind, val path: String? = null, val initial: String = "")

@Composable
fun NameDialog(
    state: NameDialogState,
    check: (String) -> NameProblem?,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val baseLength = remember(state) {
        val dot = state.initial.lastIndexOf('.')
        if (state.kind == NameDialogKind.RENAME && dot > 0 && !File(state.path.orEmpty()).isDirectory) dot else state.initial.length
    }
    var text by remember(state) { mutableStateOf(TextFieldValue(state.initial, TextRange(0, baseLength))) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(state) { focus.requestFocus() }
    val problem = check(text.text)
    val title = when (state.kind) {
        NameDialogKind.NEW_FOLDER -> R.string.dialog_new_folder
        NameDialogKind.NEW_FILE -> R.string.dialog_new_file
        NameDialogKind.RENAME -> R.string.dialog_rename
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        title = { Text(stringResource(title)) },
        text = {
            Column {
                NooklinTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    isError = problem != null && problem != NameProblem.EMPTY,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
                val message = when (problem) {
                    NameProblem.INVALID -> R.string.name_invalid
                    NameProblem.EXISTS -> R.string.name_exists
                    else -> null
                }
                if (message != null) {
                    Text(
                        stringResource(message),
                        color = GlassPill.destructiveText,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.text) }, enabled = problem == null) {
                Text(stringResource(if (state.kind == NameDialogKind.RENAME) R.string.dialog_rename_confirm else R.string.dialog_create))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** Asks for the password of a zip. With repeat it is a new password and has to be typed twice. */
@Composable
fun PasswordDialog(title: String, repeat: Boolean, confirmLabel: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val mismatch = repeat && again.isNotEmpty() && again != password
    val valid = password.isNotEmpty() && (!repeat || again == password)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                NooklinTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.password_label)) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focus)
                )
                if (repeat) {
                    NooklinTextField(
                        value = again,
                        onValueChange = { again = it },
                        singleLine = true,
                        isError = mismatch,
                        label = { Text(stringResource(R.string.password_repeat_label)) },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (mismatch) {
                        Text(stringResource(R.string.password_mismatch), color = GlassPill.destructiveText)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) }, enabled = valid) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
fun DeleteDialog(count: Int, trashAvailable: Boolean, onTrash: () -> Unit, onPermanent: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        title = { Text(stringResource(R.string.dialog_delete_title, itemsText(count))) },
        text = {
            if (trashAvailable) {
                Column {
                    Text(stringResource(R.string.dialog_delete_trash_explanation))
                    TextButton(onClick = onPermanent, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.action_delete_permanently), color = GlassPill.destructiveText)
                    }
                }
            } else {
                // Drives have no trash: nothing here can be restored
                Text(stringResource(R.string.dialog_delete_no_trash))
            }
        },
        confirmButton = {
            if (trashAvailable) {
                TextButton(onClick = onTrash) { Text(stringResource(R.string.action_move_to_trash)) }
            } else {
                TextButton(onClick = onPermanent) {
                    Text(stringResource(R.string.action_delete_permanently), color = GlassPill.destructiveText)
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel, color = GlassPill.destructiveText) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** A file or folder with this name is already in the target. Cannot be dismissed without an answer. */
@Composable
fun ConflictDialog(
    conflict: Conflict,
    onAnswer: (Resolution, Boolean) -> Unit,
    onCancelOperation: () -> Unit
) {
    var applyToAll by remember(conflict) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        title = { Text(stringResource(R.string.conflict_title)) },
        text = {
            Column {
                Text(stringResource(R.string.conflict_text, File(conflict.targetPath).name))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { applyToAll = !applyToAll }
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = applyToAll, onCheckedChange = { applyToAll = it })
                    Text(stringResource(R.string.conflict_apply_to_all))
                }
                Column(Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    TextButton(onClick = { onAnswer(Resolution.OVERWRITE, applyToAll) }) {
                        Text(stringResource(R.string.conflict_overwrite))
                    }
                    TextButton(onClick = { onAnswer(Resolution.RENAME, applyToAll) }) {
                        Text(stringResource(R.string.conflict_keep_both))
                    }
                    TextButton(onClick = { onAnswer(Resolution.SKIP, applyToAll) }) {
                        Text(stringResource(R.string.conflict_skip))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onCancelOperation) { Text(stringResource(R.string.conflict_cancel_operation)) } }
    )
}

@Composable
fun ErrorsDialog(errors: List<OpError>, onDismiss: () -> Unit) {
    val shown = errors.take(12)
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = GlassPill.shape,
        containerColor = GlassPill.adaptivePopupBackground,
        title = { Text(stringResource(R.string.errors_title)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                shown.forEach { Text(errorText(it), modifier = Modifier.padding(bottom = 6.dp)) }
                if (errors.size > shown.size) {
                    Text(stringResource(R.string.errors_more, errors.size - shown.size))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_ok)) } }
    )
}

@Composable
fun itemsText(count: Int): String =
    if (count == 1) stringResource(R.string.items_one) else stringResource(R.string.items_many, count)
