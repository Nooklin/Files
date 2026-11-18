package org.nooklin.files.fs

import java.io.File
import java.io.IOException
import java.util.Properties
import java.util.UUID

data class TrashItem(
    val id: String,
    val name: String,
    val originalPath: String,
    val isDir: Boolean,
    val size: Long,
    val deletedAt: Long
)

data class TrashLimits(
    val retentionDays: Int = 30,
    val maxBytes: Long = 2L * 1024 * 1024 * 1024,
    val maxItems: Int = 1000
)

/**
 * The Nooklin Files trash, separate from the trash in Nooklin Photos. Every item has its own folder with the file
 * under its original name, plus an id.meta file with where it came from. The meta file is written before the file
 * is moved, so a crash leaves at worst an empty folder, which cleanup removes.
 */
class TrashStore(private val root: File, val limits: TrashLimits = TrashLimits()) {

    fun payloadFile(item: TrashItem): File = File(File(root, item.id), item.name)

    private fun metaFile(id: String) = File(root, "$id.meta")

    fun fits(size: Long): Boolean = size <= limits.maxBytes

    @Synchronized
    fun put(src: File, now: Long = System.currentTimeMillis()): TrashItem {
        root.mkdirs()
        val id = now.toString() + "_" + UUID.randomUUID().toString().substring(0, 8)
        val item = TrashItem(id, src.name, src.absolutePath, src.isDirectory, FileEngine.sizeOfPlain(src), now)
        writeMeta(item)
        val folder = File(root, id)
        try {
            if (!folder.mkdirs()) throw IOException("Cannot create the trash folder")
            val target = File(folder, src.name)
            if (!src.renameTo(target)) {
                // A different volume or a refused rename: copy, then remove the original
                copyPlain(src, target)
                if (!FileEngine.deleteQuietly(src)) throw IOException("Could not remove " + src.name)
            }
        } catch (e: IOException) {
            FileEngine.deleteQuietly(folder)
            metaFile(id).delete()
            throw e
        }
        return item
    }

    @Synchronized
    fun list(): List<TrashItem> {
        val metas = root.listFiles { f -> f.isFile && f.name.endsWith(".meta") } ?: return emptyList()
        val items = ArrayList<TrashItem>(metas.size)
        for (meta in metas) {
            val item = readMeta(meta) ?: continue
            if (payloadFile(item).exists()) items.add(item)
        }
        return items.sortedByDescending { it.deletedAt }
    }

    /** Removes the item from the trash bookkeeping once its file has been moved out. */
    @Synchronized
    fun forget(item: TrashItem) {
        FileEngine.deleteQuietly(File(root, item.id))
        metaFile(item.id).delete()
    }

    @Synchronized
    fun deletePermanently(item: TrashItem) = forget(item)

    @Synchronized
    fun empty() {
        root.listFiles()?.forEach { FileEngine.deleteQuietly(it) }
    }

    /**
     * Removes what does not belong, what is older than the retention time and, oldest first, whatever is over the
     * size or count limit. Returns how many items were removed.
     */
    @Synchronized
    fun cleanup(now: Long = System.currentTimeMillis()): Int {
        var removed = 0
        val entries = root.listFiles() ?: return 0
        val known = HashSet<String>()
        for (entry in entries) {
            if (entry.isFile && entry.name.endsWith(".meta")) {
                val item = readMeta(entry)
                if (item == null || !File(root, item.id).isDirectory) {
                    // Meta without a folder, or one that cannot be read
                    entry.delete()
                } else {
                    known.add(item.id)
                }
            }
        }
        for (entry in entries) {
            if (entry.isDirectory && entry.name !in known) {
                // A folder without meta: nothing says where it came from, so it cannot be restored
                FileEngine.deleteQuietly(entry)
                removed++
            }
        }
        var items = list().sortedBy { it.deletedAt }.toMutableList()
        val cutoff = now - limits.retentionDays * 24L * 60L * 60L * 1000L
        val expired = items.filter { it.deletedAt < cutoff }
        for (item in expired) {
            forget(item)
            removed++
        }
        items = items.filter { it.deletedAt >= cutoff }.toMutableList()
        var total = items.sumOf { it.size }
        while (items.isNotEmpty() && (items.size > limits.maxItems || total > limits.maxBytes)) {
            val oldest = items.removeAt(0)
            total -= oldest.size
            forget(oldest)
            removed++
        }
        return removed
    }

    private fun writeMeta(item: TrashItem) {
        val props = Properties()
        props.setProperty("name", item.name)
        props.setProperty("original", item.originalPath)
        props.setProperty("dir", item.isDir.toString())
        props.setProperty("size", item.size.toString())
        props.setProperty("deletedAt", item.deletedAt.toString())
        val temp = File(root, item.id + ".meta.tmp")
        temp.outputStream().use { props.store(it, null) }
        if (!temp.renameTo(metaFile(item.id))) {
            temp.delete()
            throw IOException("Cannot write the trash information")
        }
    }

    private fun readMeta(file: File): TrashItem? {
        return try {
            val props = Properties()
            file.inputStream().use { props.load(it) }
            val name = props.getProperty("name") ?: return null
            val original = props.getProperty("original") ?: return null
            val deletedAt = props.getProperty("deletedAt")?.toLongOrNull() ?: return null
            TrashItem(
                id = file.name.removeSuffix(".meta"),
                name = name,
                originalPath = original,
                isDir = props.getProperty("dir") == "true",
                size = props.getProperty("size")?.toLongOrNull() ?: 0L,
                deletedAt = deletedAt
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun copyPlain(src: File, target: File) {
        if (src.isDirectory) {
            if (!target.mkdirs() && !target.isDirectory) throw IOException("Cannot create " + target.name)
            src.listFiles()?.forEach { copyPlain(it, File(target, it.name)) }
        } else {
            src.inputStream().use { input -> target.outputStream().use { output -> input.copyTo(output) } }
            target.setLastModified(src.lastModified())
        }
    }
}
