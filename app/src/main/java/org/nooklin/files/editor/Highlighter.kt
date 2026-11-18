package org.nooklin.files.editor

enum class TokenType { KEYWORD, STRING, COMMENT, NUMBER, TAG }

data class Span(val start: Int, val end: Int, val type: TokenType)

/** The 10 languages that get colors. Everything else opens as plain text. */
enum class Language(
    val extensions: Set<String>,
    val keywords: Set<String> = emptySet(),
    val lineComments: List<String> = emptyList(),
    val blockStart: String? = null,
    val blockEnd: String? = null,
    val quotes: String = "\"'",
    val tripleQuotes: Boolean = false,
    val markup: Boolean = false
) {
    KOTLIN(
        setOf("kt", "kts"),
        setOf(
            "package", "import", "class", "interface", "object", "fun", "val", "var", "if", "else", "when", "for", "while", "do",
            "return", "break", "continue", "in", "is", "as", "null", "true", "false", "this", "super", "try", "catch", "finally",
            "throw", "override", "private", "public", "protected", "internal", "open", "abstract", "sealed", "data", "enum",
            "companion", "suspend", "inline", "lateinit", "const", "typealias", "by", "init", "constructor"
        ),
        listOf("//"), "/*", "*/", tripleQuotes = true
    ),
    JAVA(
        setOf("java"),
        setOf(
            "package", "import", "class", "interface", "enum", "extends", "implements", "new", "if", "else", "switch", "case",
            "default", "for", "while", "do", "return", "break", "continue", "try", "catch", "finally", "throw", "throws", "public",
            "private", "protected", "static", "final", "abstract", "void", "int", "long", "double", "float", "boolean", "char",
            "byte", "short", "null", "true", "false", "this", "super", "instanceof", "synchronized", "volatile", "transient"
        ),
        listOf("//"), "/*", "*/"
    ),
    PYTHON(
        setOf("py"),
        setOf(
            "def", "class", "if", "elif", "else", "for", "while", "return", "import", "from", "as", "try", "except", "finally",
            "raise", "with", "pass", "break", "continue", "lambda", "yield", "global", "nonlocal", "in", "is", "not", "and", "or",
            "None", "True", "False", "async", "await", "del", "assert"
        ),
        listOf("#"), tripleQuotes = true
    ),
    JAVASCRIPT(
        setOf("js", "mjs", "ts"),
        setOf(
            "function", "const", "let", "var", "if", "else", "for", "while", "do", "return", "break", "continue", "switch", "case",
            "default", "new", "class", "extends", "import", "export", "from", "try", "catch", "finally", "throw", "typeof",
            "instanceof", "in", "of", "async", "await", "null", "undefined", "true", "false", "this", "super", "interface", "type",
            "enum", "implements", "public", "private", "protected", "readonly"
        ),
        listOf("//"), "/*", "*/", quotes = "\"'`"
    ),
    HTML(setOf("html", "htm"), markup = true),
    XML(setOf("xml", "svg"), markup = true),
    CSS(setOf("css"), blockStart = "/*", blockEnd = "*/"),
    JSON(setOf("json"), setOf("true", "false", "null"), quotes = "\""),
    SHELL(
        setOf("sh", "bash"),
        setOf(
            "if", "then", "else", "elif", "fi", "for", "while", "do", "done", "case", "esac", "function", "return", "in",
            "export", "local", "echo", "exit", "source", "set", "unset"
        ),
        listOf("#")
    ),
    C(
        setOf("c", "h", "cpp", "hpp", "cc"),
        setOf(
            "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return", "struct", "union",
            "enum", "typedef", "static", "const", "extern", "void", "int", "long", "short", "char", "float", "double", "unsigned",
            "signed", "sizeof", "class", "public", "private", "protected", "namespace", "using", "template", "new", "delete",
            "nullptr", "true", "false", "bool", "auto", "include", "define"
        ),
        listOf("//"), "/*", "*/"
    );

    companion object {
        fun forExtension(extension: String): Language? {
            val ext = extension.lowercase()
            return entries.firstOrNull { ext in it.extensions }
        }
    }
}

