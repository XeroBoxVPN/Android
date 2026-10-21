package io.nekohasekai.sfa.compose.screen.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.nekohasekai.sfa.R
import io.nekohasekai.sfa.compose.component.XMark
import io.nekohasekai.sfa.compose.navigation.NewProfileArgs
import io.nekohasekai.sfa.compose.topbar.LocalScaffoldPadding
import io.nekohasekai.sfa.compose.topbar.OverrideTopBar
import io.nekohasekai.sfa.constant.Status
import io.nekohasekai.sfa.panel.PanelSync
import io.nekohasekai.sfa.utils.RemoteControlManager
import kotlinx.coroutines.delay

private const val TRAFFIC_REFRESH_MS = 15_000L

data class CardRenderItem(val cards: List<CardGroup>, val isRow: Boolean)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    serviceStatus: Status = Status.Stopped,
    showStartFab: Boolean = false,
    showStatusBar: Boolean = false,
    onOpenNewProfile: (NewProfileArgs) -> Unit = {},
    viewModel: DashboardViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val panel by PanelSync.state.collectAsState()
    // Data left, refreshed every few seconds while the dashboard is on screen.
    LaunchedEffect(panel.signedIn) {
        while (panel.signedIn) {
            PanelSync.refreshTraffic()
            delay(TRAFFIC_REFRESH_MS)
        }
    }
    val remoteServer by RemoteControlManager.remoteServer.collectAsState()
    val remoteConnected by RemoteControlManager.isConnected.collectAsState()
    val isRemote = remoteServer != null
    // XeroBox: a brand header instead of "Dashboard", and no menu (the layout is fixed).
    OverrideTopBar {
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    XMark(modifier = Modifier.size(30.dp), spread = if (serviceStatus == Status.Started) 0f else 0.35f)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("XeroBox", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        panel.account?.accountId?.takeIf { panel.signedIn && it.isNotEmpty() }?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
        )
    }

    if (isRemote && !remoteConnected) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    val scaffoldPadding = LocalScaffoldPadding.current

    Box(
        modifier = Modifier.fillMaxSize(),
    ) {
        val bottomPadding = when {
            showStartFab -> 88.dp
            showStatusBar -> 74.dp
            else -> 0.dp
        }
        LazyColumn(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(scaffoldPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            // Dynamic dashboard cards
            // Show cards when service is running OR if it's the Profiles card (always available)
            val serviceRunning = uiState.isStatusVisible

            // Filter cards based on availability
            val actuallyVisibleCards =
                // XeroBox: the Debug card (core memory, goroutines) is for developers, not users.
                uiState.visibleCards.filter { it != CardGroup.Debug }.filter { cardGroup ->
                    when {
                        // The remote dashboard only renders cards backed by the
                        // command protocol: profiles and system proxy are
                        // operations on the local device.
                        isRemote ->
                            cardGroup != CardGroup.Profiles &&
                                cardGroup != CardGroup.SystemProxy &&
                                cardGroup != CardGroup.Ad &&  // Ad card is local-only
                                serviceRunning &&
                                isCardAvailableWhenServiceRunning(cardGroup, uiState)

                        cardGroup == CardGroup.Profiles -> true // Profiles card is always available
                        cardGroup == CardGroup.Ad -> isCardAvailableWhenServiceRunning(cardGroup, uiState) // Ad card available regardless of service
                        else -> serviceRunning && isCardAvailableWhenServiceRunning(cardGroup, uiState)
                    }
                }.toSet()

            // Process cards to group half-width cards together
            val cardRenderItems =
                processCardsForRendering(
                    cardOrder = uiState.cardOrder,
                    visibleCards = actuallyVisibleCards,
                )

            // Protection status header, local dashboard only
            if (!isRemote) {
                item(key = "shield-status") {
                    ShieldStatusCard(
                        status = serviceStatus,
                        profileName = uiState.profiles
                            .firstOrNull { it.id == uiState.selectedProfileId }
                            ?.name,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            // Data left on the account (3x-ui accounting on the server).
            val traffic = panel.account?.traffic
            if (!isRemote && panel.signedIn && traffic != null) {
                item(key = "traffic") { TrafficCard(traffic = traffic) }
            }

            items(cardRenderItems) { renderItem ->
                if (renderItem.isRow && renderItem.cards.size >= 2) {
                    // Render two half-width cards in a row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        renderItem.cards.forEach { cardGroup ->
                            DashboardCardRenderer(
                                cardGroup = cardGroup,
                                uiState = uiState,
                                onClashModeSelected = viewModel::selectClashMode,
                                onSystemProxyToggle = viewModel::toggleSystemProxy,
                                onAdDismiss = viewModel::dismissAd,
                                // Profile card specific props
                                profiles = uiState.profiles,
                                selectedProfileId = uiState.selectedProfileId,
                                isLoading = uiState.isLoading,
                                showAddProfileSheet = uiState.showAddProfileSheet,
                                showProfilePickerSheet = uiState.showProfilePickerSheet,
                                updatingProfileId = uiState.updatingProfileId,
                                updatedProfileId = uiState.updatedProfileId,
                                onProfileSelected = viewModel::selectProfile,
                                onProfileEdit = viewModel::editProfile,
                                onProfileDelete = viewModel::deleteProfile,
                                onProfileShare = viewModel::shareProfile,
                                onProfileShareURL = viewModel::shareProfileURL,
                                onProfileUpdate = viewModel::updateProfile,
                                onProfileMove = viewModel::moveProfile,
                                onShowAddProfileSheet = viewModel::showAddProfileSheet,
                                onHideAddProfileSheet = viewModel::hideAddProfileSheet,
                                onShowProfilePickerSheet = viewModel::showProfilePickerSheet,
                                onHideProfilePickerSheet = viewModel::hideProfilePickerSheet,
                                onOpenNewProfile = onOpenNewProfile,
                                commandClient = viewModel.commandClient,
                                modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                            )
                        }
                    }
                } else {
                    // Render single card (full-width or single half-width)
                    renderItem.cards.forEach { cardGroup ->
                        DashboardCardRenderer(
                            cardGroup = cardGroup,
                            uiState = uiState,
                            serviceStatus = serviceStatus,
                            onClashModeSelected = viewModel::selectClashMode,
                            onSystemProxyToggle = viewModel::toggleSystemProxy,
                            onAdDismiss = viewModel::dismissAd,
                            // Profile card specific props
                            profiles = uiState.profiles,
                            selectedProfileId = uiState.selectedProfileId,
                            isLoading = uiState.isLoading,
                            showAddProfileSheet = uiState.showAddProfileSheet,
                            showProfilePickerSheet = uiState.showProfilePickerSheet,
                            updatingProfileId = uiState.updatingProfileId,
                            updatedProfileId = uiState.updatedProfileId,
                            onProfileSelected = viewModel::selectProfile,
                            onProfileEdit = viewModel::editProfile,
                            onProfileDelete = viewModel::deleteProfile,
                            onProfileShare = viewModel::shareProfile,
                            onProfileShareURL = viewModel::shareProfileURL,
                            onProfileUpdate = viewModel::updateProfile,
                            onProfileMove = viewModel::moveProfile,
                            onShowAddProfileSheet = viewModel::showAddProfileSheet,
                            onHideAddProfileSheet = viewModel::hideAddProfileSheet,
                            onShowProfilePickerSheet = viewModel::showProfilePickerSheet,
                            onHideProfilePickerSheet = viewModel::hideProfilePickerSheet,
                            onOpenNewProfile = onOpenNewProfile,
                            commandClient = viewModel.commandClient,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Process cards for rendering, grouping consecutive cards of the same pair group into rows
 */
fun processCardsForRendering(
    cardOrder: List<CardGroup>,
    visibleCards: Set<CardGroup>,
): List<CardRenderItem> {
    val renderItems = mutableListOf<CardRenderItem>()
    val visibleOrderedCards = cardOrder.filter { visibleCards.contains(it) }

    var i = 0
    while (i < visibleOrderedCards.size) {
        val currentCard = visibleOrderedCards[i]
        val pairGroup = currentCard.pairGroup
        val nextCard = visibleOrderedCards.getOrNull(i + 1)

        if (pairGroup != null && nextCard?.pairGroup == pairGroup) {
            renderItems.add(
                CardRenderItem(
                    cards = listOf(currentCard, nextCard),
                    isRow = true,
                ),
            )
            i += 2
            continue
        }

        renderItems.add(
            CardRenderItem(
                cards = listOf(currentCard),
                isRow = false,
            ),
        )
        i++
    }

    return renderItems
}

/**
 * Determine if a service-dependent card has data available to display.
 * This function is only relevant when the service is running.
 * Note: Profiles card is always available and should not use this function.
 */
fun isCardAvailableWhenServiceRunning(cardGroup: CardGroup, uiState: DashboardUiState): Boolean = when (cardGroup) {
    CardGroup.Ad -> uiState.adsAllowed && !uiState.adDismissed && (uiState.adData != null || uiState.adLoading) // Ad card shows if not dismissed and has data/loading
    CardGroup.ClashMode -> uiState.clashModeVisible
    CardGroup.UploadTraffic -> uiState.trafficVisible
    CardGroup.DownloadTraffic -> uiState.trafficVisible
    CardGroup.Debug -> true // Debug info is always available when service is running
    CardGroup.Connections -> uiState.trafficVisible
    CardGroup.SystemProxy -> uiState.systemProxyVisible
    CardGroup.Profiles -> true // This shouldn't be called for Profiles, but return true for safety
}
