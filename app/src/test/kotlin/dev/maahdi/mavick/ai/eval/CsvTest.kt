package dev.maahdi.mavick.ai.eval

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CsvTest {
    @Test
    fun `fields with commas, quotes and line breaks survive a round trip`() {
        val rows = listOf(
            listOf("id", "text"),
            listOf("1", "Plain"),
            listOf("2", "Milk, eggs and \"fresh\" bread"),
            listOf("3", "Line one\nLine two\r\nLine three"),
            listOf("4", ""),
            listOf("5", "দুধ কিনো 😂"),
        )

        assertThat(Csv.read(Csv.write(rows))).isEqualTo(rows)
    }

    @Test
    fun `the file starts with a UTF-8 mark and uses Windows line ends, as Excel expects`() {
        val text = Csv.write(listOf(listOf("a", "b"), listOf("c", "d")))

        assertThat(text).isEqualTo("\uFEFFa,b\r\nc,d\r\n")
    }

    @Test
    fun `a message that looks like a formula can't run as one in a spreadsheet, and reads back unchanged`() {
        val risky = listOf("=HYPERLINK(\"http://x\")", "+1 call me", "-5", "@Sam", "'=already quoted", "'just an apostrophe")

        val written = Csv.write(listOf(risky))

        assertThat(written).contains("'=HYPERLINK")
        assertThat(written).contains(",'+1 call me,")
        assertThat(written).contains(",''=already quoted,")
        assertThat(written).contains(",'just an apostrophe")
        assertThat(Csv.read(written).single()).isEqualTo(risky)
    }

    @Test
    fun `files saved by spreadsheets are read, with or without the mark and with either line end`() {
        assertThat(Csv.read("a,b\nc,d\n")).isEqualTo(listOf(listOf("a", "b"), listOf("c", "d")))
        assertThat(Csv.read("\uFEFFa,b\r\nc,d")).isEqualTo(listOf(listOf("a", "b"), listOf("c", "d")))
        assertThat(Csv.read("")).isEmpty()
    }

    @Test
    fun `empty fields at the end of a line are kept`() {
        assertThat(Csv.read("a,,\r\n")).isEqualTo(listOf(listOf("a", "", "")))
    }

    @Test
    fun `a quote inside an unquoted field is just a character`() {
        assertThat(Csv.read("5\" screen,ok\n")).isEqualTo(listOf(listOf("5\" screen", "ok")))
    }
}
