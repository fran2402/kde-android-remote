package io.github.fran2402.kderemote.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fran2402.kderemote.kdeconnect.Device
import io.github.fran2402.kderemote.kdeconnect.KdeConnectManager
import io.github.fran2402.kderemote.kdeconnect.SpecialKey
import kotlinx.coroutines.flow.collectLatest

private enum class InputMode(val label: String) { DPad("D-pad"), Touchpad("Touchpad"), Keyboard("Keyboard") }

/** The KDE Remote home key: the user's Arch setup maps the backtick key to "home". */
private const val HOME_KEY = "`"

@Composable
fun RemoteScreen(manager: KdeConnectManager) {
    val devices by manager.devices.collectAsStateWithLifecycle()
    val selectedId by manager.selectedDeviceId.collectAsStateWithLifecycle()
    val pairing by manager.pairing.collectAsStateWithLifecycle()
    val volume by manager.volume.collectAsStateWithLifecycle()
    val isPlaying by manager.isPlaying.collectAsStateWithLifecycle()
    val selected = devices.firstOrNull { it.id == selectedId }
    val hasPairedDevice = devices.any { it.paired }

    var mode by rememberSaveable { mutableStateOf(InputMode.DPad) }
    var showDevices by rememberSaveable { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val colors = MaterialTheme.colorScheme

    LaunchedEffect(manager) {
        manager.messages.collectLatest { msg ->
            val result = snackbar.showSnackbar(
                message = msg.text,
                actionLabel = msg.actionLabel,
                duration = if (msg.action != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) msg.action?.invoke()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        containerColor = colors.surface,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
        ) {
            TopBar(
                device = selected,
                onDevices = { showDevices = true },
                onPower = manager::suspendComputer,
                onRefresh = manager::refresh,
                onUnpair = selected?.takeIf { it.paired }?.let { device -> { manager.unpair(device.id) } },
            )
            Spacer(Modifier.height(12.dp))

            if (!hasPairedDevice) {
                Onboarding(onChoose = { showDevices = true }, modifier = Modifier.weight(1f).fillMaxWidth())
                return@Column
            }

            ModeSelector(mode, onSelect = { mode = it })
            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = mode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                modifier = Modifier.weight(1f).fillMaxWidth(),
                label = "mode",
            ) { current ->
                when (current) {
                    InputMode.DPad -> BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        DPad(size = minOf(maxWidth, maxHeight, 340.dp), onSpecialKey = manager::sendSpecialKey)
                    }
                    InputMode.Touchpad -> TouchpadPanel(
                        onMove = manager::moveCursor,
                        onScroll = manager::scroll,
                        onClick = manager::click,
                        onRightClick = manager::rightClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                    InputMode.Keyboard -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                        KeyboardPanel(onText = manager::sendKey, onSpecialKey = manager::sendSpecialKey)
                    }
                }
            }

            if (mode != InputMode.Keyboard) {
                Spacer(Modifier.height(20.dp))
                ActionRow(
                    isPlaying = isPlaying,
                    onBack = { manager.sendSpecialKey(SpecialKey.ESCAPE) },
                    onHome = { manager.sendKey(HOME_KEY) },
                    onPlayPause = manager::playPause,
                )
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth().height(64.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val muted = volume?.muted == true
                    ExpressiveButton(
                        onClick = manager::toggleMute,
                        contentDescription = if (muted) "Unmute" else "Mute",
                        containerColor = if (muted) colors.tertiaryContainer else colors.surfaceContainerHigh,
                        contentColor = if (muted) colors.onTertiaryContainer else colors.onSurface,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Icon(if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp, null)
                    }
                    VolumeSlider(
                        level = volume?.level,
                        muted = muted,
                        onLevelChange = manager::setVolume,
                        modifier = Modifier.weight(1f).height(64.dp),
                    )
                }
            }
        }
    }

    if (showDevices) {
        DeviceSheet(
            devices = devices,
            selectedId = selectedId,
            onSelect = { device ->
                manager.selectDevice(device.id)
                showDevices = false
            },
            onPair = { device ->
                manager.selectDevice(device.id)
                manager.requestPairing(device.id)
                showDevices = false
            },
            onRefresh = manager::refresh,
            onAddByAddress = manager::addDeviceByAddress,
            onDismiss = { showDevices = false },
        )
    }

    pairing?.let { state ->
        PairingScreen(
            state = state,
            deviceType = devices.firstOrNull { it.id == state.deviceId }?.type,
            onAccept = manager::acceptPairing,
            onCancel = manager::cancelPairing,
        )
    }
}

