package io.github.fran2402.kderemote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.DesktopWindows
import androidx.compose.material.icons.rounded.Laptop
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.TabletAndroid
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.fran2402.kderemote.kdeconnect.Device

fun deviceIcon(type: String?): ImageVector = when (type) {
    "desktop" -> Icons.Rounded.DesktopWindows
    "laptop" -> Icons.Rounded.Laptop
    "tv" -> Icons.Rounded.Tv
    "phone" -> Icons.Rounded.PhoneAndroid
    "tablet" -> Icons.Rounded.TabletAndroid
    else -> Icons.Rounded.Computer
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSheet(
    devices: List<Device>,
    selectedId: String?,
    onSelect: (Device) -> Unit,
    onPair: (Device) -> Unit,
    onRefresh: () -> Unit,
    onAddByAddress: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var askAddress by remember { mutableStateOf(false) }
    val paired = devices.filter { it.paired }
    val available = devices.filter { !it.paired && it.reachable }
    val colors = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.surfaceContainer,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Devices", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text("Through KDE Connect on your network", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                }
                FilledTonalIconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Search again")
                }
            }
            Spacer(Modifier.height(14.dp))

            if (paired.isEmpty() && available.isEmpty()) {
                Text(
                    "No PCs found yet. Open KDE Connect on your PC and make sure both devices are on the same Wi-Fi.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp),
                )
            }

            paired.forEachIndexed { index, device ->
                DeviceRow(
                    device = device,
                    shape = groupShape(index, paired.size),
                    selected = device.id == selectedId,
                    status = if (device.reachable) "Connected" else "Offline",
                    onClick = { onSelect(device) },
                )
            }

            if (available.isNotEmpty()) {
                Text(
                    "Available to pair",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.primary,
                    modifier = Modifier.padding(start = 8.dp, top = 18.dp, bottom = 6.dp),
                )
                available.forEachIndexed { index, device ->
                    DeviceRow(
                        device = device,
                        shape = groupShape(index, available.size),
                        selected = false,
                        status = device.address ?: "On this network",
                        onClick = { onPair(device) },
                        trailing = {
                            FilledTonalButton(onClick = { onPair(device) }) { Text("Pair") }
                        },
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { askAddress = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("Add device by IP")
            }
        }
    }

    if (askAddress) {
        var address by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { askAddress = false },
            title = { Text("Add device by IP") },
            text = {
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("PC address, e.g. 192.168.1.20") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddByAddress(address)
                        askAddress = false
                    },
                    enabled = address.isNotBlank(),
                ) { Text("Connect") }
            },
            dismissButton = { TextButton(onClick = { askAddress = false }) { Text("Cancel") } },
        )
    }
}

private fun groupShape(index: Int, count: Int): RoundedCornerShape {
    val outer = 24.dp
    val inner = 6.dp
    return RoundedCornerShape(
        topStart = if (index == 0) outer else inner,
        topEnd = if (index == 0) outer else inner,
        bottomStart = if (index == count - 1) outer else inner,
        bottomEnd = if (index == count - 1) outer else inner,
    )
}

@Composable
private fun DeviceRow(
    device: Device,
    shape: RoundedCornerShape,
    selected: Boolean,
    status: String,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = shape,
        color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
        contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
        modifier = Modifier.fillMaxWidth().height(76.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(if (selected) RoundedCornerShape(14.dp) else CircleShape)
                    .background(if (selected) colors.primary else colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    deviceIcon(device.type),
                    contentDescription = null,
                    tint = if (selected) colors.onPrimary else colors.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(
                    status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                )
            }
            when {
                trailing != null -> trailing()
                selected -> Icon(Icons.Rounded.Check, contentDescription = "Selected")
            }
        }
    }
}