object Highlighter {
    /** Colored parts of the text, in order and not overlapping. Anything not listed is plain. */
    fun highlight(text: String, language: Language): List<Span> =
        if (language.markup) markup(text) else code(text, language)

    private fun isWordChar(c: Char) = c.isLetterOrDigit() || c == '_'

    private fun code(text: String, lang: Language): List<Span> {
        val spans = ArrayList<Span>()
        val n = text.length
        var i = 0
        while (i < n) {
            val c = text[i]
            val lineComment = lang.lineComments.firstOrNull { text.startsWith(it, i) }
            when {
                lineComment != null -> {
                    val end = text.indexOf('\n', i).let { if (it < 0) n else it }
                    spans.add(Span(i, end, TokenType.COMMENT))
                    i = end
                }

                lang.blockStart != null && text.startsWith(lang.blockStart, i) -> {
                    val close = text.indexOf(lang.blockEnd!!, i + lang.blockStart.length)
                    val end = if (close < 0) n else close + lang.blockEnd.length
                    spans.add(Span(i, end, TokenType.COMMENT))
                    i = end
                }

                c in lang.quotes -> {
                    val triple = c.toString().repeat(3)
                    val end = if (lang.tripleQuotes && text.startsWith(triple, i)) {
                        val close = text.indexOf(triple, i + 3)
                        if (close < 0) n else close + 3
                    } else {
                        stringEnd(text, i, multiline = c == '`')
                    }
                    spans.add(Span(i, end, TokenType.STRING))
                    i = end
                }

                c.isDigit() && (i == 0 || !isWordChar(text[i - 1])) -> {
                    var end = i + 1
                    while (end < n && (text[end].isLetterOrDigit() || text[end] == '.' || text[end] == '_')) end++
                    spans.add(Span(i, end, TokenType.NUMBER))
                    i = end
                }

                c.isLetter() || c == '_' -> {
                    var end = i + 1
                    while (end < n && isWordChar(text[end])) end++
                    if (text.substring(i, end) in lang.keywords) spans.add(Span(i, end, TokenType.KEYWORD))
                    i = end
                }

                else -> i++
            }
        }
        return spans
    }

    /** End of a quoted string that starts at start. It ends at the closing quote, or at the end of the line when there is none. */
    private fun stringEnd(text: String, start: Int, multiline: Boolean): Int {
        val quote = text[start]
        var i = start + 1
        while (i < text.length) {
            val c = text[i]
            if (c == '\\') {
                i += 2
                continue
            }
            if (c == quote) return i + 1
            if (c == '\n' && !multiline) return i
            i++
        }
        return text.length
    }

    private fun markup(text: String): List<Span> {
        val spans = ArrayList<Span>()
        val n = text.length
        var i = 0
        while (i < n) {
            if (text.startsWith("<!--", i)) {
                val close = text.indexOf("-->", i + 4)
                val end = if (close < 0) n else close + 3
                spans.add(Span(i, end, TokenType.COMMENT))
                i = end
            } else if (text[i] == '<' && i + 1 < n && (text[i + 1].isLetter() || text[i + 1] in "/!?")) {
                var j = i + 1
                if (text[j] == '/') j++
                while (j < n && !text[j].isWhitespace() && text[j] != '>' && text[j] != '/') j++
                spans.add(Span(i, j, TokenType.TAG))
                while (j < n && text[j] != '>') {
                    if (text[j] == '"' || text[j] == '\'') {
                        val close = text.indexOf(text[j], j + 1)
                        val end = if (close < 0) n else close + 1
                        spans.add(Span(j, end, TokenType.STRING))
                        j = end
                    } else {
                        j++
                    }
                }
                if (j < n) {
                    // The closing > together with a / right before it
                    val closeStart = if (j > i && text[j - 1] == '/' && (spans.lastOrNull()?.end ?: 0) <= j - 1) j - 1 else j
                    spans.add(Span(closeStart, j + 1, TokenType.TAG))
                    j++
                }
                i = j
            } else {
                i++
            }
        }
        return spans
    }
}
