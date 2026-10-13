package com.savie.precut.ui.editor.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.savie.precut.ui.theme.Playhead as PlayheadColor

/** Fixed centre line marking the current time. */
@Composable
fun Playhead(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(8.dp).background(PlayheadColor, CircleShape))
        Box(Modifier.width(2.dp).fillMaxHeight().background(PlayheadColor))
    }
}
