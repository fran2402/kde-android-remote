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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.fran2402.kderemote.kdeconnect.AudioOutput

/** Picks the PC's default audio output (speakers, headphones, HDMI…). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OutputSheet(outputs: List<AudioOutput>, onSelect: (AudioOutput) -> Unit, onDismiss: () -> Unit) {
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
            Text(
                "Audio output",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp, bottom = 14.dp),
            )
            if (outputs.isEmpty()) {
                Text(
                    "The PC hasn't reported any outputs. Check that Remote system volume is enabled for this phone in KDE Connect.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = colors.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
            outputs.forEachIndexed { index, output ->
                val selected = output.isDefault
                Surface(
                    onClick = { onSelect(output) },
                    shape = RoundedCornerShape(
                        topStart = if (index == 0) 24.dp else 6.dp,
                        topEnd = if (index == 0) 24.dp else 6.dp,
                        bottomStart = if (index == outputs.lastIndex) 24.dp else 6.dp,
                        bottomEnd = if (index == outputs.lastIndex) 24.dp else 6.dp,
                    ),
                    color = if (selected) colors.primaryContainer else colors.surfaceContainerHigh,
                    contentColor = if (selected) colors.onPrimaryContainer else colors.onSurface,
                    modifier = Modifier.fillMaxWidth().height(72.dp),
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
                            Icon(outputIcon(output), null, tint = if (selected) colors.onPrimary else colors.onSurfaceVariant)
                        }
                        Text(
                            output.description,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Current output")
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

private fun outputIcon(output: AudioOutput): ImageVector {
    val text = (output.description + " " + output.name).lowercase()
    return when {
        "headphone" in text || "headset" in text || "bluez" in text -> Icons.Rounded.Headphones
        "hdmi" in text || "displayport" in text -> Icons.Rounded.Tv
        else -> Icons.Rounded.Speaker
    }
}
