package com.savie.precut.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.GlassLevel
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.RowFill
import com.savie.precut.ui.theme.RowLine
import com.savie.precut.ui.theme.Scrim
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.glass

/**
 * The editor's 2026 bottom sheet (RD `.sheet`): 8 dp from the screen edges, 32 dp corners, raised
 * glass drawn solid (it covers the preview, so nothing glass sits over the video) and a grab bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = Radius.sheet,
        containerColor = Color.Transparent,
        scrimColor = Scrim,
        dragHandle = null,
    ) {
        Column(
            Modifier
                .padding(start = 8.dp, end = 8.dp, bottom = 8.dp)
                .fillMaxWidth()
                .glass(Radius.sheet, GlassLevel.Raised, solid = true),
        ) {
            Box(
                Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .background(GrabBar, RoundedCornerShape(3.dp))
                    .align(Alignment.CenterHorizontally),
            )
            content()
        }
    }
}

private val GrabBar = Color.White.copy(alpha = 0.3f)

/** Fields on sheets (RD `.fld`): 18 dp corners on white 6%, a 2 dp ring when focused (lime for AI). */
internal val FieldShape = RoundedCornerShape(18.dp)

@Composable
internal fun sheetFieldColors(ai: Boolean = false): TextFieldColors {
    val focus = if (ai) AiAccent else Accent
    return OutlinedTextFieldDefaults.colors(
        focusedBorderColor = focus,
        unfocusedBorderColor = RowLine,
        focusedContainerColor = RowFill,
        unfocusedContainerColor = RowFill,
        cursorColor = focus,
        focusedLabelColor = focus,
        unfocusedLabelColor = TextMuted,
        unfocusedPlaceholderColor = TextMuted,
        focusedPlaceholderColor = TextMuted,
    )
}
