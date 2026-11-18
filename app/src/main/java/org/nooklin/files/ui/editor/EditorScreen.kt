package org.nooklin.files.ui.editor

import org.nooklin.files.ui.theme.AppText
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.nooklin.files.R
import org.nooklin.files.editor.Highlighter
import org.nooklin.files.editor.Language
import org.nooklin.files.editor.TextFiles
import org.nooklin.files.editor.TokenType
import org.nooklin.files.editor.UndoStack
import org.nooklin.files.ui.common.GlassPill
import org.nooklin.files.ui.common.openFile
import java.io.File
import java.io.IOException

private class HighlightTransformation(
    private val language: Language,
    private val colors: Map<TokenType, SpanStyle>
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val spans = Highlighter.highlight(text.text, language)
        val styled = buildAnnotatedString {
            append(text.text)
            for (span in spans) {
                val style = colors[span.type] ?: continue
                addStyle(style, span.start, span.end)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/** Edits one text file: line numbers, colors for the common languages, save and a warning for unsaved changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(path: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val file = remember(path) { File(path) }
    val language = remember(path) { Language.forExtension(file.extension) }
    var loaded by remember(path) { mutableStateOf(false) }
    var original by remember(path) { mutableStateOf("") }
    var value by remember(path) { mutableStateOf(TextFieldValue("")) }
    var confirmClose by remember(path) { mutableStateOf(false) }
    val history = remember(path) { UndoStack() }
    // Changes whenever the history changes, so the buttons follow it
    var historyVersion by remember(path) { mutableStateOf(0) }

    LaunchedEffect(path) {
        val text = withContext(Dispatchers.IO) {
            try {
                TextFiles.read(file)
            } catch (e: IOException) {
                null
            }
        }
        if (text == null) {
            // Not UTF-8 text: saving it here would damage it, so another app gets it
            Toast.makeText(context, R.string.msg_not_editable, Toast.LENGTH_SHORT).show()
            openFile(context, path)
            onClose()
        } else {
            original = text
            value = TextFieldValue(text)
            loaded = true
        }
    }

    val dirty = loaded && value.text != original

    fun save(then: () -> Unit = {}) {
        val text = value.text
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { TextFiles.write(file, text) }.isSuccess }
            if (ok) {
                original = text
                Toast.makeText(context, R.string.msg_saved, Toast.LENGTH_SHORT).show()
                then()
            } else {
                Toast.makeText(context, R.string.msg_save_failed, Toast.LENGTH_LONG).show()
            }
        }
    }

    BackHandler { if (dirty) confirmClose = true else onClose() }

    val keyword = MaterialTheme.colorScheme.primary
    val string = MaterialTheme.colorScheme.tertiary
    val number = MaterialTheme.colorScheme.secondary
    val comment = MaterialTheme.colorScheme.onSurfaceVariant
    val transformation = remember(language, keyword, string, number, comment) {
        if (language == null) {
            VisualTransformation.None
        } else {
            HighlightTransformation(
                language,
                mapOf(
                    TokenType.KEYWORD to SpanStyle(color = keyword, fontWeight = FontWeight.Bold),
                    TokenType.TAG to SpanStyle(color = keyword),
                    TokenType.STRING to SpanStyle(color = string),
                    TokenType.NUMBER to SpanStyle(color = number),
                    TokenType.COMMENT to SpanStyle(color = comment, fontStyle = FontStyle.Italic)
                )
            )
        }
    }
    val textStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = AppText.Body,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurface,
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
    )

    Scaffold(
        // The space for the status bar is made here, around the whole editor, so the text can never run behind it
        modifier = Modifier.statusBarsPadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                windowInsets = WindowInsets(0, 0, 0, 0),
                title = { Text(file.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = { if (dirty) confirmClose = true else onClose() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    // Read so the buttons are updated after every change
                    historyVersion.let { }
                    IconButton(
                        onClick = {
                            history.undo(value.text)?.let { value = TextFieldValue(it.text, TextRange(it.cursor)) }
                            historyVersion++
                        },
                        enabled = history.canUndo
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = stringResource(R.string.editor_undo))
                    }
                    IconButton(
                        onClick = {
                            history.redo(value.text)?.let { value = TextFieldValue(it.text, TextRange(it.cursor)) }
                            historyVersion++
                        },
                        enabled = history.canRedo
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = stringResource(R.string.editor_redo))
                    }
                    IconButton(onClick = { save() }, enabled = dirty) {
                        Icon(Icons.Rounded.Save, contentDescription = stringResource(R.string.editor_save))
                    }
                }
            )
        }
    ) { padding ->
        if (!loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val lineCount = remember(value.text) { value.text.count { it == '\n' } + 1 }
            val gutter = remember(lineCount) { (1..lineCount).joinToString("\n") }
            val configuration = LocalConfiguration.current
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .clipToBounds()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    gutter,
                    style = textStyle.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                    textAlign = TextAlign.End,
                    modifier = Modifier.padding(start = 8.dp, end = 8.dp, top = 8.dp)
                )
                // Inside a horizontal scroll the field has unlimited width, so lines only break where the text has a newline
                Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
                    BasicTextField(
                        value = value,
                        onValueChange = { changed ->
                            if (changed.text != value.text) {
                                history.record(value.text, changed.text, SystemClock.elapsedRealtime())
                                historyVersion++
                            }
                            value = changed
                        },
                        textStyle = textStyle,
                        visualTransformation = transformation,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false),
                        modifier = Modifier
                            .defaultMinSize(minWidth = (configuration.screenWidthDp - 56).dp, minHeight = configuration.screenHeightDp.dp)
                            .padding(top = 8.dp, end = 16.dp)
                    )
                }
            }
        }
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            shape = GlassPill.shape,
            containerColor = GlassPill.adaptivePopupBackground,
            title = { Text(stringResource(R.string.editor_unsaved_title)) },
            text = { Text(stringResource(R.string.editor_unsaved_text, file.name)) },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        confirmClose = false
                        onClose()
                    }) { Text(stringResource(R.string.editor_discard)) }
                    TextButton(onClick = {
                        confirmClose = false
                        save { onClose() }
                    }) { Text(stringResource(R.string.editor_save)) }
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }
}
