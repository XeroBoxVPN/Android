package io.nekohasekai.sfa.compose.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.XMark
import io.nekohasekai.sfa.panel.ForceUpdate
import io.nekohasekai.sfa.update.UpdateState
import io.nekohasekai.sfa.vendor.Vendor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Shown instead of the whole app while the server requires a newer version (ForceUpdate).
 * The only action is installing it; the VPN stays off until then.
 */
@Composable
fun UpdateRequiredScreen(required: ForceUpdate.Required) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val progress by UpdateState.downloadProgress

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            XMark(modifier = Modifier.size(88.dp))
            Text(stringResource(R.string.force_update_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                stringResource(R.string.force_update_body, BuildConfig.VERSION_NAME, required.versionName),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            if (required.notes.isNotBlank()) {
                Text(required.notes, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (downloading) {
                val p = progress
                if (p != null) {
                    Text("${stringResource(R.string.downloading)} ${(p * 100).toInt()}%")
                    LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                } else {
                    Text(stringResource(R.string.downloading))
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center) }
            Button(
                enabled = !downloading,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    downloading = true
                    error = null
                    scope.launch {
                        try {
                            // A cached APK of another version must not be installed instead.
                            if (UpdateState.updateInfo.value?.versionCode != required.versionCode) {
                                UpdateState.cachedApkFile.value = null
                            }
                            withContext(Dispatchers.IO) { Vendor.downloadAndInstall(context, required.downloadUrl) }
                        } catch (e: Exception) {
                            error = e.message ?: e.javaClass.simpleName
                        } finally {
                            downloading = false
                            UpdateState.downloadProgress.value = null
                        }
                    }
                },
            ) {
                Text(stringResource(if (error != null) R.string.force_update_retry else R.string.force_update_button, required.versionName))
            }
            Text(
                stringResource(R.string.force_update_keep),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
