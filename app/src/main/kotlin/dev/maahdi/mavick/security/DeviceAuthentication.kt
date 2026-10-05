package dev.maahdi.mavick.security

import android.content.Context
import android.hardware.biometrics.BiometricManager

/** Fingerprint, face, or the phone's own PIN, pattern or password. */
const val APP_LOCK_AUTHENTICATORS =
    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL

/** Whether the phone can confirm it's you; false when it has no screen lock set. */
fun canAuthenticate(context: Context): Boolean =
    context.getSystemService(BiometricManager::class.java)?.canAuthenticate(APP_LOCK_AUTHENTICATORS) ==
        BiometricManager.BIOMETRIC_SUCCESS