@Composable
private fun TopBar(
    device: Device?,
    onDevices: () -> Unit,
    onPower: () -> Unit,
    onRefresh: () -> Unit,
    onUnpair: (() -> Unit)?,
) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    val (status, statusColor) = when {
        device == null -> "Tap to choose a PC" to colors.onSurfaceVariant
        device.connected -> "Connected" to colors.primary
        device.paired -> "Offline" to colors.outline
        else -> "Not paired" to colors.outline
    }

    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            onClick = onDevices,
            shape = RoundedCornerShape(28.dp),
            color = colors.surfaceContainer,
            modifier = Modifier.weight(1f).height(56.dp),
        ) {
            Row(
                Modifier.padding(start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(colors.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(deviceIcon(device?.type), null, tint = colors.onPrimaryContainer, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        device?.name ?: "No PC selected",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                        Text(status, style = MaterialTheme.typography.labelMedium, color = statusColor)
                    }
                }
                Icon(Icons.Rounded.ExpandMore, contentDescription = "Choose device", tint = colors.onSurfaceVariant)
            }
        }
        IconButton(onClick = onPower, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Rounded.PowerSettingsNew, contentDescription = "Suspend PC")
        }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Rounded.MoreVert, contentDescription = "More options")
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Search for devices") },
                    onClick = {
                        menu = false
                        onRefresh()
                    },
                )
                if (onUnpair != null) {
                    DropdownMenuItem(
                        text = { Text("Unpair ${device?.name}") },
                        onClick = {
                            menu = false
                            onUnpair()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeSelector(mode: InputMode, onSelect: (InputMode) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val modes = InputMode.entries
    Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        modes.forEachIndexed { index, item ->
            val selected = item == mode
            // Connected button group: the selected segment rounds off fully, the others keep tight inner corners.
            val full = 24.dp
            val inner = 8.dp
            val start by animateDpAsState(if (selected || index == 0) full else inner, label = "start")
            val end by animateDpAsState(if (selected || index == modes.lastIndex) full else inner, label = "end")
            Surface(
                onClick = { onSelect(item) },
                shape = RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end),
                color = if (selected) colors.secondaryContainer else colors.surfaceContainer,
                contentColor = if (selected) colors.onSecondaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.weight(1f).height(48.dp),
            ) {
                Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (selected) {
                        Icon(Icons.Rounded.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(item.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ActionRow(isPlaying: Boolean, onBack: () -> Unit, onHome: () -> Unit, onPlayPause: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ExpressiveButton(
            onClick = onBack,
            contentDescription = "Back",
            containerColor = colors.surfaceContainerHigh,
            contentColor = colors.onSurface,
            modifier = Modifier.size(64.dp),
        ) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, null)
        }
        ExpressiveButton(
            onClick = onHome,
            contentDescription = "Home",
            containerColor = colors.primary,
            contentColor = colors.onPrimary,
            modifier = Modifier.size(96.dp),
            pressedCornerPercent = 34,
        ) {
            Icon(Icons.Rounded.Home, null, modifier = Modifier.size(36.dp))
        }
        ExpressiveButton(
            onClick = onPlayPause,
            contentDescription = if (isPlaying) "Pause" else "Play",
            containerColor = colors.tertiaryContainer,
            contentColor = colors.onTertiaryContainer,
            modifier = Modifier.size(64.dp),
            cornerPercent = 30,
            pressedCornerPercent = 50,
        ) {
            Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
        }
    }
}

@Composable
private fun Onboarding(onChoose: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(160.dp).clip(CookieShape(lobes = 9, amplitude = 0.06f)).background(colors.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Home, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(64.dp))
        }
        Text(
            "Pair with your PC",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            "Open KDE Connect on your PC, make sure both devices are on the same Wi-Fi, then pick it from the list.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onChoose, modifier = Modifier.height(56.dp)) {
            Text("Choose device", style = MaterialTheme.typography.titleMedium)
        }
    }
}
