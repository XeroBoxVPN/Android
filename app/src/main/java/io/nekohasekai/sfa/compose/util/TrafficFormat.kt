package io.nekohasekai.sfa.compose.util

import java.util.Locale

/**
 * Traffic sizes the way the XeroBox server counts them (1 GB = 1024 MB), so the app and
 * the dashboard show the same numbers.
 */
object TrafficFormat {
    private const val MB = 1L shl 20
    private const val GB = 1L shl 30

    fun bytes(n: Long): String = when {
        n >= GB -> String.format(Locale.US, "%.2f GB", n.toDouble() / GB).replace(".00 ", " ")
        n >= MB -> "${n / MB} MB"
        n >= 1024 -> "${n / 1024} KB"
        else -> "$n B"
    }

    fun megabytes(mb: Long): String = bytes(mb * MB)
}
