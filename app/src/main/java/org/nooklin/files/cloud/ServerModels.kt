package org.nooklin.files.cloud

import org.json.JSONArray
import org.json.JSONObject

enum class ServerType { FTP, SMB }

/** A saved FTP or SMB server. The password is plain text in memory only, on disk it is encrypted. */
data class SavedServer(
    val id: String,
    val type: ServerType,
    val name: String,
    val host: String,
    val port: Int,
    /** SMB only: the name of the share, for example "public". Empty for FTP. */
    val share: String,
    val user: String,
    val password: String
) {
    val defaultPort: Int get() = defaultPortFor(type)

    companion object {
        fun defaultPortFor(type: ServerType): Int = if (type == ServerType.FTP) 21 else 445
    }
}

/** Why a request to an FTP or SMB server failed. */
enum class ServerError { LOGIN, NOT_FOUND, SHARE, DENIED, OTHER }

class ServerException(val kind: ServerError, message: String) : java.io.IOException(message)

/** The saved servers as JSON. The passwords go through the two functions given, the real ones use the Keystore. */
object ServerJson {
    fun write(servers: List<SavedServer>, encrypt: (String) -> String): String {
        val array = JSONArray()
        for (s in servers) {
            array.put(
                JSONObject()
                    .put("id", s.id).put("type", s.type.name).put("name", s.name).put("host", s.host)
                    .put("port", s.port).put("share", s.share).put("user", s.user)
                    .put("password", if (s.password.isEmpty()) "" else encrypt(s.password))
            )
        }
        return array.toString()
    }

    /** A server whose password cannot be decrypted any more is left out, it would only fail to log in. */
    fun read(json: String?, decrypt: (String) -> String?): List<SavedServer> {
        if (json.isNullOrEmpty()) return emptyList()
        return try {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { i ->
                val o = array.getJSONObject(i)
                val stored = o.optString("password")
                val password = if (stored.isEmpty()) "" else decrypt(stored) ?: return@mapNotNull null
                val type = ServerType.entries.find { it.name == o.optString("type") } ?: return@mapNotNull null
                SavedServer(
                    o.getString("id"), type, o.optString("name"), o.getString("host"),
                    o.optInt("port", SavedServer.defaultPortFor(type)), o.optString("share"), o.optString("user"), password
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
