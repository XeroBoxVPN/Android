package io.nekohasekai.sfa.compose.screen.dashboard

import android.provider.Settings
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.XMark
import io.nekohasekai.sfa.compose.theme.XeroCyan
import io.nekohasekai.sfa.compose.theme.XeroMint
import io.nekohasekai.sfa.compose.theme.LocalProtectionColors
import io.nekohasekai.sfa.constant.Status

/**
 * Dashboard header that states, at a glance, whether traffic is protected.
 *
 * The connect control stays where it was (the start FAB); this card only reports
 * state, so it adds no new start path. Protected pulses softly, connecting sweeps an
 * arc; both stop when the system "remove animations" setting is on.
 */
@Composable
fun ShieldStatusCard(
    status: Status,
    profileName: String?,
    modifier: Modifier = Modifier,
) {
    val protection = LocalProtectionColors.current
    val scheme = MaterialTheme.colorScheme
    val isProtected = status == Status.Started
    val isTransitioning = status == Status.Starting || status == Status.Stopping

    val stateColor by animateColorAsState(
        targetValue = when {
            isProtected -> protection.protected
            isTransitioning -> scheme.primary
            else -> protection.unprotected
        },
        animationSpec = tween(400),
        label = "shieldColor",
    )

    val title = stringResource(
        when (status) {
            Status.Started -> R.string.shield_protected
            Status.Starting -> R.string.shield_connecting
            Status.Stopping -> R.string.shield_disconnecting
            Status.Stopped -> R.string.shield_unprotected
        },
    )
    val detail = when {
        isProtected && !profileName.isNullOrBlank() -> stringResource(R.string.shield_detail_protected, profileName)
        isProtected -> stringResource(R.string.shield_detail_protected_no_profile)
        status == Status.Starting && !profileName.isNullOrBlank() ->
            stringResource(R.string.shield_detail_connecting, profileName)
        status == Status.Starting -> stringResource(R.string.shield_detail_connecting_no_profile)
        profileName.isNullOrBlank() -> stringResource(R.string.shield_detail_no_profile)
        else -> stringResource(R.string.shield_detail_unprotected)
    }

    val shape = RoundedCornerShape(28.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        stateColor.copy(alpha = 0.22f).compositeOver(scheme.surfaceContainerHigh),
                        scheme.surfaceContainerLow,
                    ),
                    start = Offset.Zero,
                    end = Offset(900f, 600f),
                ),
            )
            .border(1.dp, stateColor.copy(alpha = 0.35f), shape)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 22.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            ShieldEmblem(
                color = stateColor,
                isProtected = isProtected,
                isTransitioning = isTransitioning,
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = scheme.onSurface,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusChip(
                    text = stringResource(if (isProtected) R.string.shield_chip_encrypted else R.string.shield_chip_open),
                    color = stateColor,
                )
            }
        }
    }
}

@Composable
private fun ShieldEmblem(
    color: Color,
    isProtected: Boolean,
    isTransitioning: Boolean,
) {
    val animate = rememberAnimationsEnabled()
    val transition = rememberInfiniteTransition(label = "shield")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2200, easing = LinearEasing), RepeatMode.Restart),
        label = "pulse",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep",
    )

    Box(modifier = Modifier.size(84.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val base = size.minDimension / 2f * 0.62f
            val maxRadius = size.minDimension / 2f
            if (isProtected && animate) {
                // Two staggered rings expanding outward.
                for (offset in listOf(0f, 0.5f)) {
                    val t = (pulse + offset) % 1f
                    drawCircle(
                        color = color.copy(alpha = (1f - t) * 0.35f),
                        radius = base + (maxRadius - base) * t,
                        center = center,
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            } else if (!isTransitioning) {
                drawCircle(
                    color = color.copy(alpha = 0.25f),
                    radius = maxRadius - 2.dp.toPx(),
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx()),
                )
            }
            if (isTransitioning) {
                val stroke = 3.dp.toPx()
                drawArc(
                    color = color,
                    startAngle = if (animate) sweep else -90f,
                    sweepAngle = 100f,
                    useCenter = false,
                    topLeft = Offset(stroke, stroke),
                    size = androidx.compose.ui.geometry.Size(size.width - stroke * 2, size.height - stroke * 2),
                    style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
        }
        // The "locking X": blades apart when open, sliding while connecting, snapped
        // together around a glowing core when protected. The title next to it says the
        // state in words, so the mark itself is decorative for screen readers.
        val breathe by transition.animateFloat(
            initialValue = 0.15f,
            targetValue = 0.85f,
            animationSpec = infiniteRepeatable(tween(650, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "breathe",
        )
        val spread by animateFloatAsState(
            targetValue = when {
                isProtected -> 0f
                isTransitioning -> if (animate) breathe else 0.5f
                else -> 1f
            },
            animationSpec = if (isProtected) spring(dampingRatio = 0.45f, stiffness = 420f) else tween(220),
            label = "spread",
        )
        val glow by animateFloatAsState(if (isProtected) 1f else 0f, tween(500), label = "glow")
        if (glow > 0f) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(
                        Brush.radialGradient(listOf(XeroCyan.copy(alpha = 0.32f * glow), Color.Transparent)),
                        CircleShape,
                    ),
            )
        }
        XMark(
            modifier = Modifier.size(52.dp),
            spread = spread,
            tint = if (isProtected) null else color.copy(alpha = if (isTransitioning) 0.95f else 0.75f),
            coreAlpha = if (isProtected) 0.95f else 0.35f,
            coreColor = if (isProtected) XeroMint else color,
        )
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = color,
        )
    }
}

/** False when the user has turned animations off in system settings. */
@Composable
private fun rememberAnimationsEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
    }
}
