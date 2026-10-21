package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.util.TrafficFormat
import io.nekohasekai.sfa.model.AdData
import io.nekohasekai.sfa.panel.PanelSync
import kotlinx.coroutines.delay

private sealed interface RewardStep {
    data object Starting : RewardStep
    data class Watching(val token: String, val seconds: Int) : RewardStep
    data object Claiming : RewardStep
    data class Done(val creditedMb: Long) : RewardStep
    data class Failed(val message: String) : RewardStep
}

/**
 * Shows a rewarded ad for the time the server asks, then claims the reward. The server
 * starts the clock when the view starts and refuses an early claim, so closing early
 * simply forfeits the reward.
 */
@Composable
fun RewardedAdDialog(ad: AdData, onDismiss: () -> Unit) {
    var step by remember { mutableStateOf<RewardStep>(RewardStep.Starting) }
    var left by remember { mutableIntStateOf(0) }

    // One coroutine for the whole flow: keying it on the step would cancel the claim
    // the moment the step leaves Watching.
    LaunchedEffect(ad.id) {
        val (view, error) = PanelSync.startAdView(ad.id)
        if (view == null) {
            step = RewardStep.Failed(error ?: "")
            return@LaunchedEffect
        }
        val w = RewardStep.Watching(view.token, view.seconds)
        step = w
        for (s in w.seconds downTo 1) {
            left = s
            delay(1_000)
        }
        left = 0
        delay(300) // a little past the server's clock
        step = RewardStep.Claiming
        val (reward, claimError) = PanelSync.claimAdReward(w.token)
        step = if (reward == null) RewardStep.Failed(claimError ?: "") else RewardStep.Done(reward.creditedMb)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(dismissOnClickOutside = false, usePlatformDefaultWidth = false),
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)),
            ) {
                if (ad.imageUrl.isNotBlank()) {
                    AdImage(imageUrl = ad.imageUrl, modifier = Modifier.matchParentSize())
                } else {
                    AdHeroArt(modifier = Modifier.matchParentSize())
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ad.title?.let {
                    Text(it, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                }
                ad.subtitle?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                Spacer(modifier = Modifier.height(4.dp))
                when (val s = step) {
                    RewardStep.Starting, RewardStep.Claiming -> {
                        CircularProgressIndicator(modifier = Modifier.size(40.dp))
                        Text(
                            text = stringResource(if (s == RewardStep.Claiming) R.string.ad_reward_claiming else R.string.ad_reward_starting),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    is RewardStep.Watching -> {
                        val progress by animateFloatAsState(
                            targetValue = 1f - left.toFloat() / s.seconds,
                            animationSpec = tween(durationMillis = 1_000, easing = LinearEasing),
                            label = "rewardProgress",
                        )
                        Box(contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(56.dp), strokeWidth = 5.dp)
                            Text("$left", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = stringResource(R.string.ad_reward_watching, TrafficFormat.megabytes(ad.rewardMb)),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                        )
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.ad_reward_leave)) }
                    }
                    is RewardStep.Done -> {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                        Text(
                            text = stringResource(R.string.ad_reward_done, TrafficFormat.megabytes(s.creditedMb)),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ad_reward_close)) }
                    }
                    is RewardStep.Failed -> {
                        Text(
                            text = s.message.ifEmpty { stringResource(R.string.ad_reward_failed) },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ad_reward_close)) }
                    }
                }
            }
        }
    }
}
