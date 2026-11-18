package org.nooklin.files.fs

import java.util.Calendar

enum class MediaGroupBy { NONE, DATE, FOLDER, TYPE, LETTER }
enum class MediaSort { NAME, DATE, SIZE }

/** A header with the files below it. */
data class MediaSection(val title: String, val files: List<MediaFile>)

object MediaGrouping {
    /**
     * Sorts the files and puts them under headers. Inside a header the files follow the chosen sort,
     * the headers themselves follow the kind of grouping: months newest first (oldest first when sorting
     * ascending by date), the others alphabetical. monthLabel turns a time into the header text of its month.
     */
    fun organize(
        files: List<MediaFile>,
        groupBy: MediaGroupBy,
        sort: MediaSort,
        ascending: Boolean,
        monthLabel: (Long) -> String
    ): List<MediaSection> {
        val comparator: Comparator<MediaFile> = when (sort) {
            MediaSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            MediaSort.DATE -> compareBy { it.modified }
            MediaSort.SIZE -> compareBy { it.size }
        }
        val sorted = files.sortedWith(if (ascending) comparator else comparator.reversed())
        if (groupBy == MediaGroupBy.NONE) return if (sorted.isEmpty()) emptyList() else listOf(MediaSection("", sorted))

        // key -> (header text, order value)
        val sections = LinkedHashMap<String, MutableList<MediaFile>>()
        val order = HashMap<String, Comparable<*>>()
        for (file in sorted) {
            val key: String
            when (groupBy) {
                MediaGroupBy.DATE -> {
                    val cal = Calendar.getInstance().apply { timeInMillis = file.modified }
                    val month = cal.get(Calendar.YEAR) * 12 + cal.get(Calendar.MONTH)
                    key = monthLabel(file.modified)
                    order[key] = month
                }
                MediaGroupBy.FOLDER -> {
                    key = java.io.File(file.path).parent ?: ""
                    order[key] = key.lowercase()
                }
                MediaGroupBy.TYPE -> {
                    key = file.extension.uppercase()
                    order[key] = key
                }
                else -> {
                    val first = file.name.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()
                    key = if (first != null && first.isLetter()) first.toString() else "#"
                    // # goes last
                    order[key] = if (key == "#") "￿" else key
                }
            }
            sections.getOrPut(key) { ArrayList() }.add(file)
        }
        @Suppress("UNCHECKED_CAST")
        val keys = sections.keys.sortedWith { a, b ->
            val result = (order[a] as Comparable<Any>).compareTo(order[b] as Any)
            if (groupBy == MediaGroupBy.DATE && !(sort == MediaSort.DATE && ascending)) -result else result
        }
        return keys.map { MediaSection(it, sections.getValue(it)) }
    }
}
