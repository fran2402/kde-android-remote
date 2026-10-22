package io.github.fran2402.kderemote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.max

private const val POINTER_SPEED = 1.2f
private const val SCROLL_SPEED = 0.6f

@Composable
fun TouchpadPanel(
    onMove: (Float, Float) -> Unit,
    onScroll: (Float) -> Unit,
    onClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TouchSurface(onMove, onScroll, onClick, onRightClick, Modifier.weight(1f).fillMaxHeight())
            ScrollStrip(onScroll, Modifier.width(48.dp).fillMaxHeight())
        }
        Row(Modifier.fillMaxWidth().height(56.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ExpressiveButton(
                onClick = onClick,
                contentDescription = "Left click",
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                cornerPercent = 30,
                pressedCornerPercent = 18,
            ) {
                Text("Left click", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            ExpressiveButton(
                onClick = onRightClick,
                contentDescription = "Right click",
                containerColor = colors.surfaceContainerHigh,
                contentColor = colors.onSurface,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                cornerPercent = 30,
                pressedCornerPercent = 18,
            ) {
                Text("Right click", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** One finger moves the pointer, two fingers scroll, a tap clicks and a two-finger tap right-clicks. */
@Composable
private fun TouchSurface(
    onMove: (Float, Float) -> Unit,
    onScroll: (Float) -> Unit,
    onClick: () -> Unit,
    onRightClick: () -> Unit,
    modifier: Modifier,
) {
    val move by rememberUpdatedState(onMove)
    val scroll by rememberUpdatedState(onScroll)
    val click by rememberUpdatedState(onClick)
    val rightClick by rememberUpdatedState(onRightClick)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(40.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics { contentDescription = "Touchpad" }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var maxPointers = 1
                    var travelled = 0f
                    var moving = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        maxPointers = max(maxPointers, pressed.size)
                        var delta = Offset.Zero
                        pressed.forEach { delta += it.positionChange() }
                        delta /= pressed.size.toFloat()
                        travelled += delta.getDistance()
                        if (travelled > slop) moving = true
                        if (moving) {
                            if (pressed.size >= 2) scroll(-delta.y * SCROLL_SPEED / density) else move(delta.x * POINTER_SPEED, delta.y * POINTER_SPEED)
                        }
                        event.changes.forEach { it.consume() }
                    }
                    if (!moving) {
                        if (maxPointers >= 2) rightClick() else click()
                    }
                }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            "Drag to move · Tap to click\nTwo fingers to scroll · Two-finger tap to right-click",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}

@Composable
private fun ScrollStrip(onScroll: (Float) -> Unit, modifier: Modifier) {
    val scroll by rememberUpdatedState(onScroll)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .semantics { contentDescription = "Scroll strip" }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, amount ->
                    change.consume()
                    scroll(-amount * SCROLL_SPEED / density)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Rounded.UnfoldMore, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
