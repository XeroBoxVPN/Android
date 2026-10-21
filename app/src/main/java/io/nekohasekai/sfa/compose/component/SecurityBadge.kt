package io.nekohasekai.sfa.compose.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.compose.theme.SecureGreen
import io.nekohasekai.sfa.compose.theme.SecureGreenDark
import io.nekohasekai.sfa.compose.theme.ServiceStopped

/**
 * A security badge that conveys connection status.
 * Designed to build user trust with clear visual feedback.
 */
@Composable
fun SecurityBadge(
    isConnected: Boolean,
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    size: SecurityBadgeSize = SecurityBadgeSize.Medium
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (isConnected) SecureGreen.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
        animationSpec = tween(300),
        label = "badge_bg"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isConnected) SecureGreen else ServiceStopped,
        animationSpec = tween(300),
        label = "badge_icon"
    )

    val iconSize = when (size) {
        SecurityBadgeSize.Small -> 16.dp
        SecurityBadgeSize.Medium -> 20.dp
        SecurityBadgeSize.Large -> 28.dp
    }

    val padding = when (size) {
        SecurityBadgeSize.Small -> 6.dp
        SecurityBadgeSize.Medium -> 10.dp
        SecurityBadgeSize.Large -> 14.dp
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(backgroundColor)
            .padding(horizontal = padding, vertical = padding / 2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Default.Shield else Icons.Default.LockOpen,
            contentDescription = if (isConnected) "Connected securely" else "Not connected",
            tint = iconColor,
            modifier = Modifier.size(iconSize)
        )

        if (showLabel) {
            Text(
                text = if (isConnected) "Secure" else "Not Protected",
                style = when (size) {
                    SecurityBadgeSize.Small -> MaterialTheme.typography.labelSmall
                    SecurityBadgeSize.Medium -> MaterialTheme.typography.labelMedium
                    SecurityBadgeSize.Large -> MaterialTheme.typography.labelLarge
                },
                fontWeight = FontWeight.Medium,
                color = iconColor
            )
        }
    }
}

enum class SecurityBadgeSize {
    Small,
    Medium,
    Large
}

/**
 * A shield icon with a glowing effect when connected.
 */
@Composable
fun SecureShieldIcon(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val glowAlpha by animateFloatAsState(
        targetValue = if (isConnected) 0.6f else 0f,
        animationSpec = tween(500),
        label = "glow"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isConnected) SecureGreen else ServiceStopped,
        animationSpec = tween(300),
        label = "shield_color"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Glow effect
        if (isConnected) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .alpha(glowAlpha)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                SecureGreen.copy(alpha = 0.4f),
                                Color.Transparent
                            )
                        ),
                        shape = CircleShape
                    )
            )
        }

        Icon(
            imageVector = Icons.Default.VerifiedUser,
            contentDescription = if (isConnected) "Secure connection" else "No connection",
            tint = iconColor,
            modifier = Modifier.size(32.dp)
        )
    }
}

/**
 * A pill-shaped status indicator with gradient background.
 */
@Composable
fun ConnectionStatusPill(
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val gradientColors = if (isConnected) {
        listOf(SecureGreen, SecureGreenDark)
    } else {
        listOf(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.surfaceVariant
        )
    }

    val textColor by animateColorAsState(
        targetValue = if (isConnected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(300),
        label = "pill_text"
    )

    Row(
        modifier = modifier
            .shadow(if (isConnected) 4.dp else 0.dp, RoundedCornerShape(24.dp))
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.horizontalGradient(gradientColors))
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = if (isConnected) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(18.dp)
        )

        Text(
            text = if (isConnected) "Protected" else "Unprotected",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = textColor
        )
    }
}
