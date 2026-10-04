package dev.maahdi.mavick

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.maahdi.mavick.ui.HomeScreen
import dev.maahdi.mavick.ui.theme.MavickTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Keeps Mavick out of screenshots, screen recordings and the Recents preview.
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()

        val container = (application as MavickApp).container
        val hasInternetPermission =
            checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
        val isDebugBuild = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val versionName = packageManager
            .getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            .versionName
            .orEmpty()

        setContent {
            MavickTheme {
                HomeScreen(
                    checkStorage = container.storageHealthCheck::run,
                    hasInternetPermission = hasInternetPermission,
                    versionName = versionName,
                    isDebugBuild = isDebugBuild,
                )
            }
        }
    }
}
