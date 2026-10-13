package com.savie.precut.ui.editor.tools

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.model.Clip
import com.savie.precut.model.FilterType
import com.savie.precut.ui.editor.timeline.ThumbnailTile

private val TileShape = RoundedCornerShape(8.dp)
private val TileSize = 60.dp

/** Row of the clip's frame under each filter; tap applies it to the clip. */
@Composable
fun FilterTool(
    clip: Clip,
    loader: ThumbnailLoader,
    onFilterSelected: (FilterType) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    /** Shows "Apply to all" when set. */
    onApplyAll: (() -> Unit)? = null,
) {
    val heightPx = with(LocalDensity.current) { TileSize.roundToPx() }
    val key = ThumbnailKey(clip.uri, clip.type, clip.trimStartMs, heightPx, clip.sourceRotationDegrees)
    ToolPanel(stringResource(R.string.filter_title), onDone, modifier, actions = { onApplyAll?.let { ApplyToAllButton(it) } }) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(FilterType.entries) { filter ->
                val selected = filter == clip.filter
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.clickable { onFilterSelected(filter) },
                ) {
                    ThumbnailTile(
                        loader = loader,
                        key = key,
                        colorFilter = filterPreviewMatrix(filter)?.let(ColorFilter::colorMatrix),
                        modifier = Modifier
                            .size(TileSize)
                            .clip(TileShape)
                            .border(2.dp, if (selected) MaterialTheme.colorScheme.primary else Color.Transparent, TileShape),
                    )
                    Text(
                        stringResource(filter.labelRes()),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

@StringRes
private fun FilterType.labelRes(): Int = when (this) {
    FilterType.NONE -> R.string.filter_none
    FilterType.BRIGHTNESS -> R.string.filter_bright
    FilterType.CONTRAST -> R.string.filter_contrast
    FilterType.SATURATION -> R.string.filter_vivid
    FilterType.GRAYSCALE -> R.string.filter_mono
    FilterType.WARM -> R.string.filter_warm
    FilterType.COOL -> R.string.filter_cool
    FilterType.VINTAGE -> R.string.filter_vintage
}
