package dev.maahdi.mavick.ai.eval

/**
 * Comma-separated values as spreadsheets read and write them (RFC 4180): a field with a comma,
 * quote or line break is quoted, and quotes inside are doubled.
 *
 * Written with a UTF-8 mark first, so Excel shows Bangla and emoji correctly. A field that starts
 * like a formula (=, +, -, @) gets a leading apostrophe, so a message can't run as a formula when
 * the file is opened in a spreadsheet; reading takes the apostrophe off again.
 */
object Csv {
    private const val BYTE_ORDER_MARK = '\uFEFF'
    private val FORMULA_START = setOf('=', '+', '-', '@', '\t', '\r')

    fun write(rows: List<List<String>>): String =
        BYTE_ORDER_MARK + rows.joinToString(separator = "\r\n", postfix = "\r\n") { row -> row.joinToString(",") { field(it) } }

    /** Every row, header included. A trailing empty line is ignored. */
    fun read(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var quoted = false
        var index = if (text.startsWith(BYTE_ORDER_MARK)) 1 else 0
        while (index < text.length) {
            val char = text[index]
            when {
                quoted && char == '"' && text.getOrNull(index + 1) == '"' -> {
                    field.append('"')
                    index++
                }
                char == '"' && (quoted || field.isEmpty()) -> quoted = !quoted
                !quoted && char == ',' -> {
                    row += unescape(field.toString())
                    field.clear()
                }
                !quoted && (char == '\n' || char == '\r') -> {
                    if (char == '\r' && text.getOrNull(index + 1) == '\n') index++
                    row += unescape(field.toString())
                    field.clear()
                    rows += row
                    row = mutableListOf()
                }
                else -> field.append(char)
            }
            index++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) {
            row += unescape(field.toString())
            rows += row
        }
        return rows
    }

    private fun field(value: String): String {
        val safe = if (needsApostrophe(value)) "'$value" else value
        val needsQuotes = safe.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    /** Starts like a formula, or with an apostrophe before one (so that apostrophe survives too). */
    private fun needsApostrophe(value: String): Boolean =
        value.firstOrNull() in FORMULA_START || (value.firstOrNull() == '\'' && value.getOrNull(1) in FORMULA_START)

    private fun unescape(value: String): String =
        if (value.firstOrNull() == '\'' && needsApostrophe(value.substring(1))) value.substring(1) else value
}
