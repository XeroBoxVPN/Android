package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.theme.LocalProtectionColors
import io.nekohasekai.sfa.compose.util.TrafficFormat
import io.nekohasekai.sfa.panel.PanelTraffic

/** The account's data allowance on the dashboard: what is left, and a usage bar. */
@Composable
fun TrafficCard(traffic: PanelTraffic, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DataUsage,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.traffic_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            TrafficMeter(traffic)
        }
    }
}

/** Remaining data in large type, the used share as a bar, and a hint when it ran out. */
@Composable
fun TrafficMeter(traffic: PanelTraffic, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val warning = LocalProtectionColors.current.unprotected // amber, as for "Not protected"
    val fraction by animateFloatAsState(traffic.usedFraction, label = "trafficUsed")
    if (traffic.unlimited) {
        Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.traffic_unlimited),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = stringResource(R.string.traffic_unlimited_used, TrafficFormat.bytes(traffic.usedBytes)),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }
        return
    }
    val exhausted = traffic.remainingBytes <= 0
    val barColor = when {
        exhausted -> colors.error
        fraction >= 0.8f -> warning
        else -> colors.primary
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = TrafficFormat.bytes(traffic.remainingBytes),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (exhausted) colors.error else colors.onSurface,
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.traffic_left),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            color = barColor,
            trackColor = colors.outlineVariant.copy(alpha = 0.55f), // visible on light cards too
            drawStopIndicator = {},
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50)),
        )
        Text(
            text = stringResource(R.string.traffic_used_of, TrafficFormat.bytes(traffic.usedBytes), TrafficFormat.bytes(traffic.totalBytes)),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )
        when {
            exhausted -> Text(
                text = stringResource(R.string.traffic_exhausted),
                style = MaterialTheme.typography.bodySmall,
                color = colors.error,
            )
            fraction >= 0.8f -> Text(
                text = stringResource(R.string.traffic_low),
                style = MaterialTheme.typography.bodySmall,
                color = warning,
            )
        }
    }
}
