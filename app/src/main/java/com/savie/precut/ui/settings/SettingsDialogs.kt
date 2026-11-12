package com.savie.precut.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.home.GlassDialog
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary

private val OptionShape = RoundedCornerShape(14.dp)

/** A list of [options] to pick one from, as 52 dp rows in the 2026 dialog; picking closes it. */
@Composable
internal fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: @Composable (T) -> String, onPick: (T) -> Unit, onDismiss: () -> Unit) {
    GlassDialog(title = title, onDismiss = onDismiss) {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()).selectableGroup()) {
            options.forEach { option ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .clip(OptionShape)
                        .selectable(option == selected, role = Role.RadioButton) { onPick(option) }
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    RadioButton(selected = option == selected, onClick = null, colors = RadioButtonDefaults.colors(selectedColor = Accent, unselectedColor = TextMuted))
                    Text(label(option), style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                }
            }
        }
    }
}
