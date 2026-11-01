package com.savie.precut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.AccentFill
import com.savie.precut.ui.theme.OnAccent

/** The small "PRO" tag on Pro tools (Phase 25 gating). */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.pro_badge),
        style = MaterialTheme.typography.labelSmall,
        color = OnAccent,
        modifier = modifier.background(AccentFill, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
    )
}
