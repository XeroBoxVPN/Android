package io.nekohasekai.sfa.compose.screen.dashboard

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.theme.SecureGradientEnd
import io.nekohasekai.sfa.compose.theme.SecureGradientStart
import io.nekohasekai.sfa.compose.util.TrafficFormat
import io.nekohasekai.sfa.config.AdConfig
import io.nekohasekai.sfa.ext.Extensions
import io.nekohasekai.sfa.model.AdData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

/**
 * Native-style sponsored card in the XeroBox look: a hero artwork band (the ad's image,
 * or built-in generated artwork), a "Sponsored" pill, title, one line of copy and a
 * call-to-action button. No SDK, no tracking.
 */
@Composable
fun AdCard(
    adData: AdData?,
    isLoading: Boolean,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Don't render if no ad data and not loading
    if (adData == null && !isLoading) return

    // ADD-ON HOOK: the ad became visible (once per ad while the card is on screen).
    if (adData != null) {
        LaunchedEffect(adData.id) { Extensions.onAdShown(adData) }
    }

    val openLink: () -> Unit = {
        val url = adData?.clickUrl.orEmpty()
        if (url.startsWith("https://", ignoreCase = true)) {
            // ADD-ON HOOK: the user tapped the ad.
            adData?.let { Extensions.onAdClicked(it) }
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            } catch (_: Exception) {
                // No browser available: nothing sensible to do.
            }
        }
    }
    val hasLink = adData?.clickUrl?.startsWith("https://", ignoreCase = true) == true
    val scheme = MaterialTheme.colorScheme
    var watching by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        if (adData == null) {
            AdSkeleton()
            return@Card
        }
        Column(modifier = Modifier.clickable(enabled = hasLink, onClick = openLink)) {
            // Hero band
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)),
            ) {
                if (adData.imageUrl.isNotBlank()) {
                    AdImage(imageUrl = adData.imageUrl, modifier = Modifier.matchParentSize())
                } else {
                    AdHeroArt(modifier = Modifier.matchParentSize())
                }
                // Scrim so the pill and close button read on any image
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.28f),
                                0.45f to Color.Transparent,
                            ),
                        ),
                )
                Text(
                    text = stringResource(R.string.ad_sponsored),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.35f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
                if (onDismiss != null) {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(50))
                                .background(Color.Black.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.ad_dismiss),
                                tint = Color.White,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }

            // Copy + call to action
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 18.dp, end = 14.dp, top = 14.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    adData.title?.let { title ->
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = scheme.onSurface,
                        )
                    }
                    adData.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                if (hasLink) {
                    FilledTonalButton(
                        onClick = openLink,
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = scheme.primaryContainer,
                            contentColor = scheme.onPrimaryContainer,
                        ),
                    ) {
                        Text(
                            text = adData.cta ?: stringResource(R.string.ad_learn_more),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
            if (adData.rewardMb > 0) {
                Button(
                    onClick = { watching = true },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                ) {
                    Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.ad_watch_to_earn, TrafficFormat.megabytes(adData.rewardMb)))
                }
            }
        }
    }
    if (watching && adData != null) {
        RewardedAdDialog(ad = adData, onDismiss = { watching = false })
    }
}

/** Generated artwork for ads without an image: the XeroBox void with cyan and violet glows. */
@Composable
internal fun AdHeroArt(modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val glowA = scheme.primary
    val glowB = scheme.secondary
    val accent = scheme.tertiary
    Canvas(
        modifier = modifier.background(
            Brush.linearGradient(listOf(SecureGradientStart, SecureGradientEnd)),
        ),
    ) {
        val w = size.width
        val h = size.height
        drawCircle(
            brush = Brush.radialGradient(
                listOf(glowA.copy(alpha = 0.55f), Color.Transparent),
                center = Offset(w * 0.82f, h * 0.30f),
                radius = h * 0.95f,
            ),
            radius = h * 0.95f,
            center = Offset(w * 0.82f, h * 0.30f),
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(glowB.copy(alpha = 0.40f), Color.Transparent),
                center = Offset(w * 0.12f, h * 1.05f),
                radius = h * 0.9f,
            ),
            radius = h * 0.9f,
            center = Offset(w * 0.12f, h * 1.05f),
        )
        // Flowing "signal" lines
        for (i in 0 until 4) {
            val y = h * (0.55f + i * 0.11f)
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, y)
                cubicTo(w * 0.3f, y - h * 0.22f, w * 0.6f, y + h * 0.18f, w, y - h * 0.10f)
            }
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.10f - i * 0.018f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
            )
        }
        // Accent spark
        drawCircle(color = accent.copy(alpha = 0.9f), radius = 3.dp.toPx(), center = Offset(w * 0.72f, h * 0.62f))
        drawCircle(color = Color.White.copy(alpha = 0.7f), radius = 2.dp.toPx(), center = Offset(w * 0.30f, h * 0.38f))
    }
}

