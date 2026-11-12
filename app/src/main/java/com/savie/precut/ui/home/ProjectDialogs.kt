package com.savie.precut.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.savie.precut.R
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutIconButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.ClipBackground
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.RowFill
import com.savie.precut.ui.theme.RowLine
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import java.io.File

private val FieldShape = RoundedCornerShape(18.dp)

/** RD-H04: "Rename project", a NAME field (pink ring when focused) and a pink Save pill. */
@Composable
fun RenameDialog(initialName: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    GlassDialog(title = stringResource(R.string.rd_app_rename_title), onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.rd_app_name_label).uppercase(), style = PreCutType.section, color = TextMuted)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
                shape = FieldShape,
                trailingIcon = if (name.isEmpty()) null else {
                    { PreCutIconButton(R.drawable.ic_close, stringResource(R.string.rd_app_clear_name), { name = "" }, small = true, tint = TextMuted) }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = RowFill,
                    unfocusedContainerColor = RowFill,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = RowLine,
                    cursorColor = Accent,
                ),
                modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
            )
        }
        FilledButton(
            text = stringResource(R.string.action_save),
            onClick = { onConfirm(name) },
            enabled = name.isNotBlank(),
            large = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** RD-H05: the draft's cover beside the question, what stays safe, and an ErrorRed Delete pill. */
@Composable
fun DeleteDialog(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit, coverPath: String? = null) {
    GlassDialog(
        title = stringResource(R.string.home_delete_title),
        onDismiss = onDismiss,
        leading = {
            Box(Modifier.width(48.dp).aspectRatio(9f / 16f).clip(RoundedCornerShape(12.dp)).background(ClipBackground)) {
                coverPath?.let {
                    AsyncImage(model = File(it), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(9f / 16f))
                }
            }
        },
    ) {
        Text(stringResource(R.string.home_delete_message, name), style = MaterialTheme.typography.bodyLarge, color = TextSecondary)
        FilledButton(
            text = stringResource(R.string.action_delete),
            onClick = onConfirm,
            icon = R.drawable.ic_delete,
            large = true,
            destructive = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
