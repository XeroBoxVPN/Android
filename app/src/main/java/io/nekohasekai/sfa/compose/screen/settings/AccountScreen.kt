package io.nekohasekai.sfa.compose.screen.settings

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.screen.dashboard.TrafficCard
import io.nekohasekai.sfa.compose.theme.LocalProtectionColors
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.panel.PanelAccount
import io.nekohasekai.sfa.panel.PanelPlan
import io.nekohasekai.sfa.panel.PanelState
import io.nekohasekai.sfa.panel.PanelSync
import kotlinx.coroutines.launch
import java.net.InetAddress
import java.net.URI
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(navController: NavController) {
    OverrideTopBar {
        TopAppBar(
            title = { Text(stringResource(R.string.panel_account)) },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.content_description_back),
                    )
                }
            },
        )
    }

    val state by PanelSync.state.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(LocalScaffoldPadding.current)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (state.signedIn) {
            SignedInContent(state, navController)
        } else {
            SignInCard(state)
        }
    }
}

/**
 * Login or invitation code entry. The server address is built into the app
 * ([PanelSync.builtInServerUrl]) and never shown. Also used by the first-launch screen.
 */
@Composable
fun SignInCard(state: PanelState, title: String = stringResource(R.string.panel_sign_in_title)) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var busy by rememberSaveable { mutableStateOf(false) }
    // A sign-out caused by the server (revoked token) explains itself here.
    var error by rememberSaveable { mutableStateOf(state.lastError) }
    val localNetworkDenied = stringResource(R.string.panel_local_network_denied)

    fun signIn() {
        busy = true
        error = null
        scope.launch {
            error = PanelSync.login(code)
            busy = false
            if (error == null) code = ""
        }
    }

    // Android 17+: a server on the local network (the emulator's 10.0.2.2, a LAN address)
    // is unreachable until the user grants local network access.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) signIn() else error = localNetworkDenied
    }

    val submit: () -> Unit = submit@{
        if (busy || code.isBlank()) return@submit
        if (needsLocalNetworkPermission(context, PanelSync.builtInServerUrl)) {
            permissionLauncher.launch(LOCAL_NETWORK_PERMISSION)
        } else {
            signIn()
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.AccountCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = stringResource(R.string.panel_sign_in_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = code,
                onValueChange = { value ->
                    code = value.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(32)
                    error = null
                },
                label = { Text(stringResource(R.string.panel_login_or_invite_code)) },
                placeholder = { Text(stringResource(R.string.panel_login_code_hint)) },
                singleLine = true,
                enabled = !busy,
                textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Button(
                onClick = submit,
                enabled = !busy && code.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(stringResource(R.string.panel_sign_in))
            }
        }
    }
}

private const val LOCAL_NETWORK_PERMISSION = "android.permission.ACCESS_LOCAL_NETWORK"

/** Whether [serverUrl] is on the local network and Android 17+ still needs the user's consent for it. */
private fun needsLocalNetworkPermission(context: Context, serverUrl: String): Boolean {
    if (Build.VERSION.SDK_INT < 37) return false
    if (ContextCompat.checkSelfPermission(context, LOCAL_NETWORK_PERMISSION) == PackageManager.PERMISSION_GRANTED) return false
    val host = runCatching { URI(serverUrl).host }.getOrNull()?.trim('[', ']') ?: return false
    if (host.endsWith(".local", ignoreCase = true)) return true
    // Only literal addresses are checked, so this never does a DNS lookup on the main thread.
    val literal = Regex("""\d{1,3}(\.\d{1,3}){3}""").matches(host) || ':' in host
    if (!literal) return false
    val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return false
    return address.isSiteLocalAddress || address.isLinkLocalAddress || address.isLoopbackAddress
}

