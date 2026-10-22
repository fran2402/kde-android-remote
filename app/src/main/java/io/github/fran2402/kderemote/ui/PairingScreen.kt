package io.github.fran2402.kderemote.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.fran2402.kderemote.kdeconnect.PairingState

@Composable
fun PairingScreen(state: PairingState, deviceType: String?, onAccept: () -> Unit, onCancel: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val spin = rememberInfiniteTransition(label = "spin")
    val rotation by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing), RepeatMode.Restart),
        label = "rotation",
    )

    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.surface) {
            Column(
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(200.dp), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(200.dp)
                            .graphicsLayer { rotationZ = rotation }
                            .clip(CookieShape(lobes = 9, amplitude = 0.06f))
                            .background(colors.primaryContainer),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.PhoneAndroid, null, tint = colors.onPrimaryContainer, modifier = Modifier.size(40.dp))
                        Icon(Icons.Rounded.SyncAlt, null, tint = colors.primary, modifier = Modifier.size(28.dp))
                        Icon(deviceIcon(deviceType), null, tint = colors.onPrimaryContainer, modifier = Modifier.size(48.dp))
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (state.incoming) "${state.deviceName} wants to pair" else "Pair with ${state.deviceName}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        if (state.incoming) {
                            "Make sure this code matches the one shown on your PC."
                        } else {
                            "Accept the request in the KDE Connect notification on your PC. Both screens should show the same code."
                        },
                        style = MaterialTheme.typography.bodyLarge,
                        color = colors.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(colors.surfaceContainer)
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("Verification code", style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant)
                    Text(
                        state.verificationKey.chunked(4).joinToString(" "),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 30.sp,
                        letterSpacing = 4.sp,
                        color = colors.tertiary,
                    )
                }

                if (!state.incoming) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        LinearProgressIndicator(Modifier.size(width = 160.dp, height = 6.dp))
                        Text("Waiting for the PC to accept…", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }

                Spacer(Modifier.weight(1f))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = onCancel, modifier = Modifier.weight(1f).height(56.dp)) {
                        Text(if (state.incoming) "Reject" else "Cancel", style = MaterialTheme.typography.titleMedium)
                    }
                    if (state.incoming) {
                        Button(onClick = onAccept, modifier = Modifier.weight(1.4f).height(56.dp)) {
                            Text("Accept", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
