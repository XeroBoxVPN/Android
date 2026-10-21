package io.nekohasekai.sfa.compose.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.base.GlobalEventBus
import io.nekohasekai.sfa.compose.base.UiEvent
import io.nekohasekai.sfa.database.RemoteServer
import io.nekohasekai.sfa.utils.RemoteControlManager
import kotlinx.coroutines.launch

const val REMOTE_CONTROL_ROUTE = "settings/remote_control"

// Remote control is removed from XeroBox: no servers, so every remote-control menu stays hidden.
@Composable
fun rememberRemoteServers(): State<List<RemoteServer>> = remember { mutableStateOf(emptyList()) }

@Composable
fun RemoteControlMenuItems(servers: List<RemoteServer>, onAction: () -> Unit, leadingDivider: Boolean = true) {
    val scope = rememberCoroutineScope()
    val remoteServer by RemoteControlManager.remoteServer.collectAsState()

    if (servers.isEmpty()) {
        return
    }

    if (leadingDivider) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    }
    Text(
        text = stringResource(R.string.remote_control),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
    SelectableMenuItem(
        label = stringResource(R.string.remote_local_device),
        selected = remoteServer == null,
        onClick = {
            onAction()
            RemoteControlManager.exitRemoteControl()
        },
    )
    servers.forEach { server ->
        val isActive = remoteServer?.id == server.id
        SelectableMenuItem(
            label = server.displayName,
            selected = isActive,
            onClick = {
                onAction()
                if (!isActive) {
                    RemoteControlManager.enterRemoteControl(server)
                }
            },
        )
    }
    DropdownMenuItem(
        text = { Text(stringResource(R.string.remote_manage_servers)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Outlined.Tune,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        onClick = {
            onAction()
            scope.launch {
                GlobalEventBus.emit(UiEvent.Navigate(REMOTE_CONTROL_ROUTE))
            }
        },
    )
}

@Composable
private fun SelectableMenuItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        leadingIcon = {
            Icon(
                imageVector =
                if (selected) {
                    Icons.Default.RadioButtonChecked
                } else {
                    Icons.Default.RadioButtonUnchecked
                },
                contentDescription = null,
                tint =
                if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        onClick = onClick,
    )
}
