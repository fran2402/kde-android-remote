package io.github.fran2402.kderemote.ui

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * A Material 3 Expressive style button: it squishes and morphs its corners while pressed.
 * With [repeatOnHold], holding the button keeps firing [onClick] (for the D-pad arrows).
 */
@Composable
fun ExpressiveButton(
    onClick: () -> Unit,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    cornerPercent: Int = 50,
    pressedCornerPercent: Int = 28,
    repeatOnHold: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateIntAsState(
        targetValue = if (pressed) pressedCornerPercent else cornerPercent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "corner",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "scale",
    )
    val view = LocalView.current
    val currentOnClick by rememberUpdatedState(onClick)
    val click = {
        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        currentOnClick()
    }
    val scope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(corner))
            .background(containerColor)
            .indication(interactionSource, ripple())
            .semantics {
                role = Role.Button
                this.contentDescription = contentDescription
                // Explicit receiver: plain `onClick` would resolve to this composable's parameter.
                this.onClick(label = null) {
                    click()
                    true
                }
            }
            .pointerInput(repeatOnHold) {
                detectTapGestures(
                    onPress = { offset ->
                        val press = PressInteraction.Press(offset)
                        interactionSource.emit(press)
                        val repeater = if (repeatOnHold) {
                            click()
                            scope.launch {
                                delay(400)
                                while (true) {
                                    currentOnClick()
                                    delay(90)
                                }
                            }
                        } else {
                            null
                        }
                        val released = tryAwaitRelease()
                        repeater?.cancel()
                        interactionSource.emit(if (released) PressInteraction.Release(press) else PressInteraction.Cancel(press))
                        if (released && !repeatOnHold) click()
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            content()
        }
    }
}

/** The scalloped "cookie" shape from the Material 3 Expressive shape set. */
class CookieShape(private val lobes: Int, private val amplitude: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2
        val cy = size.height / 2
        val radius = min(cx, cy) / (1 + amplitude)
        val steps = lobes * 24
        val path = Path()
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val r = radius * (1 + amplitude * cos(lobes * t)).toFloat()
            val x = cx + r * cos(t - PI / 2).toFloat()
            val y = cy + r * sin(t - PI / 2).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}
