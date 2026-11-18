package org.nooklin.files.editor

import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Which files the editor opens, and how a file is read and saved. */
object TextFiles {
    const val MAX_BYTES = 1024L * 1024L

    private val PLAIN_EXTENSIONS = setOf(
        "txt", "md", "log", "csv", "tsv", "ini", "conf", "cfg", "properties", "gradle", "toml", "yml", "yaml", "sql", "gitignore", "env"
    )

    private val EDITABLE = PLAIN_EXTENSIONS + Language.entries.flatMap { it.extensions }

    fun isEditableName(extension: String): Boolean = extension.lowercase() in EDITABLE

    /** The editor is used for known text types up to the size limit. Anything else goes to another app. */
    fun canEdit(file: File): Boolean = isEditableName(file.extension) && file.length() <= MAX_BYTES

    /** The text of the file, or null when it is not valid UTF-8 (saving it would damage it). */
    fun read(file: File): String? {
        val bytes = file.readBytes()
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (e: CharacterCodingException) {
            null
        }
    }

    /** Writes next to the file first and then renames, so a failed save does not leave a half written file. */
    fun write(file: File, text: String) {
        val temp = File(file.parentFile, ".nooklin-" + file.name + ".part")
        try {
            temp.writeText(text, Charsets.UTF_8)
            if (!temp.renameTo(file)) {
                file.writeText(text, Charsets.UTF_8)
                temp.delete()
            }
        } catch (e: Exception) {
            temp.delete()
            throw e
        }
    }
}
