package com.savie.precut.ui.editor.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.ThumbnailKey
import com.savie.precut.media.ThumbnailLoader
import com.savie.precut.media.look.CustomLut
import com.savie.precut.media.look.LookThumbnailer
import com.savie.precut.model.look.ClipLook
import com.savie.precut.model.look.FilterCatalog
import com.savie.precut.model.look.FilterCategory
import kotlin.math.roundToInt

/** What the Filter panel can do. */
interface FilterActions {
    fun onFilter(filterId: String?)
    fun onIntensity(intensity: Float)
    fun onImportLut()
}

/**
 * CapCut's Filters: categories, tiles graded with the real LUT, an intensity slider, imported
 * `.cube` LUTs ("My LUTs"), and optionally "Apply to all".
 */
@Composable
fun FilterTool(
    look: ClipLook,
    baseKey: ThumbnailKey?,
    loader: ThumbnailLoader,
    thumbnailer: LookThumbnailer,
    customLuts: List<CustomLut>,
    actions: FilterActions,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onApplyAll: (() -> Unit)? = null,
) {
    val current = look.filterId
    // null = "My LUTs".
    var category by remember {
        mutableStateOf(if (current != null && FilterCatalog.isCustom(current)) null else current?.let { FilterCatalog.preset(it)?.category } ?: FilterCategory.BASIC)
    }
    var intensity by remember(look.filterIntensity) { mutableFloatStateOf(look.filterIntensity) }
    ToolPanel(stringResource(R.string.filter_title), onDone, modifier, actions = { onApplyAll?.let { ApplyToAllButton(it) } }) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterCategory.entries.forEach { c ->
                FilterChip(selected = category == c, onClick = { category = c }, label = { Text(stringResource(c.labelRes())) })
            }
            FilterChip(selected = category == null, onClick = { category = null }, label = { Text(stringResource(R.string.filter_category_custom)) })
        }
        val none = stringResource(R.string.filter_none)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            item(key = "none") { FilterTile(none, null, baseKey, loader, thumbnailer, current == null) { actions.onFilter(null) } }
            val selectedCategory = category
            if (selectedCategory == null) {
                item(key = "import") { IconTile(stringResource(R.string.filter_import_lut), R.drawable.ic_add_box, actions::onImportLut) }
                items(customLuts, key = { it.id }) { lut ->
                    FilterTile(lut.name, lut.id, baseKey, loader, thumbnailer, lut.id == current) { actions.onFilter(lut.id) }
                }
            } else {
                items(FilterCatalog.inCategory(selectedCategory), key = { it.id }) { preset ->
                    val name = filterNameRes(preset.id)?.let { stringResource(it) } ?: preset.id
                    FilterTile(name, preset.id, baseKey, loader, thumbnailer, preset.id == current) { actions.onFilter(preset.id) }
                }
            }
        }
        if (current != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.filter_intensity), style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(72.dp))
                Slider(
                    value = intensity,
                    onValueChange = { intensity = it },
                    onValueChangeFinished = { actions.onIntensity(intensity) },
                    modifier = Modifier.weight(1f),
                )
                Text("${(intensity * 100).roundToInt()}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.width(32.dp))
            }
        }
    }
}
