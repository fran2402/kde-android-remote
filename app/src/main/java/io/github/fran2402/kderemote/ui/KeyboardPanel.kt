package io.github.fran2402.kderemote.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.fran2402.kderemote.kdeconnect.SpecialKey

/** Mirrors whatever is typed into the field to the PC, key by key. */
@Composable
fun KeyboardPanel(onText: (String) -> Unit, onSpecialKey: (Int) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val colors = MaterialTheme.colorScheme

    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                val common = text.commonPrefixWith(new)
                repeat(text.length - common.length) { onSpecialKey(SpecialKey.BACKSPACE) }
                new.substring(common.length).forEach { ch ->
                    if (ch == '\n') onSpecialKey(SpecialKey.RETURN) else onText(ch.toString())
                }
                text = new
            },
            placeholder = { Text("Type here to send to the PC") },
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Send,
            ),
            keyboardActions = KeyboardActions(onSend = {
                onSpecialKey(SpecialKey.RETURN)
                text = ""
            }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Row(Modifier.fillMaxWidth().height(52.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(
                "Esc" to SpecialKey.ESCAPE,
                "Tab" to SpecialKey.TAB,
                "⌫" to SpecialKey.BACKSPACE,
                "Enter" to SpecialKey.RETURN,
            ).forEach { (label, key) ->
                ExpressiveButton(
                    onClick = { onSpecialKey(key) },
                    contentDescription = if (label == "⌫") "Backspace" else label,
                    containerColor = colors.secondaryContainer,
                    contentColor = colors.onSecondaryContainer,
                    modifier = Modifier.weight(1f).height(52.dp),
                    cornerPercent = 30,
                    pressedCornerPercent = 50,
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }
}
