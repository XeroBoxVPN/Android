package io.nekohasekai.sfa.compose.screen.welcome

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.XMark
import io.nekohasekai.sfa.compose.screen.settings.LOCAL_NETWORK_PERMISSION
import io.nekohasekai.sfa.compose.screen.settings.SignInCard
import io.nekohasekai.sfa.compose.screen.settings.needsLocalNetworkPermission
import io.nekohasekai.sfa.panel.GuestOffer
import io.nekohasekai.sfa.panel.GuestPlan
import io.nekohasekai.sfa.panel.PanelState
import io.nekohasekai.sfa.panel.PanelSync
import kotlinx.coroutines.launch

/**
 * The first screen when signed out: Enter Code (an access code bought in Telegram, an
 * invitation or a login code) or Continue as Guest (try a plan, panel guest.go).
 */
@Composable
fun WelcomeScreen(panel: PanelState) {
    val context = LocalContext.current
    var step by rememberSaveable { mutableStateOf(STEP_HOME) }
    var planId by rememberSaveable { mutableStateOf("") }
    var offer by remember { mutableStateOf<GuestOffer?>(null) }
    var offerError by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun loadOffer() {
        loading = true
        offerError = null
        scope.launch {
            val (o, why) = PanelSync.guestOffer()
            offer = o ?: offer
            offerError = why
            loading = false
        }
    }
    LaunchedEffect(Unit) { loadOffer() }
    val telegram = offer?.telegramUrl.orEmpty().ifEmpty { panel.links.telegramUrl }

    BackHandler(enabled = step != STEP_HOME) {
        step = if (step == STEP_CONFIRM) STEP_PLANS else STEP_HOME
    }

    WelcomeFrame {
        when (step) {
            STEP_HOME -> {
                XMark(modifier = Modifier.size(96.dp))
                Text(stringResource(R.string.panel_welcome_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                Text(
                    stringResource(R.string.welcome_tagline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                ChoiceCard(Icons.Default.Key, stringResource(R.string.welcome_enter_code), stringResource(R.string.welcome_enter_code_sub), primary = true) { step = STEP_CODE }
                ChoiceCard(Icons.Default.PersonOutline, stringResource(R.string.welcome_guest), stringResource(R.string.welcome_guest_sub), primary = false) {
                    step = STEP_PLANS
                    if (offer == null) loadOffer()
                }
                if (telegram.isNotEmpty()) {
                    TextButton(onClick = { openLink(context, telegram) }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.welcome_no_code))
                    }
                }
            }

            STEP_CODE -> {
                BackRow { step = STEP_HOME }
                SignInCard(
                    panel,
                    title = stringResource(R.string.welcome_code_title),
                    body = stringResource(R.string.welcome_code_body),
                    label = stringResource(R.string.welcome_code_label),
                )
                TelegramButton(telegram, context)
            }

            STEP_PLANS -> {
                BackRow { step = STEP_HOME }
                Text(stringResource(R.string.guest_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(stringResource(R.string.guest_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                val o = offer
                when {
                    loading && o == null -> CircularProgressIndicator(modifier = Modifier.padding(24.dp))
                    o == null -> {
                        Text(offerError.orEmpty(), color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                        OutlinedButton(onClick = { loadOffer() }) { Text(stringResource(R.string.guest_retry)) }
                    }
                    !o.enabled -> Unavailable(o.message, telegram, context) { step = STEP_CODE }
                    else -> o.plans.forEach { p ->
                        PlanCard(p) {
                            planId = p.id
                            step = STEP_CONFIRM
                        }
                    }
                }
            }

            STEP_CONFIRM -> {
                val p = offer?.plans?.firstOrNull { it.id == planId }
                if (p == null) {
                    LaunchedEffect(Unit) { step = STEP_PLANS }
                } else {
                    BackRow { step = STEP_PLANS }
                    Confirm(p, onOther = { step = STEP_PLANS })
                }
            }
        }
    }
}

private const val STEP_HOME = "home"
private const val STEP_CODE = "code"
private const val STEP_PLANS = "plans"
private const val STEP_CONFIRM = "confirm"

@Composable
internal fun WelcomeFrame(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding()
            .imePadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) { content() }
    }
}

@Composable
private fun BackRow(onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.welcome_back))
        }
    }
}

@Composable
private fun ChoiceCard(icon: ImageVector, title: String, sub: String, primary: Boolean, onClick: () -> Unit) {
    val container = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val onContainer = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = onContainer),
    ) {
        Row(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).background(onContainer.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null) }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = onContainer.copy(alpha = 0.75f))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        }
    }
}

