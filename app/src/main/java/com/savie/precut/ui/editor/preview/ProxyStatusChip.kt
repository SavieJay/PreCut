package com.savie.precut.ui.editor.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink4
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.tabular
import kotlinx.coroutines.flow.StateFlow

/** "Optimizing preview 40%" while this project's preview proxies are made (E-08); fades out after. */
@Composable
fun ProxyStatusChip(progress: StateFlow<Int?>, modifier: Modifier = Modifier) {
    val percent by progress.collectAsStateWithLifecycle()
    // Keep the last value while fading out.
    var shown by remember { mutableIntStateOf(0) }
    percent?.let { shown = it }
    AnimatedVisibility(
        visible = percent != null,
        enter = fadeIn(),
        exit = fadeOut(tween(durationMillis = 300, delayMillis = 600)),
        modifier = modifier,
    ) {
        Row(
            Modifier
                .height(28.dp)
                .background(Color(0xD90E0E10), RoundedCornerShape(14.dp))
                .padding(start = 8.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CircularProgressIndicator(
                progress = { shown / 100f },
                modifier = Modifier.size(14.dp),
                color = Accent,
                trackColor = Ink4,
                strokeWidth = 3.dp,
                gapSize = 0.dp,
            )
            Text(stringResource(R.string.preview_optimizing, shown), style = MaterialTheme.typography.labelMedium.tabular(), color = TextPrimary)
        }
    }
}
