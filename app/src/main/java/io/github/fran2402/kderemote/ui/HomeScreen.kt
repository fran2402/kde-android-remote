package io.github.fran2402.kderemote.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.fran2402.kderemote.home.HomeAccess
import io.github.fran2402.kderemote.home.HomeLights
import io.github.fran2402.kderemote.home.Light
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

private val whiteKelvins = listOf(2200, 2700, 3000, 4000, 5000, 6500)
private val colorHues = listOf(0f, 25f, 45f, 120f, 175f, 205f, 240f, 280f, 320f)

@Composable
fun HomeTab(homeLights: HomeLights?, modifier: Modifier = Modifier) {
    if (homeLights == null) {
        Message(
            "Google Home isn't available",
            "This phone doesn't have the Google Play services the Home APIs need.",
            modifier,
        )
        return
    }
    val access by homeLights.access.collectAsStateWithLifecycle()
    val lights by homeLights.lights.collectAsStateWithLifecycle()
    var openId by rememberSaveable { mutableStateOf<String?>(null) }

    when (access) {
        HomeAccess.Checking -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        HomeAccess.NotGranted -> Column(
            modifier.padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Box(
                Modifier.size(160.dp).clip(CookieShape(lobes = 9, amplitude = 0.06f)).background(MaterialTheme.colorScheme.tertiaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Lightbulb, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(64.dp))
            }
            Text("Control your lights", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(
                "Give this app access to your Google Home to switch, dim and recolour your lights.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = homeLights::requestAccess, modifier = Modifier.height(56.dp)) {
                Text("Connect Google Home", style = MaterialTheme.typography.titleMedium)
            }
        }
        HomeAccess.Granted -> if (lights.isEmpty()) {
            Message("No lights yet", "Lights you've added in the Google Home app show up here.", modifier)
        } else {
            LightList(lights, onToggle = homeLights::setOn, onOpen = { openId = it.id }, modifier = modifier)
        }
    }

    val open = lights.firstOrNull { it.id == openId }
    if (open != null) {
        LightDetail(open, homeLights, onClose = { openId = null })
    }
}

@Composable
private fun Message(title: String, body: String, modifier: Modifier) {
    Column(
        modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
    }
}

@Composable
private fun LightList(lights: List<Light>, onToggle: (Light, Boolean) -> Unit, onOpen: (Light) -> Unit, modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lights.groupBy { it.room }.forEach { (room, inRoom) ->
            Text(
                room ?: "Other lights",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 8.dp, top = 16.dp, bottom = 6.dp),
            )
            inRoom.forEachIndexed { index, light ->
                LightCard(
                    light,
                    shape = RoundedCornerShape(
                        topStart = if (index == 0) 28.dp else 8.dp,
                        topEnd = if (index == 0) 28.dp else 8.dp,
                        bottomStart = if (index == inRoom.lastIndex) 28.dp else 8.dp,
                        bottomEnd = if (index == inRoom.lastIndex) 28.dp else 8.dp,
                    ),
                    onToggle = { onToggle(light, it) },
                    onOpen = { onOpen(light) },
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun LightCard(light: Light, shape: RoundedCornerShape, onToggle: (Boolean) -> Unit, onOpen: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val glow = lightColor(light)
    val container = if (light.isOn) lerp(colors.surfaceContainerHigh, glow, 0.22f) else colors.surfaceContainerHigh
    Surface(onClick = onOpen, shape = shape, color = container, modifier = Modifier.fillMaxWidth().height(80.dp)) {
        Row(
            Modifier.padding(start = 16.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(if (light.isOn) glow else colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Lightbulb,
                    null,
                    tint = if (light.isOn) Color.Black.copy(alpha = 0.75f) else colors.onSurfaceVariant,
                )
            }
            Column(Modifier.weight(1f)) {
                Text(light.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    when {
                        !light.isOn -> "Off"
                        light.level != null -> "On · ${(light.level * 100).roundToInt()}%"
                        else -> "On"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
            Switch(checked = light.isOn, onCheckedChange = onToggle)
        }
    }
}

/** Full-screen light control, modelled on the Google Home app: a big brightness pill and colour chips. */
@Composable
private fun LightDetail(light: Light, homeLights: HomeLights, onClose: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    var showColors by rememberSaveable(light.id) { mutableStateOf(light.supportsColor && light.colorTemperatureMireds == null) }
    val sendLevel = rememberThrottled { homeLights.setLevel(light, it) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize(), color = colors.surface) {
            Column(Modifier.fillMaxSize().systemBarsPadding().padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, contentDescription = "Close") }
                    Text(
                        light.name,
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Switch(checked = light.isOn, onCheckedChange = { homeLights.setOn(light, it) })
                }

                Box(Modifier.weight(1f).fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                    if (light.level != null) {
                        BrightnessPill(
                            level = if (light.isOn) light.level else 0f,
                            color = lightColor(light),
                            onLevelChange = sendLevel,
                            modifier = Modifier.width(180.dp).fillMaxHeight().heightIn(max = 420.dp),
                        )
                    } else {
                        Icon(Icons.Rounded.Lightbulb, null, modifier = Modifier.size(96.dp), tint = if (light.isOn) lightColor(light) else colors.outline)
                    }
                    if (light.supportsColor && light.supportsTemperature) {
                        Surface(
                            onClick = { showColors = !showColors },
                            shape = CircleShape,
                            color = colors.surfaceContainerHigh,
                            modifier = Modifier.align(Alignment.BottomStart).size(72.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (showColors) Icons.Rounded.WbSunny else Icons.Rounded.Palette,
                                    contentDescription = if (showColors) "Show white tones" else "Show colours",
                                )
                            }
                        }
                    }
                }

                when {
                    light.supportsColor && (showColors || !light.supportsTemperature) -> SwatchGrid(
                        swatches = colorHues.map { Color.hsv(it, 0.85f, 1f) },
                        selected = light.hue?.takeIf { light.colorTemperatureMireds == null || showColors }?.let { hue ->
                            colorHues.indices.minByOrNull { hueDistance(colorHues[it], hue) }
                        },
                        onSelect = { homeLights.setColor(light, colorHues[it], 1f) },
                    )
                    light.supportsTemperature -> SwatchGrid(
                        swatches = whiteKelvins.map { kelvinColor(it) },
                        selected = light.colorTemperatureMireds?.let { mireds ->
                            whiteKelvins.indices.minByOrNull { abs(1_000_000 / whiteKelvins[it] - mireds) }
                        },
                        onSelect = { homeLights.setColorTemperature(light, 1_000_000 / whiteKelvins[it]) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SwatchGrid(swatches: List<Color>, selected: Int?, onSelect: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        swatches.indices.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { index ->
                    val isSelected = index == selected
                    val corner by animateDpAsState(if (isSelected) 40.dp else 22.dp, label = "swatch")
                    Box(
                        Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(corner))
                            .background(swatches[index])
                            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(corner))
                            .pointerInput(index) { detectTapGestures { onSelect(index) } }
                            .semantics { contentDescription = if (isSelected) "Selected colour" else "Colour ${index + 1}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) Icon(Icons.Rounded.Check, null, tint = Color.Black.copy(alpha = 0.8f))
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** A tall pill that fills from the bottom; drag up or down anywhere on it. */
@Composable
private fun BrightnessPill(level: Float, color: Color, onLevelChange: (Float) -> Unit, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    var dragLevel by remember { mutableStateOf<Float?>(null) }
    val shown = (dragLevel ?: level).coerceIn(0f, 1f)
    val animated by animateFloatAsState(shown, label = "brightness")
    val currentLevel by rememberUpdatedState(level)
    val change by rememberUpdatedState(onLevelChange)
    val dark = colors.surface.luminance() < 0.5f
    val fill = if (dark) lerp(Color.Black, color, 0.38f) else lerp(Color.White, color, 0.6f)
    val label = if (dark) color else Color.Black.copy(alpha = 0.8f)
    val percent = (shown * 100).roundToInt()

    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(colors.surfaceContainerHigh)
            .semantics {
                contentDescription = "Brightness"
                stateDescription = "$percent%"
                setProgress { change(it.coerceIn(0f, 1f)); true }
            }
            .pointerInput(Unit) {
                detectTapGestures { offset -> change((1f - offset.y / size.height).coerceIn(0.01f, 1f)) }
            }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragStart = { dragLevel = currentLevel.coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragLevel?.let(change)
                        dragLevel = null
                    },
                    onDragCancel = { dragLevel = null },
                    onVerticalDrag = { pointer, amount ->
                        pointer.consume()
                        val next = ((dragLevel ?: 0f) - amount / size.height).coerceIn(0.01f, 1f)
                        dragLevel = next
                        change(next)
                    },
                )
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(Modifier.fillMaxWidth().fillMaxHeight(animated).background(fill))
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icons.Rounded.Lightbulb, null, tint = if (shown > 0.45f) label else colors.onSurface, modifier = Modifier.size(32.dp))
            Text(
                if (percent == 0) "Off" else "$percent%",
                style = MaterialTheme.typography.headlineSmall,
                color = if (shown > 0.45f) label else colors.onSurface,
            )
        }
    }
}

/** Sends at most one value per [intervalMs], always ending with the latest one. */
@Composable
private fun rememberThrottled(intervalMs: Long = 150, action: (Float) -> Unit): (Float) -> Unit {
    val channel = remember { Channel<Float>(Channel.CONFLATED) }
    val current by rememberUpdatedState(action)
    LaunchedEffect(channel) {
        for (value in channel) {
            current(value)
            delay(intervalMs)
        }
    }
    return remember(channel) { { value: Float -> channel.trySend(value) } }
}

private fun lightColor(light: Light): Color = when {
    light.supportsColor && light.colorTemperatureMireds == null && light.hue != null ->
        Color.hsv(light.hue, (light.saturation ?: 1f).coerceIn(0f, 1f) * 0.85f, 1f)
    light.colorTemperatureMireds != null && light.colorTemperatureMireds > 0 -> kelvinColor(1_000_000 / light.colorTemperatureMireds)
    else -> kelvinColor(2700)
}

/** Approximate colour of white light at a colour temperature, from warm amber to cool blue-white. */
private fun kelvinColor(kelvin: Int): Color {
    val stops = listOf(
        2200 to Color(0xFFFFC14D),
        2700 to Color(0xFFFFD873),
        3000 to Color(0xFFFFE9A6),
        4000 to Color(0xFFFFF6DE),
        5000 to Color(0xFFFFFFFF),
        6500 to Color(0xFFEFFAFF),
    )
    if (kelvin <= stops.first().first) return stops.first().second
    if (kelvin >= stops.last().first) return stops.last().second
    val upper = stops.indexOfFirst { it.first >= kelvin }
    val (k0, c0) = stops[upper - 1]
    val (k1, c1) = stops[upper]
    return lerp(c0, c1, (kelvin - k0).toFloat() / (k1 - k0))
}

private fun hueDistance(a: Float, b: Float): Float {
    val d = abs(a - b) % 360f
    return if (d > 180f) 360f - d else d
}
