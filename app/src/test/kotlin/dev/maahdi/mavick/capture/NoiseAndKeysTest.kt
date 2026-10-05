package dev.maahdi.mavick.capture

import com.google.common.truth.Truth.assertThat
import java.time.Instant
import org.junit.Test

/** The noise filter, the supported-app list, account keys and message fingerprints. */
class NoiseAndKeysTest {
    private val posted = Instant.parse("2026-10-05T04:00:00Z")

    private fun raw(isGroupSummary: Boolean = false, isOngoing: Boolean = false, category: String? = null) =
        RawNotification("com.whatsapp", 0, posted, isGroupSummary = isGroupSummary, isOngoing = isOngoing, category = category)

    @Test
    fun `summaries, ongoing notifications and call or progress categories are noise`() {
        assertThat(Noise.isNoise(raw(isGroupSummary = true))).isTrue()
        assertThat(Noise.isNoise(raw(isOngoing = true))).isTrue()
        listOf("call", "missed_call", "progress", "service", "sys", "transport", "status").forEach { category ->
            assertThat(Noise.isNoise(raw(category = category))).isTrue()
        }
        assertThat(Noise.isNoise(raw(category = "msg"))).isFalse()
        assertThat(Noise.isNoise(raw(category = "email"))).isFalse()
        assertThat(Noise.isNoise(raw())).isFalse()
    }

    @Test
    fun `placeholder texts are noise in any case, real texts are not`() {
        listOf(
            "This message was deleted",
            "you deleted this message.",
            "Missed video call",
            "Incoming voice call",
            "Checking for new messages",
            "5 new messages",
            "12 messages from 3 chats",
            "",
            "  ",
        ).forEach { assertThat(Noise.isNoiseText(it)).isTrue() }
        listOf("Call me at 5", "This message was deleted by mistake, resending", "5 messages left to send").forEach {
            assertThat(Noise.isNoiseText(it)).isFalse()
        }
    }

    @Test
    fun `only the five supported apps are recognised`() {
        assertThat(SourceApp.fromPackage("com.whatsapp")).isEqualTo(SourceApp.WHATSAPP)
        assertThat(SourceApp.fromPackage("com.whatsapp.w4b")).isEqualTo(SourceApp.WHATSAPP_BUSINESS)
        assertThat(SourceApp.fromPackage("com.facebook.orca")).isEqualTo(SourceApp.MESSENGER)
        assertThat(SourceApp.fromPackage("com.google.android.gm")).isEqualTo(SourceApp.GMAIL)
        assertThat(SourceApp.fromPackage("com.google.android.keep")).isEqualTo(SourceApp.KEEP)
        listOf("com.bkash.customerapp", "com.android.mms", "com.whatsapp.extra", "", null).forEach {
            assertThat(SourceApp.fromPackage(it)).isNull()
        }
    }

    @Test
    fun `stored app names never change`() {
        assertThat(SourceApp.entries.map { it.name }).containsExactly("WHATSAPP", "WHATSAPP_BUSINESS", "MESSENGER", "GMAIL", "KEEP").inOrder()
    }

    @Test
    fun `account keys combine the Android user and the app's account name`() {
        assertThat(Accounts.key(0)).isEqualTo("0")
        assertThat(Accounts.key(999, null)).isEqualTo("999")
        assertThat(Accounts.key(0, " ")).isEqualTo("0")
        assertThat(Accounts.key(0, "you@gmail.com")).isEqualTo("0/you@gmail.com")
        assertThat(Accounts.userIdOf("0/you@gmail.com")).isEqualTo(0)
        assertThat(Accounts.userIdOf("999")).isEqualTo(999)
        assertThat(Accounts.labelOf("0/you@gmail.com")).isEqualTo("you@gmail.com")
        assertThat(Accounts.labelOf("0")).isNull()
    }

    private val base = IncomingMessage(SourceApp.WHATSAPP, "0", "s:sam", "Sam", "Sam", "Call me", posted, isFromMe = false, isGroup = false, cutShort = false)

    @Test
    fun `the same message always has the same fingerprint`() {
        assertThat(base.dedupHash()).isEqualTo(base.copy(conversationTitle = "Sam renamed", cutShort = true).dedupHash())
        assertThat(base.dedupHash()).matches("[0-9a-f]{64}")
    }

    @Test
    fun `any difference that matters gives a different fingerprint, so accounts and chats never mix`() {
        val variants = listOf(
            base.copy(app = SourceApp.WHATSAPP_BUSINESS),
            base.copy(accountKey = "999"),
            base.copy(conversationKey = "s:rina"),
            base.copy(sender = "Rina"),
            base.copy(text = "Call me!"),
            base.copy(postedAt = posted.plusMillis(1)),
            base.copy(sender = null, isFromMe = true),
        )

        assertThat(variants.map { it.dedupHash() }.toSet()).hasSize(variants.size)
        assertThat(variants.map { it.dedupHash() }).doesNotContain(base.dedupHash())
    }

    @Test
    fun `fields can't run into each other`() {
        val a = base.copy(sender = "ab", text = "c")
        val b = base.copy(sender = "a", text = "bc")

        assertThat(a.dedupHash()).isNotEqualTo(b.dedupHash())
    }
}
