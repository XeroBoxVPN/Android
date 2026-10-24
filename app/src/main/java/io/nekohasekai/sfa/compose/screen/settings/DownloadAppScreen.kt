package io.nekohasekai.sfa.compose.screen.settings

import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import io.nekohasekai.sfa.BuildConfig
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.panel.PanelSync

/**
 * A QR code for the official download page, so another phone can scan it and get the app.
 * The address comes from the panel (dashboard → Settings → App links), so it can change without
 * a new app; until it is set, the build's XEROBOX_DOWNLOAD_URL is used, which defaults to a
 * clearly marked placeholder (an address under the reserved .invalid domain).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadAppScreen(navController: NavController) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.download_title)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.content_description_back))
                }
            },
        )
    }
    LaunchedEffect(Unit) { PanelSync.refreshLinks() }
    val state by PanelSync.state.collectAsState()
    val url = downloadUrl(state.links.downloadUrl)
    val placeholder = isPlaceholder(url)
    val qr = remember(url) { qrBitmap(url, 720) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LocalScaffoldPadding.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(
                modifier = Modifier.padding(20.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(stringResource(R.string.download_heading), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.download_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                // Always dark modules on white, so it scans in dark mode too.
                Box(
                    modifier = Modifier
                        .size(260.dp)
                        .background(Color.White, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) {
                        Image(
                            bitmap = qr.asImageBitmap(),
                            contentDescription = null,
                            filterQuality = FilterQuality.None,
                            modifier = Modifier.fillMaxSize().semantics { contentDescription = "QR code: $url" },
                        )
                    }
                }
                SelectionContainer {
                    Text(url, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace), textAlign = TextAlign.Center)
                }
                if (placeholder) {
                    Text(
                        stringResource(R.string.download_placeholder),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { clipboard.setText(AnnotatedString(url)) },
                        enabled = !placeholder,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.download_copy)) }
                    FilledTonalButton(
                        onClick = {
                            val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, context.getString(R.string.download_share_text, url))
                            runCatching { context.startActivity(Intent.createChooser(send, null)) }
                        },
                        enabled = !placeholder,
                        modifier = Modifier.weight(1f),
                    ) { Text(stringResource(R.string.download_share)) }
                }
            }
        }
    }
}

/** The panel's address when set, else the build's (a placeholder until configured). */
internal fun downloadUrl(fromPanel: String): String = fromPanel.ifEmpty { BuildConfig.DOWNLOAD_URL }

/** Placeholder addresses use the reserved .invalid domain and never resolve. */
internal fun isPlaceholder(url: String): Boolean =
    runCatching { android.net.Uri.parse(url).host.orEmpty().endsWith(".invalid") }.getOrDefault(true)

private fun qrBitmap(text: String, size: Int): Bitmap? = runCatching {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size) { i -> if (matrix.get(i % size, i / size)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt() }
    Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}.getOrNull()