@Composable
private fun TelegramButton(url: String, context: Context) {
    if (url.isEmpty()) return
    OutlinedButton(onClick = { openLink(context, url) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.welcome_get_in_telegram))
    }
}

@Composable
private fun Unavailable(message: String, telegram: String, context: Context, onEnterCode: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.guest_unavailable_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (telegram.isNotEmpty()) {
                Button(onClick = { openLink(context, telegram) }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.welcome_get_in_telegram))
                }
            }
            OutlinedButton(onClick = onEnterCode, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text(stringResource(R.string.welcome_enter_code))
            }
        }
    }
}

private fun planColor(hex: String, fallback: Color): Color =
    runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(fallback)

@Composable
private fun PlanCard(p: GuestPlan, onClick: () -> Unit) {
    val accent = planColor(p.color, MaterialTheme.colorScheme.primary)
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).background(accent, CircleShape))
                Spacer(Modifier.width(10.dp))
                Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    "${p.length} · ${p.data}",
                    style = MaterialTheme.typography.labelLarge,
                    color = accent,
                    modifier = Modifier.background(accent.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            if (p.description.isNotBlank()) {
                Text(p.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            p.features.drop(2).forEach { Feature(it) } // the first two (data, length) are in the chip
            if (p.priceFrom.isNotEmpty()) {
                Text(stringResource(R.string.guest_then_from, p.priceFrom), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.guest_try, p.name), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun Feature(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Stat(icon: ImageVector, label: String, value: String, modifier: Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Confirm(p: GuestPlan, onOther: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun start() {
        busy = true
        error = null
        scope.launch {
            error = PanelSync.startGuest(p.id) // success: signed in, the app opens by itself
            busy = false
        }
    }
    // Android 17+: a server on the local network needs the user's consent first.
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { start() }

    Text(stringResource(R.string.guest_confirm_title, p.name), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    Text(
        stringResource(R.string.guest_trial_chip),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 4.dp),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        Stat(Icons.Default.Schedule, stringResource(R.string.guest_length), p.length, Modifier.weight(1f))
        Stat(Icons.Default.SignalCellularAlt, stringResource(R.string.guest_data), p.data, Modifier.weight(1f))
    }
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        p.features.drop(2).forEach { Feature(it) }
    }
    Text(
        stringResource(R.string.guest_confirm_body, p.length, p.name, p.data),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    Button(
        onClick = {
            if (needsLocalNetworkPermission(context, PanelSync.builtInServerUrl)) permissionLauncher.launch(LOCAL_NETWORK_PERMISSION) else start()
        },
        enabled = !busy,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(10.dp))
        }
        Text(stringResource(R.string.guest_start))
    }
    TextButton(onClick = onOther, enabled = !busy) { Text(stringResource(R.string.guest_other_plan)) }
}

/** Shown instead of the app when a guest trial has ended: get a plan in Telegram, then enter its code here. */
@Composable
fun GuestEndedScreen(panel: PanelState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun redeem() {
        if (busy || code.isBlank()) return
        busy = true
        error = null
        scope.launch {
            val (_, why) = PanelSync.redeemCode(code)
            error = why // success: the account is a normal one again and this screen goes away
            busy = false
        }
    }

    WelcomeFrame {
        XMark(modifier = Modifier.size(88.dp))
        Text(stringResource(R.string.guest_ended_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(stringResource(R.string.guest_ended_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Button(
            onClick = {
                scope.launch {
                    // A link that also links this account to the bot, else the bot's public link.
                    val (url, _) = PanelSync.telegramLink()
                    (url ?: panel.links.telegramUrl).takeIf { !it.isNullOrEmpty() }?.let { openLink(context, it) }
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.welcome_get_in_telegram))
        }
        OutlinedTextField(
            value = code,
            onValueChange = { v ->
                code = v.uppercase().filter { it.isLetterOrDigit() || it == '-' }.take(32)
                error = null
            },
            label = { Text(stringResource(R.string.guest_ended_code_label)) },
            singleLine = true,
            enabled = !busy,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Characters,
                keyboardType = KeyboardType.Password,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { redeem() }),
            modifier = Modifier.fillMaxWidth(),
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        OutlinedButton(onClick = { redeem() }, enabled = !busy && code.isNotBlank(), modifier = Modifier.fillMaxWidth().height(52.dp)) {
            if (busy) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(stringResource(R.string.guest_ended_redeem))
        }
        TextButton(onClick = { scope.launch { PanelSync.signOut() } }) { Text(stringResource(R.string.guest_ended_sign_out)) }
    }
}

private fun openLink(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