@Composable
private fun SignedInContent(state: PanelState, navController: NavController) {
    val scope = rememberCoroutineScope()
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }
    var signingOut by rememberSaveable { mutableStateOf(false) }
    val account = state.account
    val protection = LocalProtectionColors.current
    val statusColor = when (account?.status) {
        null, PanelAccount.STATUS_ACTIVE -> protection.protected
        else -> protection.unprotected
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(statusColor.copy(alpha = 0.16f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = account?.name?.firstOrNull()?.uppercase() ?: "?",
                        style = MaterialTheme.typography.titleLarge,
                        color = statusColor,
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = account?.name?.ifEmpty { null } ?: stringResource(R.string.panel_account),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(statusColor, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = statusLabel(account?.status),
                            style = MaterialTheme.typography.labelLarge,
                            color = statusColor,
                        )
                    }
                }
            }

            state.lastError?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(stringResource(R.string.panel_account_id), account?.accountId?.ifEmpty { null } ?: "—", monospace = true)
            InfoRow(stringResource(R.string.panel_expires), formatExpiry(account?.plan?.takeIf { it.paid }?.expiresAt ?: account?.expiresAt))
            InfoRow(stringResource(R.string.panel_configs), state.managedProfiles.toString())
            InfoRow(
                stringResource(R.string.panel_last_sync),
                if (state.lastSync > 0) {
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(state.lastSync))
                } else {
                    stringResource(R.string.panel_not_synced)
                },
            )
        }
    }

    account?.plan?.let { PlanCard(it, state.links, navController) }

    account?.traffic?.let { TrafficCard(traffic = it) }

    if (state.loginCode.isNotEmpty()) {
        LoginCodeCard(state.loginCode)
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        FilledTonalButton(
            onClick = { PanelSync.syncInBackground() },
            enabled = !state.syncing && !signingOut,
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            if (state.syncing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(if (state.syncing) R.string.panel_syncing else R.string.panel_sync_now))
        }
        OutlinedButton(
            onClick = { confirmSignOut = true },
            enabled = !signingOut,
            modifier = Modifier.weight(1f).height(52.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.panel_sign_out))
        }
    }

    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var deleteError by rememberSaveable { mutableStateOf<String?>(null) }
    TextButton(
        onClick = { confirmDelete = true },
        enabled = !signingOut,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(R.string.delete_account), color = MaterialTheme.colorScheme.error)
    }
    deleteError?.let { Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.delete_account_title)) },
            text = { Text(stringResource(R.string.delete_account_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    signingOut = true
                    scope.launch {
                        deleteError = PanelSync.deleteAccount()
                        signingOut = false
                    }
                }) { Text(stringResource(R.string.delete_account_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.panel_cancel)) }
            },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text(stringResource(R.string.panel_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.panel_sign_out_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    signingOut = true
                    scope.launch {
                        PanelSync.signOut()
                        signingOut = false
                    }
                }) { Text(stringResource(R.string.panel_sign_out)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmSignOut = false }) { Text(stringResource(R.string.panel_cancel)) }
            },
        )
    }
}

/** The plan, and the way to buy or renew one: the Telegram bot, linked to this account in one tap. */
@Composable
private fun PlanCard(plan: PanelPlan, links: io.nekohasekai.sfa.panel.PanelLinks, navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val noTelegram = stringResource(R.string.plan_no_telegram)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.WorkspacePremium,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = plan.name.ifEmpty { stringResource(R.string.plan_title) },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (plan.paid) stringResource(R.string.plan_paid_until, formatExpiry(plan.expiresAt)) else stringResource(R.string.plan_free),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = stringResource(R.string.plan_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            error?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { navController.navigate("settings/redeem") },
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) { Text(stringResource(R.string.plan_enter_code)) }
            // Where to get a code: the panel's purchase page or Telegram link; without them, the
            // Telegram bot (one-time link to this account).
            val buy = links.purchaseUrl.ifEmpty { links.telegramUrl }
            OutlinedButton(
                onClick = {
                    if (buy.isNotEmpty()) {
                        openLink(context, buy)
                        return@OutlinedButton
                    }
                    busy = true
                    error = null
                    scope.launch {
                        val (url, err) = PanelSync.telegramLink()
                        busy = false
                        if (url == null) {
                            error = err
                        } else {
                            val opened = runCatching {
                                context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                            }.isSuccess
                            if (!opened) error = noTelegram
                        }
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                }
                Text(stringResource(if (plan.paid) R.string.plan_renew else R.string.plan_get_code))
            }
        }
    }
}

/** The account's own login code, hidden until tapped, so it can be used on other devices. */
@Composable
private fun LoginCodeCard(code: String) {
    val clipboard = LocalClipboardManager.current
    var shown by rememberSaveable { mutableStateOf(false) }
    var copied by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.panel_your_login_code), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.panel_your_login_code_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (shown) code else code.map { if (it == '-') '-' else '•' }.joinToString(""),
                    style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { shown = !shown }) {
                    Text(stringResource(if (shown) R.string.panel_hide else R.string.panel_show))
                }
                TextButton(onClick = {
                    clipboard.setText(AnnotatedString(code))
                    copied = true
                }) {
                    Text(stringResource(if (copied) R.string.panel_copied else R.string.panel_copy))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, monospace: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium.let { if (monospace) it.copy(fontFamily = FontFamily.Monospace) else it },
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.4f, fill = false),
        )
    }
}

@Composable
private fun statusLabel(status: String?): String = when (status) {
    null, PanelAccount.STATUS_ACTIVE -> stringResource(R.string.panel_status_active)
    "suspended" -> stringResource(R.string.panel_status_suspended)
    "expired" -> stringResource(R.string.panel_status_expired)
    else -> status.replaceFirstChar { it.uppercase() }
}

@Composable
private fun formatExpiry(value: String?): String {
    if (value.isNullOrBlank()) return stringResource(R.string.panel_never)
    return try {
        // RFC 3339; only the date part is shown.
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date = parser.parse(value.take(10)) ?: return value
        DateFormat.getDateInstance(DateFormat.MEDIUM).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(date) // the date as stored, not shifted a day by the phone's time zone
    } catch (_: Exception) {
        value
    }
}
