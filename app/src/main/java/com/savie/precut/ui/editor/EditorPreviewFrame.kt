package com.savie.precut.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.CaptureMenu
import com.savie.precut.ui.components.PreCutSpinner
import com.savie.precut.ui.components.pressScale
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary

private val PreviewEdge = Color.White.copy(alpha = 0.07f)

/**
 * The preview's frame (RD-E03, redline 2): 16 dp from the sides and 4 dp under the top bar, black
 * with 24 dp corners, a faint white edge and a soft pink glow below. Nothing glass goes on it.
 */
internal fun Modifier.previewFrame(): Modifier = this
    .fillMaxWidth()
    .padding(start = 16.dp, end = 16.dp, top = 4.dp)
    .shadow(26.dp, Radius.card, clip = false, ambientColor = Color.Transparent, spotColor = Accent.copy(alpha = 0.5f))
    .clip(Radius.card)
    .background(Color.Black)
    .border(1.dp, PreviewEdge, Radius.card)

/**
 * The empty project's preview (RD-E01): a big white "+" with a pink glow, then what to do. It opens
 * the same gallery/camera menu as the timeline's "+".
 */
@Composable
internal fun EmptyPreview(importing: Boolean, onAddMedia: () -> Unit, onCamera: (video: Boolean) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(28.dp)
    Column(
        Modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box {
            Box(
                Modifier
                    .size(80.dp)
                    .pressScale(interaction)
                    .shadow(20.dp, shape, ambientColor = Color.Transparent, spotColor = Accent.copy(alpha = 0.35f))
                    .clip(shape)
                    .background(Color.White)
                    .clickable(
                        interactionSource = interaction,
                        indication = null,
                        enabled = !importing,
                        role = Role.Button,
                        onClickLabel = stringResource(R.string.timeline_add_media),
                    ) { menu = true },
                contentAlignment = Alignment.Center,
            ) {
                if (importing) {
                    PreCutSpinner(size = 32.dp)
                } else {
                    Icon(painterResource(R.drawable.ic_add), stringResource(R.string.timeline_add_media), tint = Ink0, modifier = Modifier.size(40.dp))
                }
            }
            CaptureMenu(expanded = menu, onDismiss = { menu = false }, onCamera = onCamera, onGallery = onAddMedia)
        }
        Text(stringResource(R.string.rd_editor_empty_title), style = PreCutType.panelTitle, color = TextPrimary, textAlign = TextAlign.Center)
        Text(stringResource(R.string.rd_editor_empty_body), style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center)
    }
}
