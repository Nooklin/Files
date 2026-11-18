package org.nooklin.files.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.TextDecrease
import androidx.compose.material.icons.rounded.TextIncrease
import androidx.compose.material3.CircularProgressIndicator
import org.nooklin.files.ui.common.NooklinMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.nooklin.files.R
import org.nooklin.files.fs.DocChapter
import org.nooklin.files.fs.DocReaders
import org.nooklin.files.fs.MediaKind
import org.nooklin.files.fs.MediaKinds
import org.nooklin.files.ui.common.openFile
import java.io.File

private sealed interface DocState {
    data object Loading : DocState
    data object Failed : DocState
    class Ready(val chapters: List<DocChapter>) : DocState
}

/** The text of a docx or epub. Pictures, styles and layout are left out. */
@Composable
fun DocViewer(path: String, onClose: () -> Unit) {
    val context = LocalContext.current
    BackHandler { onClose() }
    val state by produceState<DocState>(DocState.Loading, path) {
        value = withContext(Dispatchers.IO) {
            try {
                val file = File(path)
                val chapters = if (MediaKinds.of(file) == MediaKind.EPUB) {
                    DocReaders.epubChapters(file)
                } else {
                    listOf(DocChapter(file.nameWithoutExtension, DocReaders.docxText(file)))
                }.filter { it.text.isNotBlank() }
                if (chapters.isEmpty()) DocState.Failed else DocState.Ready(chapters)
            } catch (e: Exception) {
                DocState.Failed
            } catch (e: OutOfMemoryError) {
                DocState.Failed
            }
        }
    }
    var textSize by remember { mutableIntStateOf(17) }
    var chapter by remember { mutableIntStateOf(0) }
    var chaptersMenu by remember { mutableStateOf(false) }
    val onBg = MaterialTheme.colorScheme.onBackground
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        ViewerBar(
            title = File(path).name,
            onBack = onClose,
            tint = onBg,
            actions = {
                IconButton(onClick = { textSize = (textSize - 2).coerceAtLeast(11) }) {
                    Icon(Icons.Rounded.TextDecrease, contentDescription = stringResource(R.string.viewer_text_smaller), tint = onBg)
                }
                IconButton(onClick = { textSize = (textSize + 2).coerceAtMost(32) }) {
                    Icon(Icons.Rounded.TextIncrease, contentDescription = stringResource(R.string.viewer_text_bigger), tint = onBg)
                }
                IconButton(onClick = { openFile(context, path) }) {
                    Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = stringResource(R.string.viewer_open_other), tint = onBg)
                }
            }
        )
        when (val s = state) {
            DocState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            DocState.Failed -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.viewer_doc_error), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is DocState.Ready -> {
                val current = s.chapters[chapter.coerceIn(0, s.chapters.lastIndex)]
                val lines = remember(current) { current.text.split("\n") }
                val list = rememberLazyListState()
                LaunchedEffect(chapter) { list.scrollToItem(0) }
                SelectionContainer(Modifier.weight(1f)) {
                    LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
                        items(lines) { line ->
                            Text(
                                line.ifEmpty { " " },
                                fontSize = textSize.sp,
                                lineHeight = (textSize * 1.5f).sp,
                                color = onBg,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
                if (s.chapters.size > 1) {
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { chapter = (chapter - 1).coerceAtLeast(0) }, enabled = chapter > 0) {
                            Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = stringResource(R.string.viewer_previous_chapter))
                        }
                        Box {
                            IconButton(onClick = { chaptersMenu = true }) {
                                Icon(Icons.AutoMirrored.Rounded.List, contentDescription = stringResource(R.string.viewer_chapters))
                            }
                            NooklinMenu(chaptersMenu, onDismiss = { chaptersMenu = false }) {
                                s.chapters.forEachIndexed { i, c ->
                                    DropdownMenuItem(
                                        text = { Text("${i + 1}. ${c.title}", maxLines = 1) },
                                        onClick = {
                                            chapter = i
                                            chaptersMenu = false
                                        }
                                    )
                                }
                            }
                        }
                        Text("${chapter + 1} / ${s.chapters.size}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        IconButton(onClick = { chapter = (chapter + 1).coerceAtMost(s.chapters.lastIndex) }, enabled = chapter < s.chapters.lastIndex) {
                            Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = stringResource(R.string.viewer_next_chapter))
                        }
                    }
                } else {
                    Box(Modifier.navigationBarsPadding())
                }
            }
        }
    }
}
