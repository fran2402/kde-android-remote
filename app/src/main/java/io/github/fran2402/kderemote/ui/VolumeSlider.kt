package io.github.fran2402.kderemote.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * A big pill that fills up like the Google Home light slider. Drag (or tap) anywhere on it;
 * dragging is relative so the level never jumps under your thumb.
 *
 * @param level 0f..1f, or null while the PC hasn't reported its volume yet.
 */
@Composable
fun VolumeSlider(
    level: Float?,
    muted: Boolean,
    onLevelChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    var dragLevel by remember { mutableStateOf<Float?>(null) }
    val enabled = level != null
    val shown = (dragLevel ?: level ?: 0f).coerceIn(0f, 1f)
    val animated by animateFloatAsState(
        targetValue = shown,
        animationSpec = if (dragLevel != null) spring(stiffness = Spring.StiffnessHigh) else spring(),
        label = "volume",
    )
    val currentLevel by rememberUpdatedState(level)
    val currentOnChange by rememberUpdatedState(onLevelChange)
    val percent = ((dragLevel ?: level ?: 0f) * 100).roundToInt()
    val fillColor = if (muted) colors.surfaceContainerHighest else colors.primaryContainer
    val labelColor = if (muted) colors.onSurfaceVariant else colors.onPrimaryContainer

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceContainerHigh)
            .alpha(if (enabled) 1f else 0.6f)
            .semantics {
                contentDescription = "Volume"
                stateDescription = if (enabled) "$percent%" else "Unavailable"
                if (enabled) {
                    setProgress { target ->
                        currentOnChange(target.coerceIn(0f, 1f))
                        true
                    }
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    currentOnChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectHorizontalDragGestures(
                    onDragStart = { dragLevel = (currentLevel ?: 0f).coerceIn(0f, 1f) },
                    onDragEnd = { dragLevel = null },
                    onDragCancel = { dragLevel = null },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        val next = ((dragLevel ?: 0f) + amount / size.width).coerceIn(0f, 1f)
                        dragLevel = next
                        currentOnChange(next)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(animated)
                .background(fillColor),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (muted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                contentDescription = null,
                tint = if (shown > 0.5f) labelColor else colors.onSurface,
                modifier = Modifier.size(22.dp),
            )
            Text(
                text = if (enabled) "  $percent%" else "  Volume",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (shown > 0.5f) labelColor else colors.onSurface,
            )
        }
    }
}
