package com.savie.precut.ui.templates

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.savie.precut.R
import com.savie.precut.data.StoredTemplate
import com.savie.precut.model.outputSize
import com.savie.precut.ui.components.MediaBadge
import com.savie.precut.ui.components.PreCutChip
import com.savie.precut.ui.components.PreCutSpinner
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.theme.ClipBackground
import com.savie.precut.ui.theme.Radius
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glass
import com.savie.precut.ui.theme.TextSecondary

/** Projects → Templates: category chips over a grid of template cards; read again whenever it shows. */
@Composable
fun TemplatesTab(onOpen: (templateId: String) -> Unit, modifier: Modifier = Modifier, viewModel: TemplatesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }
    val shown = state.shown
    when {
        shown == null -> Box(modifier, contentAlignment = Alignment.Center) { PreCutSpinner() }
        state.templates.isNullOrEmpty() -> Text(
            stringResource(R.string.templates_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
            modifier = modifier.padding(32.dp),
        )
        else -> Column(modifier) {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.filters.forEach { filter -> PreCutChip(filterLabel(filter), selected = state.filter == filter, onClick = { viewModel.onFilter(filter) }) }
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(shown, key = { it.template.id }) { TemplateCard(it, onClick = { onOpen(it.template.id) }) }
            }
        }
    }
}

@Composable
private fun filterLabel(filter: TemplateFilter): String = when (filter) {
    TemplateFilter.All -> stringResource(R.string.templates_all)
    TemplateFilter.Favourites -> stringResource(R.string.templates_favourites)
    TemplateFilter.Recent -> stringResource(R.string.templates_recent)
    is TemplateFilter.Category -> stringResource(filter.category.label)
}

@Composable
private fun TemplateCard(stored: StoredTemplate, onClick: () -> Unit) {
    val size = stored.template.project.outputSize()
    // The same glass card as a draft (RD-H02): 24 dp corners, an 8 dp inset, the cover at 18 dp.
    Column(Modifier.glass(Radius.card).clickable(onClick = onClick).padding(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(size.width.toFloat() / size.height).clip(RoundedCornerShape(18.dp)).background(ClipBackground)) {
            if (stored.thumbnailUri != null) {
                AsyncImage(model = stored.thumbnailUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                TemplateStoryboard(stored.template, fontSize = 20.sp, modifier = Modifier.fillMaxSize())
            }
            MediaBadge(formatDurationMs(stored.durationMs), Modifier.align(Alignment.BottomEnd).padding(8.dp))
        }
        Column(Modifier.padding(start = 6.dp, end = 6.dp, bottom = 4.dp)) {
            Text(
                stored.template.title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold, lineHeight = 20.sp),
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                pluralStringResource(R.plurals.template_slots, stored.slotCount, stored.slotCount),
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
                color = TextMuted,
            )
        }
    }
}