/** Placeholder while the first server ad loads (only when there is no built-in ad). */
@Composable
private fun AdSkeleton() {
    val tone = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .background(tone),
        )
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.width(140.dp).height(14.dp).clip(RoundedCornerShape(7.dp)).background(tone))
            Box(Modifier.width(220.dp).height(12.dp).clip(RoundedCornerShape(6.dp)).background(tone))
        }
    }
}

/**
 * Simple image loader that fetches and displays an image from URL.
 * Lightweight alternative to Coil/Glide for this single use case.
 */
@Composable
internal fun AdImage(
    imageUrl: String,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(imageUrl) { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember(imageUrl) { mutableStateOf(true) }
    var hasError by remember(imageUrl) { mutableStateOf(false) }

    LaunchedEffect(imageUrl) {
        isLoading = true
        hasError = false
        val loaded = withContext(Dispatchers.IO) { loadAdBitmap(imageUrl) }
        bitmap = loaded
        hasError = loaded == null
        isLoading = false
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 1.5.dp
                )
            }
            bitmap != null -> {
                Image(
                    bitmap = bitmap!!.asImageBitmap(),
                    contentDescription = "Ad image",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }
            hasError -> {
                // Show placeholder on error
                Icon(
                    imageVector = Icons.Default.Campaign,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

/** Largest image download accepted, so a bad ad server can't exhaust memory. */
private const val MAX_AD_IMAGE_BYTES = 2 * 1024 * 1024

/** The card shows the image at 64dp; this leaves headroom for high-density screens. */
private const val AD_IMAGE_TARGET_PX = 256

/**
 * Fetches and decodes an ad image, or returns null on any failure.
 *
 * https only: Android blocks cleartext traffic by default, so an http URL would just
 * fail with a less obvious error. The image is downsampled to roughly
 * [AD_IMAGE_TARGET_PX] before decoding the full bitmap.
 */
private fun loadAdBitmap(imageUrl: String): android.graphics.Bitmap? {
    if (!imageUrl.startsWith("https://", ignoreCase = true)) return null
    AdImageCache.get(imageUrl)?.let { return it }
    return downloadAdBitmap(imageUrl)?.also { AdImageCache.put(imageUrl, it) }
}

/**
 * Process-wide cache of the last ad image, so the periodic ad refresh and the card
 * leaving and re-entering the screen don't re-download an unchanged image.
 * Holds one entry: the card only ever shows one ad.
 */
private object AdImageCache {
    private var url: String? = null
    private var bitmap: android.graphics.Bitmap? = null
    private var storedAt = 0L

    @Synchronized
    fun get(imageUrl: String): android.graphics.Bitmap? {
        val fresh = System.currentTimeMillis() - storedAt < AdConfig.IMAGE_CACHE_DURATION_MS
        return if (imageUrl == url && fresh) bitmap else null
    }

    @Synchronized
    fun put(imageUrl: String, image: android.graphics.Bitmap) {
        url = imageUrl
        bitmap = image
        storedAt = System.currentTimeMillis()
    }
}

private fun downloadAdBitmap(imageUrl: String): android.graphics.Bitmap? {
    return try {
        val connection = URL(imageUrl).openConnection()
        connection.connectTimeout = 5000
        connection.readTimeout = 5000
        val bytes = connection.inputStream.use { input ->
            val buffer = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val read = input.read(chunk)
                if (read < 0) break
                if (buffer.size() + read > MAX_AD_IMAGE_BYTES) return null
                buffer.write(chunk, 0, read)
            }
            buffer.toByteArray()
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= AD_IMAGE_TARGET_PX &&
            bounds.outHeight / (sampleSize * 2) >= AD_IMAGE_TARGET_PX
        ) {
            sampleSize *= 2
        }
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    } catch (_: Exception) {
        null
    }
}
