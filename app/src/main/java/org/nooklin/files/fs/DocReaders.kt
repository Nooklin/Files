package org.nooklin.files.fs

import java.io.File
import java.util.zip.ZipFile

/** One chapter of an epub or the whole text of a docx. */
data class DocChapter(val title: String, val text: String)

/** Plain text out of docx and epub files. Layout, images and styles are not kept, only the text. */
object DocReaders {
    /** Largest part of a document that is read into memory. */
    private const val MAX_PART_BYTES = 20L * 1024 * 1024

    private val entities = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "ndash" to "–", "mdash" to "—", "hellip" to "…", "lsquo" to "‘", "rsquo" to "’", "ldquo" to "“", "rdquo" to "”")

    fun decodeEntities(text: String): String =
        Regex("&(#x?[0-9a-fA-F]+|[a-zA-Z]+);").replace(text) { m ->
            val body = m.groupValues[1]
            when {
                body.startsWith("#x") || body.startsWith("#X") -> body.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                body.startsWith("#") -> body.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> entities[body] ?: m.value
            }
        }

    /** The text of the paragraphs of a docx, one paragraph per line. */
    fun docxText(file: File): String {
        ZipFile(file).use { zip ->
            val entry = zip.getEntry("word/document.xml") ?: throw java.io.IOException("Not a docx file")
            val xml = readPart(zip, entry)
            val out = StringBuilder()
            for (paragraph in xml.split("</w:p>")) {
                val line = StringBuilder()
                // Text runs, tabs and line breaks in the order they appear
                for (m in Regex("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|<w:tab\\s*/>|<w:br\\s*/>").findAll(paragraph)) {
                    when {
                        m.value.startsWith("<w:tab") -> line.append('\t')
                        m.value.startsWith("<w:br") -> line.append('\n')
                        else -> line.append(decodeEntities(m.groupValues[1]))
                    }
                }
                if (line.isNotEmpty() || out.isNotEmpty()) out.append(line).append('\n')
            }
            return out.toString().trim()
        }
    }

    /** html or xhtml turned into text: block ends become line breaks, tags are dropped, entities decoded. */
    fun htmlToText(html: String): String {
        var text = Regex("(?is)<(script|style|head)[^>]*>.*?</\\1>").replace(html, "")
        text = Regex("(?i)<br\\s*/?>|</(p|div|h[1-6]|li|tr|blockquote|section)>").replace(text, "\n")
        text = Regex("<[^>]+>").replace(text, "")
        text = decodeEntities(text)
        // Spaces inside a line are collapsed, empty lines are limited to one
        text = text.lines().joinToString("\n") { it.replace(Regex("[ \\t\\u00A0]+"), " ").trim() }
        return Regex("\n{3,}").replace(text, "\n\n").trim()
    }

    /** The chapters of an epub in reading order. A file in the spine that cannot be read is skipped. */
    fun epubChapters(file: File): List<DocChapter> {
        ZipFile(file).use { zip ->
            val container = zip.getEntry("META-INF/container.xml") ?: throw java.io.IOException("Not an epub file")
            val opfPath = Regex("full-path\\s*=\\s*\"([^\"]+)\"").find(readPart(zip, container))?.groupValues?.get(1)
                ?: throw java.io.IOException("Not an epub file")
            val opf = readPart(zip, zip.getEntry(opfPath) ?: throw java.io.IOException("Not an epub file"))
            val base = opfPath.substringBeforeLast('/', "")
            // id -> href from the manifest, then the order of the spine
            val manifest = HashMap<String, String>()
            for (m in Regex("<item\\s[^>]*>").findAll(opf)) {
                val id = Regex("\\sid\\s*=\\s*\"([^\"]*)\"").find(m.value)?.groupValues?.get(1) ?: continue
                val href = Regex("href\\s*=\\s*\"([^\"]*)\"").find(m.value)?.groupValues?.get(1) ?: continue
                manifest[id] = href
            }
            val order = Regex("<itemref\\s[^>]*idref\\s*=\\s*\"([^\"]*)\"").findAll(opf).map { it.groupValues[1] }.toList()
            val chapters = ArrayList<DocChapter>()
            for (id in order) {
                val href = (manifest[id] ?: continue).substringBefore('#')
                val full = resolve(base, java.net.URLDecoder.decode(href, "UTF-8"))
                val entry = zip.getEntry(full) ?: continue
                val html = try {
                    readPart(zip, entry)
                } catch (e: java.io.IOException) {
                    continue
                }
                val text = htmlToText(html)
                if (text.isEmpty()) continue
                val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.let { decodeEntities(it).trim() }
                    ?.takeIf { it.isNotEmpty() }
                    ?: text.lineSequence().first().take(60)
                chapters.add(DocChapter(title, text))
            }
            return chapters
        }
    }

    private fun resolve(base: String, href: String): String {
        val parts = ArrayList<String>()
        if (base.isNotEmpty()) parts.addAll(base.split('/'))
        for (piece in href.split('/')) {
            when (piece) {
                "", "." -> Unit
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                else -> parts.add(piece)
            }
        }
        return parts.joinToString("/")
    }

    private fun readPart(zip: ZipFile, entry: java.util.zip.ZipEntry): String {
        if (entry.size > MAX_PART_BYTES) throw java.io.IOException("Part too large")
        return zip.getInputStream(entry).use { String(it.readBytes(), Charsets.UTF_8) }
    }
}
