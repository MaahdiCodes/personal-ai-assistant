package dev.maahdi.mavick

import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.task.TaskDraft
import dev.maahdi.mavick.reminders.ReminderIntents
import dev.maahdi.mavick.ui.Destination
import dev.maahdi.mavick.ui.NavigationViewModel
import java.time.LocalTime
import org.junit.Test
import org.junit.runner.RunWith

/** "Send to Mavick" from another app opens the task editor, pre-filled, through the real activity. */
@RunWith(AndroidJUnit4::class)
class ShareIntoMavickTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun shareIntent(text: CharSequence?, subject: CharSequence? = null, type: String = "text/plain") =
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_SEND).setType(type).apply {
            text?.let { putExtra(Intent.EXTRA_TEXT, it) }
            subject?.let { putExtra(Intent.EXTRA_SUBJECT, it) }
        }

    /** Bold text, as some apps share it: a CharSequence that is not a String. */
    private fun styled(text: String) = SpannableString(text).apply {
        setSpan(StyleSpan(Typeface.BOLD), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }

    /** The screen Mavick shows after opening with [intent]. */
    private fun destinationAfter(intent: Intent): Destination {
        var destination: Destination? = null
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            scenario.onActivity { activity ->
                val navigation = ViewModelProvider(activity)[NavigationViewModel::class.java]
                destination = navigation.current
                // An editor opened from another app returns to that app when closed.
                assertThat(navigation.finishAfterEditor).isEqualTo(navigation.current is Destination.Editor)
            }
        }
        return destination!!
    }

    private fun draftAfter(intent: Intent): TaskDraft {
        val destination = destinationAfter(intent)
        assertThat(destination).isInstanceOf(Destination.Editor::class.java)
        return (destination as Destination.Editor).draft!!
    }

    @Test
    fun `plain shared text opens the editor pre-filled`() {
        val draft = draftAfter(shareIntent("Pay rent at 10am"))

        assertThat(draft.title).isEqualTo("Pay rent")
        assertThat(draft.dueTime).isEqualTo(LocalTime.of(10, 0))
    }

    @Test
    fun `styled shared text opens the editor pre-filled`() {
        val draft = draftAfter(shareIntent(styled("Call the bank at 5pm\nAsk about the card")))

        assertThat(draft.title).isEqualTo("Call the bank")
        assertThat(draft.dueTime).isEqualTo(LocalTime.of(17, 0))
        assertThat(draft.notes).isEqualTo("Ask about the card")
    }

    @Test
    fun `a styled note title is kept as the title`() {
        val draft = draftAfter(shareIntent(text = "Thursday 4pm", subject = styled("Dentist")))

        assertThat(draft.title).isEqualTo("Dentist")
        assertThat(draft.dueTime).isEqualTo(LocalTime.of(16, 0))
    }

    @Test
    fun `a share with no text opens the task list`() {
        assertThat(destinationAfter(shareIntent(text = null))).isEqualTo(Destination.Tasks)
    }

    @Test
    fun `a share that is not text is ignored`() {
        assertThat(destinationAfter(shareIntent(text = "Pay rent", type = "image/png"))).isEqualTo(Destination.Tasks)
    }

    private fun selectedTextIntent(text: CharSequence?) =
        Intent(context, MainActivity::class.java).setAction(Intent.ACTION_PROCESS_TEXT).setType("text/plain").apply {
            text?.let { putExtra(Intent.EXTRA_PROCESS_TEXT, it) }
        }

    @Test
    fun `selected text with Add to Mavick opens the editor pre-filled`() {
        val draft = draftAfter(selectedTextIntent(styled("Renew the passport by 12/11\nTake two photos")))

        assertThat(draft.title).isEqualTo("Renew the passport")
        assertThat(draft.notes).isEqualTo("Take two photos")
    }

    @Test
    fun `Add to Mavick with nothing selected opens the task list`() {
        assertThat(destinationAfter(selectedTextIntent(text = null))).isEqualTo(Destination.Tasks)
    }

    @Test
    fun `a reading warning opens Settings`() {
        val intent = Intent(context, MainActivity::class.java).setAction(ReminderIntents.ACTION_OPEN_SETTINGS)

        assertThat(destinationAfter(intent)).isEqualTo(Destination.Settings)
    }
}
