package io.github.fran2402.kderemote.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import io.github.fran2402.kderemote.kdeconnect.SpecialKey

@Composable
fun DPad(size: Dp, onSpecialKey: (Int) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val arrowSize = size * 0.27f
    val inset = size * 0.02f
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Arrow(Icons.Rounded.KeyboardArrowUp, "Up", arrowSize, Modifier.align(Alignment.TopCenter).padding(top = inset)) {
            onSpecialKey(SpecialKey.UP)
        }
        Arrow(Icons.Rounded.KeyboardArrowDown, "Down", arrowSize, Modifier.align(Alignment.BottomCenter).padding(bottom = inset)) {
            onSpecialKey(SpecialKey.DOWN)
        }
        Arrow(Icons.Rounded.KeyboardArrowLeft, "Left", arrowSize, Modifier.align(Alignment.CenterStart).padding(start = inset)) {
            onSpecialKey(SpecialKey.LEFT)
        }
        Arrow(Icons.Rounded.KeyboardArrowRight, "Right", arrowSize, Modifier.align(Alignment.CenterEnd).padding(end = inset)) {
            onSpecialKey(SpecialKey.RIGHT)
        }
        ExpressiveButton(
            onClick = { onSpecialKey(SpecialKey.RETURN) },
            contentDescription = "OK",
            containerColor = colors.surfaceContainerHighest,
            contentColor = colors.onSurface,
            modifier = Modifier.size(size * 0.42f),
            pressedCornerPercent = 36,
        ) {
            Text("OK", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun Arrow(icon: ImageVector, description: String, size: Dp, modifier: Modifier, onClick: () -> Unit) {
    ExpressiveButton(
        onClick = onClick,
        contentDescription = description,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.size(size),
        repeatOnHold = true,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.45f))
    }
}
