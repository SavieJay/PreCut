package com.savie.precut.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.theme.AccentFill
import com.savie.precut.ui.theme.OnAccent

/** The small "PRO" tag on Pro tools (Phase 25 gating): a 16 dp pink pill with a dark label. */
@Composable
fun ProBadge(modifier: Modifier = Modifier) {
    Text(
        stringResource(R.string.pro_badge),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp),
        color = OnAccent,
        modifier = modifier.height(16.dp).background(AccentFill, CircleShape).padding(horizontal = 6.dp).wrapContentHeight(),
    )
}
