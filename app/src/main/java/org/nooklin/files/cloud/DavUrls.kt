package org.nooklin.files.cloud

import java.net.URI

sealed interface ServerCheck {
    data class Ok(val server: String) : ServerCheck
    data object Empty : ServerCheck
    data object Invalid : ServerCheck
    data object HttpNotAllowed : ServerCheck
}

object DavUrls {
    /**
     * Turns what the user typed into the server address without a trailing slash, for example https://cloud.example.com
     * or https://example.com/nextcloud. No scheme means https. http is only accepted when allowHttp is on.
     */
    fun normalizeServer(input: String, allowHttp: Boolean): ServerCheck {
        var text = input.trim()
        if (text.isEmpty()) return ServerCheck.Empty
        if (!text.contains("://")) text = "https://$text"
        val uri = try {
            URI(text)
        } catch (e: Exception) {
            return ServerCheck.Invalid
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return ServerCheck.Invalid
        val host = uri.host
        if (host.isNullOrEmpty()) return ServerCheck.Invalid
        if (scheme == "http" && !allowHttp) return ServerCheck.HttpNotAllowed
        val port = if (uri.port >= 0) ":" + uri.port else ""
        val path = (uri.rawPath ?: "").trimEnd('/')
        return ServerCheck.Ok("$scheme://$host$port$path")
    }

    /** True when the address has something behind the host, like /nextcloud or /index.php/apps/files. */
    fun hasPath(server: String): Boolean =
        try {
            URI(server).rawPath.orEmpty().trim('/').isNotEmpty()
        } catch (e: Exception) {
            false
        }

    /** Percent-encodes every part of a path, keeps the slashes. A space becomes %20, a plus stays a plus. */
    fun encodePath(path: String): String =
        path.split('/').joinToString("/") { encodeSegment(it) }

    fun encodeSegment(segment: String): String {
        val out = StringBuilder()
        for (byte in segment.toByteArray(Charsets.UTF_8)) {
            val c = byte.toInt() and 0xFF
            val safe = (c in 'a'.code..'z'.code) || (c in 'A'.code..'Z'.code) || (c in '0'.code..'9'.code) ||
                c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
            if (safe) out.append(c.toChar()) else out.append('%').append(String.format("%02X", c))
        }
        return out.toString()
    }

    fun filesUrl(server: String, user: String, path: String): String =
        server + "/remote.php/dav/files/" + encodeSegment(user) + encodePath(path)

    fun trashUrl(server: String, user: String, trashPath: String): String =
        server + "/remote.php/dav/trashbin/" + encodeSegment(user) + "/" + encodePath(trashPath)

    /** The path of a file below the files root, from a href in a server answer. "" for the root itself. */
    fun filesPathFromHref(href: String): String {
        val decoded = decodeHref(href)
        val marker = "/dav/files/"
        val at = decoded.indexOf(marker)
        if (at < 0) return decoded.trimEnd('/')
        val afterUser = decoded.substring(at + marker.length).substringAfter('/', "")
        return if (afterUser.isEmpty()) "" else "/" + afterUser.trimEnd('/')
    }

    /** The path of a trash item relative to the user's trashbin, for example trash/a.txt.d123. */
    fun trashPathFromHref(href: String): String {
        val decoded = decodeHref(href)
        val marker = "/dav/trashbin/"
        val at = decoded.indexOf(marker)
        if (at < 0) return decoded.trim('/')
        return decoded.substring(at + marker.length).substringAfter('/', "").trim('/')
    }

    private fun decodeHref(href: String): String =
        try {
            URI(href).path ?: href
        } catch (e: Exception) {
            href
        }

    fun parentOf(path: String): String = path.substringBeforeLast('/', "")

    fun nameOf(path: String): String = path.substringAfterLast('/')

    /** name, or name (1), name (2) and so on, when the name is already in taken. */
    fun uniqueName(name: String, taken: Set<String>): String {
        if (name !in taken) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var n = 1
        while (true) {
            val candidate = "$base ($n)$ext"
            if (candidate !in taken) return candidate
            n++
        }
    }
}
