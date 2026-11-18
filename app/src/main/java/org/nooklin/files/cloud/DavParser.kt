package org.nooklin.files.cloud

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.DocumentBuilderFactory

/** One entry of a PROPFIND answer. */
data class DavEntry(
    val href: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val trashFilename: String?,
    val trashOriginalLocation: String?,
    val trashDeletedAt: Long
)

object DavParser {
    private const val DAV = "DAV:"
    private const val NC = "http://nextcloud.org/ns"

    fun parse(xml: String): List<DavEntry> {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        try {
            // No external definitions are read from an answer
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        } catch (e: Exception) {
            // This parser does not know the setting, it does not load them anyway
        }
        val document = factory.newDocumentBuilder().parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        val responses = document.getElementsByTagNameNS(DAV, "response")
        val entries = ArrayList<DavEntry>()
        for (i in 0 until responses.length) {
            val response = responses.item(i) as Element
            val href = response.getElementsByTagNameNS(DAV, "href").item(0)?.textContent?.trim() ?: continue
            var isDir = false
            var size = 0L
            var modified = 0L
            var trashName: String? = null
            var trashOriginal: String? = null
            var trashDeleted = 0L
            val propstats = response.getElementsByTagNameNS(DAV, "propstat")
            for (p in 0 until propstats.length) {
                val propstat = propstats.item(p) as Element
                val status = propstat.getElementsByTagNameNS(DAV, "status").item(0)?.textContent.orEmpty()
                // Only the properties the server could give: 200
                if (!status.contains(" 200")) continue
                val prop = propstat.getElementsByTagNameNS(DAV, "prop").item(0) as? Element ?: continue
                val children = prop.childNodes
                for (c in 0 until children.length) {
                    val node = children.item(c) as? Element ?: continue
                    val text = node.textContent?.trim().orEmpty()
                    when {
                        node.namespaceURI == DAV && node.localName == "resourcetype" ->
                            isDir = node.getElementsByTagNameNS(DAV, "collection").length > 0

                        node.namespaceURI == DAV && node.localName == "getcontentlength" -> size = text.toLongOrNull() ?: 0L
                        node.namespaceURI == DAV && node.localName == "getlastmodified" -> modified = parseDate(text)
                        node.namespaceURI == NC && node.localName == "trashbin-filename" -> trashName = text
                        node.namespaceURI == NC && node.localName == "trashbin-original-location" -> trashOriginal = text
                        node.namespaceURI == NC && node.localName == "trashbin-deletion-time" ->
                            trashDeleted = (text.toLongOrNull() ?: 0L) * 1000L
                    }
                }
            }
            entries.add(DavEntry(href, isDir, size, modified, trashName, trashOriginal, trashDeleted))
        }
        return entries
    }

    fun parseDate(text: String): Long =
        try {
            val format = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
            format.timeZone = TimeZone.getTimeZone("GMT")
            format.parse(text)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }

    /** The folder listing without the folder itself. */
    fun toItems(entries: List<DavEntry>, folderPath: String): List<RemoteItem> =
        entries.mapNotNull { entry ->
            val path = DavUrls.filesPathFromHref(entry.href)
            if (path == folderPath.trimEnd('/')) null
            else RemoteItem(DavUrls.nameOf(path), path, entry.isDir, if (entry.isDir) 0L else entry.size, entry.modified)
        }

    fun toTrashItems(entries: List<DavEntry>): List<RemoteTrashItem> =
        entries.mapNotNull { entry ->
            val trashPath = DavUrls.trashPathFromHref(entry.href)
            // The collection itself ("trash") and the restore folder have no deletion time
            if (!trashPath.startsWith("trash/") || entry.trashFilename == null) null
            else RemoteTrashItem(
                trashPath,
                entry.trashFilename,
                entry.trashOriginalLocation.orEmpty(),
                entry.trashDeletedAt,
                entry.size,
                entry.isDir
            )
        }
}
