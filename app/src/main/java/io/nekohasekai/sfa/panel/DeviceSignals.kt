package io.nekohasekai.sfa.panel

import android.media.MediaDrm
import android.os.Build
import java.security.MessageDigest
import java.util.UUID

/**
 * What the server needs to give each phone one guest trial (panel guest.go). Only hashes
 * leave the phone, and only when the user starts a trial.
 *
 * - [drmId]: the Widevine DRM device id. It survives reinstalls and, on most phones, a
 *   factory reset. Needs no permission; some phones (and most emulators) have none.
 * - [isEmulator]: the build looks like an emulator, which can be reset at will.
 *
 * The other id, ANDROID_ID, is [PanelStore.hardwareId], sent with every sign-in.
 */
object DeviceSignals {
    private val WIDEVINE = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)

    val drmId: String by lazy {
        runCatching {
            val drm = MediaDrm(WIDEVINE)
            try {
                val raw = drm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
                if (raw.isEmpty()) "" else sha256("xerobox-drm:" + raw.joinToString("") { "%02x".format(it) })
            } finally {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    drm.close()
                } else {
                    @Suppress("DEPRECATION")
                    drm.release()
                }
            }
        }.getOrDefault("")
    }

    val isEmulator: Boolean by lazy {
        val fp = Build.FINGERPRINT.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()
        val hardware = Build.HARDWARE.orEmpty().lowercase()
        val model = Build.MODEL.orEmpty().lowercase()
        fp.startsWith("generic") || fp.contains("emulator") || fp.contains("sdk_gphone") ||
            hardware.contains("goldfish") || hardware.contains("ranchu") || hardware == "vbox86" ||
            product.contains("sdk_gphone") || product.contains("emulator") || product.contains("simulator") || product.startsWith("sdk") ||
            model.contains("emulator") || model.contains("android sdk built for") ||
            Build.MANUFACTURER.orEmpty().contains("Genymotion", ignoreCase = true)
    }

    private fun sha256(s: String): String =
        MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
