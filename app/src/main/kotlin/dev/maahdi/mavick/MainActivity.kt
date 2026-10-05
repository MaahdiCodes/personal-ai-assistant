package dev.maahdi.mavick

import android.content.Intent
import android.hardware.biometrics.BiometricPrompt
import android.os.Bundle
import android.os.CancellationSignal
import android.service.notification.NotificationListenerService
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import dev.maahdi.mavick.reminders.ReminderIntents
import dev.maahdi.mavick.security.APP_LOCK_AUTHENTICATORS
import dev.maahdi.mavick.security.canAuthenticate
import dev.maahdi.mavick.share.SharedText
import dev.maahdi.mavick.ui.MavickRoot
import dev.maahdi.mavick.ui.NavigationViewModel
import dev.maahdi.mavick.ui.theme.MavickTheme
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container: AppContainer get() = (application as MavickApp).container
    private val navigation: NavigationViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keeps Mavick out of screenshots, screen recordings and the Recents preview.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)

        setContent {
            MavickTheme {
                MavickRoot(
                    container = container,
                    navigation = navigation,
                    onUnlockRequest = ::requestUnlock,
                    onFinish = ::finish,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        container.appLock.onScreenShown(canAuthenticate(this))
        // If Android dropped message reading (HyperOS does), ask it to connect again. Does nothing
        // when reading is already connected or access isn't granted.
        if (container.hasNotificationAccess()) NotificationListenerService.requestRebind(container.listenerComponent)
        // Messages left waiting for the AI (paused for the battery, say) get looked at now.
        container.suggestionWorker.wake()
        // Restores alarms if Android force-stopped Mavick, once per process, off the main thread.
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                container.reminderEngine.resyncOncePerProcess()
            } catch (e: Exception) {
                // The task list shows storage problems; never crash the app over a resync.
                Log.w(TAG, "Reminder resync failed: ${e.javaClass.simpleName}")
            } catch (e: LinkageError) {
                // The encryption library failed to load; the task list explains it.
                Log.w(TAG, "Reminder resync failed: ${e.javaClass.simpleName}")
            }
        }
    }

    override fun onStop() {
        super.onStop()
        container.appLock.onScreenHidden()
    }

    private fun handleIntent(intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SEND -> if (intent.type?.startsWith("text/") == true) {
                // Read as CharSequence: some apps share styled text, which getStringExtra would drop.
                openTextAsTask(
                    subject = intent.getCharSequenceExtra(Intent.EXTRA_SUBJECT)?.toString(),
                    text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
                )
            }
            Intent.ACTION_PROCESS_TEXT -> openTextAsTask(subject = null, text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString())
            ReminderIntents.ACTION_OPEN_TASK -> ReminderIntents.taskIdOf(intent)?.let { navigation.openEditor(taskId = it) }
            ReminderIntents.ACTION_OPEN_SETTINGS -> navigation.openSettings()
            ReminderIntents.ACTION_OPEN_SUGGESTIONS -> navigation.openSuggestions()
        }
    }

    /**
     * Text from another app opens as a new task: shared (for example Google Keep: ⋮ > Send >
     * Mavick), or selected and then "Add to Mavick" picked from the selection menu.
     */
    private fun openTextAsTask(subject: String?, text: String?) {
        val draft = SharedText.toDraft(
            subject = subject,
            text = text,
            fromPackage = referrer?.host,
            parser = container.whenParser(),
            now = LocalDateTime.now(container.clock()),
        ) ?: return
        navigation.openEditor(draft = draft, fromShare = true)
    }

    private fun requestUnlock() {
        if (!canAuthenticate(this)) {
            // The phone has no screen lock any more, so there is nothing to check against.
            container.appLock.onUnlocked()
            return
        }
        BiometricPrompt.Builder(this)
            .setTitle(getString(R.string.lock_prompt_title))
            .setAllowedAuthenticators(APP_LOCK_AUTHENTICATORS)
            .build()
            .authenticate(
                CancellationSignal(),
                mainExecutor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        container.appLock.onUnlocked()
                    }
                },
            )
    }

    private companion object {
        const val TAG = "Mavick"
    }
}
