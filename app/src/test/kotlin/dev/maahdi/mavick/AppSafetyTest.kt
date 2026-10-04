package dev.maahdi.mavick

import android.Manifest
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the privacy promises at runtime, from the app's real merged manifest. The build's
 * permission check (app/build.gradle.kts) guards the full permission list.
 */
@RunWith(AndroidJUnit4::class)
class AppSafetyTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun `app does not request internet access`() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong()),
        )

        assertThat(packageInfo.requestedPermissions.orEmpty().toList()).doesNotContain(Manifest.permission.INTERNET)
    }

    @Test
    fun `app is excluded from Android backup`() {
        assertThat(context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP).isEqualTo(0)
    }

    @Test
    fun `screens are hidden from screenshots and the Recents preview`() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val flags = activity.window.attributes.flags
                assertThat(flags and WindowManager.LayoutParams.FLAG_SECURE).isEqualTo(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }
}
