package io.github.fran2402.kderemote.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.fran2402.kderemote.kdeconnect.KeyModifiers
import io.github.fran2402.kderemote.kdeconnect.SpecialKey

private enum class Modifier4(val label: String) { Ctrl("Ctrl"), Alt("Alt"), Shift("Shift"), Super("Super") }

/** Off, armed for the next key only, or locked until tapped again. */
private enum class Latch { Off, Once, Locked }

private val specialKeys: List<Pair<String, Int>> = listOf(
    "Esc" to SpecialKey.ESCAPE,
    "Tab" to SpecialKey.TAB,
    "⌫" to SpecialKey.BACKSPACE,
    "Del" to SpecialKey.DELETE,
    "Enter" to SpecialKey.RETURN,
    "←" to SpecialKey.LEFT,
    "↑" to SpecialKey.UP,
    "↓" to SpecialKey.DOWN,
    "→" to SpecialKey.RIGHT,
    "Home" to SpecialKey.HOME,
    "End" to SpecialKey.END,
    "PgUp" to SpecialKey.PAGE_UP,
    "PgDn" to SpecialKey.PAGE_DOWN,
) + (1..12).map { "F$it" to SpecialKey.function(it) }

/**
 * Live keyboard: every key typed into the field goes straight to the PC. The field asks the
 * on-screen keyboard for "password" input, which turns off suggestions and word composition, so
 * keys arrive one at a time instead of whole words being retyped as the IME corrects them.
 */
@Composable
fun KeyboardPanel(
    onKey: (String, KeyModifiers) -> Unit,
    onSpecialKey: (Int, KeyModifiers) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    var latches by remember { mutableStateOf(Modifier4.entries.associateWith { Latch.Off }) }
    val focus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme

    fun current() = KeyModifiers(
        ctrl = latches[Modifier4.Ctrl] != Latch.Off,
        alt = latches[Modifier4.Alt] != Latch.Off,
        shift = latches[Modifier4.Shift] != Latch.Off,
        superKey = latches[Modifier4.Super] != Latch.Off,
    )

    /** Uses the held modifiers for one key, then releases the one-shot ones. */
    fun consumeModifiers(): KeyModifiers = current().also {
        latches = latches.mapValues { (_, latch) -> if (latch == Latch.Once) Latch.Off else latch }
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Modifier4.entries.forEachIndexed { index, mod ->
                val latch = latches[mod] ?: Latch.Off
                ModifierKey(
                    label = mod.label,
                    latch = latch,
                    shape = RoundedCornerShape(
                        topStart = if (index == 0) 24.dp else 8.dp,
                        bottomStart = if (index == 0) 24.dp else 8.dp,
                        topEnd = if (index == Modifier4.entries.lastIndex) 24.dp else 8.dp,
                        bottomEnd = if (index == Modifier4.entries.lastIndex) 24.dp else 8.dp,
                    ),
                    onTap = {
                        latches = latches + (mod to if (latch == Latch.Off) Latch.Once else Latch.Off)
                    },
                    onLongPress = { latches = latches + (mod to Latch.Locked) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Text(
            "Tap a modifier for the next key, hold it to lock. Super alone is in the row below.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp),
        )

        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                val common = text.commonPrefixWith(new)
                repeat(text.length - common.length) { onSpecialKey(SpecialKey.BACKSPACE, consumeModifiers()) }
                new.substring(common.length).forEach { ch ->
                    if (ch == '\n') onSpecialKey(SpecialKey.RETURN, consumeModifiers()) else onKey(ch.toString(), consumeModifiers())
                }
                // Keep the buffer short; the PC holds the real text.
                text = if (new.length > 48) "" else new
            },
            placeholder = { Text("Type here to send to the PC") },
            shape = RoundedCornerShape(28.dp),
            singleLine = true,
            visualTransformation = VisualTransformation.None,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = {
                onSpecialKey(SpecialKey.RETURN, consumeModifiers())
                text = ""
            }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ExpressiveButton(
                onClick = { onSpecialKey(SpecialKey.SUPER, consumeModifiers()) },
                contentDescription = "Super key",
                containerColor = colors.tertiaryContainer,
                contentColor = colors.onTertiaryContainer,
                modifier = Modifier.height(48.dp).widthIn(min = 72.dp),
                cornerPercent = 50,
                pressedCornerPercent = 30,
            ) {
                Text("Super", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
            }
            specialKeys.forEach { (label, code) ->
                ExpressiveButton(
                    onClick = { onSpecialKey(code, consumeModifiers()) },
                    contentDescription = if (label == "⌫") "Backspace" else label,
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                    modifier = Modifier.height(48.dp).widthIn(min = 56.dp),
                    cornerPercent = 30,
                    pressedCornerPercent = 50,
                    repeatOnHold = code in setOf(SpecialKey.BACKSPACE, SpecialKey.LEFT, SpecialKey.UP, SpecialKey.DOWN, SpecialKey.RIGHT),
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 14.dp))
                }
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ModifierKey(
    label: String,
    latch: Latch,
    shape: RoundedCornerShape,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (latch) {
        Latch.Off -> colors.surfaceContainerHigh to colors.onSurface
        Latch.Once -> colors.secondaryContainer to colors.onSecondaryContainer
        Latch.Locked -> colors.primary to colors.onPrimary
    }
    Surface(
        shape = shape,
        color = container,
        contentColor = content,
        modifier = modifier
            .height(48.dp)
            .semantics {
                role = Role.Checkbox
                stateDescription = when (latch) {
                    Latch.Off -> "Off"
                    Latch.Once -> "On for next key"
                    Latch.Locked -> "Locked"
                }
            },
    ) {
        Row(
            Modifier.fillMaxSize().combinedClickable(onClick = onTap, onLongClick = onLongPress),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (latch == Latch.Locked) {
                Icon(Icons.Rounded.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.size(4.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}
