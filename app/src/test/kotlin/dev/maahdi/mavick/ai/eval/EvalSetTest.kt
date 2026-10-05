package dev.maahdi.mavick.ai.eval

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.ai.ChatLine
import dev.maahdi.mavick.capture.SourceApp
import dev.maahdi.mavick.data.message.MessageEntity
import dev.maahdi.mavick.testing.TEST_ZONE
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Test

class EvalSetTest {
    private val sentAt = Instant.parse("2026-10-05T04:00:00Z") // 10:00 in Dhaka

    private fun message(id: String, text: String, sender: String? = "Sam", fromMe: Boolean = false, minutesEarlier: Long = 0) = MessageEntity(
        id = id,
        app = SourceApp.WHATSAPP,
        accountKey = "0",
        conversationKey = "s:family",
        conversationTitle = "Family",
        sender = if (fromMe) null else sender,
        text = text,
        postedAt = sentAt.minusSeconds(minutesEarlier * 60),
        receivedAt = sentAt,
        isFromMe = fromMe,
        isGroup = true,
        cutShort = false,
        dedupHash = id,
    )

    /** Fills in the label columns, as you would in a spreadsheet. */
    private fun label(csv: String, id: String, actionable: String, title: String = "", whenText: String = ""): String {
        val rows = Csv.read(csv).toMutableList()
        val index = rows.indexOfFirst { it.first() == id }
        rows[index] = rows[index].toMutableList().apply {
            this[1] = actionable
            this[2] = title
            this[3] = whenText
        }
        return Csv.write(rows)
    }

    @Test
    fun `an exported message reads back as the input the AI would get, with its context`() {
        val context = listOf(message("m1", "Are you coming tomorrow?", sender = "Rina", minutesEarlier = 5), message("m2", "yes", fromMe = true, minutesEarlier = 4))
        val exported = EvalSet.write(listOf(message("m3", "Party at 5, bring the cake") to context), TEST_ZONE)

        val read = EvalSet.read(label(exported, "m3", "y", "Bring the cake", "2026-10-05 17:00"), TEST_ZONE)

        assertThat(read.problems).isEmpty()
        val row = read.rows.single()
        assertThat(row.id).isEqualTo("m3")
        with(row.input) {
            assertThat(app).isEqualTo(SourceApp.WHATSAPP)
            assertThat(chatTitle).isEqualTo("Family")
            assertThat(isGroup).isTrue()
            assertThat(message).isEqualTo(ChatLine("Sam", false, "Party at 5, bring the cake"))
            assertThat(earlier).containsExactly(ChatLine("Rina", false, "Are you coming tomorrow?"), ChatLine(null, true, "yes")).inOrder()
            assertThat(sentAt).isEqualTo(LocalDateTime.of(2026, 10, 5, 10, 0))
        }
        assertThat(row.expected).isEqualTo(EvalExpectation(true, "Bring the cake", LocalDate.of(2026, 10, 5), LocalTime.of(17, 0)))
    }

    @Test
    fun `an exported file is unlabelled, and only the last three context messages are kept`() {
        val context = (1..5).map { message("c$it", "context $it", minutesEarlier = 10L - it) }
        val read = EvalSet.read(EvalSet.write(listOf(message("m", "hello") to context), TEST_ZONE), TEST_ZONE)

        val row = read.rows.single()
        assertThat(row.expected.actionable).isNull()
        assertThat(row.input.earlier.map { it.text }).containsExactly("context 3", "context 4", "context 5").inOrder()
    }

    @Test
    fun `labels accept yes and no in the usual spellings, and a date alone`() {
        val exported = EvalSet.write(listOf(message("a", "x") to emptyList(), message("b", "y") to emptyList(), message("c", "z") to emptyList()), TEST_ZONE)
        val labelled = label(label(label(exported, "a", "Yes", whenText = "2026-10-08"), "b", "0"), "c", "x", whenText = "2026-10-08T09:30")

        val rows = EvalSet.read(labelled, TEST_ZONE).rows.associateBy { it.id }

        assertThat(rows.getValue("a").expected).isEqualTo(EvalExpectation(true, null, LocalDate.of(2026, 10, 8), null))
        assertThat(rows.getValue("b").expected.actionable).isFalse()
        assertThat(rows.getValue("c").expected.whenTime).isEqualTo(LocalTime.of(9, 30))
    }

    @Test
    fun `bad lines are left out and named by line number, never by content`() {
        val exported = EvalSet.write(listOf(message("a", "secret text") to emptyList(), message("b", "fine") to emptyList()), TEST_ZONE)
        val labelled = label(label(exported, "a", "maybe"), "b", "y", whenText = "next Friday")

        val read = EvalSet.read(labelled, TEST_ZONE)

        assertThat(read.rows).isEmpty()
        assertThat(read.problems).containsExactly(
            "Line 2: expected_actionable must be y or n",
            "Line 3: expected_when must look like 2026-10-08 or 2026-10-08 17:00",
        )
        assertThat(read.problems.joinToString()).doesNotContain("secret")
    }

    @Test
    fun `a file without the right columns is refused whole`() {
        val read = EvalSet.read("id,text\r\n1,hello\r\n", TEST_ZONE)

        assertThat(read.rows).isEmpty()
        assertThat(read.problems.single()).startsWith("Missing columns: expected_actionable")
    }

    @Test
    fun `blank lines are ignored`() {
        val exported = EvalSet.write(listOf(message("a", "x") to emptyList()), TEST_ZONE)

        assertThat(EvalSet.read(exported + ",,,\r\n\r\n", TEST_ZONE).problems).isEmpty()
    }

    @Test
    fun `the sample file in eval stays readable`() {
        val sample = generateSequence(File("").absoluteFile) { it.parentFile }.map { File(it, "eval/sample.csv") }.first { it.isFile }

        val read = EvalSet.read(sample.readText(), TEST_ZONE)

        assertThat(read.problems).isEmpty()
        assertThat(read.rows).hasSize(12)
        assertThat(read.rows.count { it.expected.actionable != null }).isEqualTo(11)
    }
}
