package dev.maahdi.mavick.ui

import com.google.common.truth.Truth.assertThat
import dev.maahdi.mavick.data.task.TaskDraft
import org.junit.Test

class NavigationViewModelTest {
    private val navigation = NavigationViewModel()

    @Test
    fun `it starts on the task list, which has nothing behind it`() {
        assertThat(navigation.current).isEqualTo(Destination.Tasks)
        assertThat(navigation.back()).isFalse()
    }

    @Test
    fun `messages open over each other and back returns step by step`() {
        navigation.openInbox()
        navigation.openMessage("m1")
        navigation.openEditor(draft = TaskDraft(title = "Call Sam"))

        assertThat(navigation.current).isInstanceOf(Destination.Editor::class.java)
        navigation.back()
        assertThat(navigation.current).isEqualTo(Destination.Message("m1"))
        navigation.back()
        assertThat(navigation.current).isEqualTo(Destination.Inbox)
        navigation.back()
        assertThat(navigation.current).isEqualTo(Destination.Tasks)
    }

    @Test
    fun `the same screen is never opened twice in a row`() {
        navigation.openSettings()
        navigation.openSettings()

        navigation.back()

        assertThat(navigation.current).isEqualTo(Destination.Tasks)
    }

    @Test
    fun `rules open from Settings and from the inbox`() {
        navigation.openSettings()
        navigation.openReading()
        assertThat(navigation.current).isEqualTo(Destination.Reading)
        navigation.back()
        assertThat(navigation.current).isEqualTo(Destination.Settings)
    }

    @Test
    fun `a suggestion's Edit opens an editor that knows the suggestion, over the list`() {
        navigation.openSuggestions()
        navigation.openEditor(draft = TaskDraft(title = "Send the form"), suggestionId = "s1")

        val editor = navigation.current as Destination.Editor
        assertThat(editor.suggestionId).isEqualTo("s1")
        assertThat(editor.draft!!.title).isEqualTo("Send the form")
        navigation.back()
        assertThat(navigation.current).isEqualTo(Destination.Suggestions)
    }

    @Test
    fun `an editor opened from the app returns to the app, one from a share returns to the other app`() {
        navigation.openEditor()
        assertThat(navigation.finishAfterEditor).isFalse()

        navigation.openEditor(draft = TaskDraft(title = "Shared"), fromShare = true)
        assertThat(navigation.finishAfterEditor).isTrue()
        navigation.back()
        assertThat(navigation.current).isEqualTo(Destination.Tasks) // the first editor was replaced
    }
}
