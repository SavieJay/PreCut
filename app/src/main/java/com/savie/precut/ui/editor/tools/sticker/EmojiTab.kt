package com.savie.precut.ui.editor.tools.sticker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.model.sticker.EmojiCatalog
import com.savie.precut.model.sticker.EmojiCategory
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Outline
import com.savie.precut.ui.theme.TextSecondary

/** Search, Recent and the categories, 7 to a row (S-01). */
@Composable
fun EmojiTab(recent: List<String>, onEmoji: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    OutlinedTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = { Text(stringResource(R.string.sticker_search)) },
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null) },
        singleLine = true,
        shape = CircleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Accent, unfocusedBorderColor = Outline,
            focusedContainerColor = Ink0, unfocusedContainerColor = Ink0, cursorColor = Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
    val found = remember(query) { EmojiCatalog.search(query).map { it.emoji } }
    val sections = buildList {
        if (query.isNotBlank()) {
            add(null to found)
        } else {
            if (recent.isNotEmpty()) add(R.string.sticker_recent to recent)
            EmojiCatalog.categories.forEach { (category, entries) -> add(category.label() to entries.map { it.emoji }) }
        }
    }
    LazyVerticalGrid(GridCells.Fixed(7), Modifier.fillMaxWidth().height(300.dp)) {
        if (query.isNotBlank() && found.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(stringResource(R.string.sticker_no_results, query), color = TextSecondary, modifier = Modifier.padding(vertical = 16.dp))
            }
        }
        sections.forEach { (title, emoji) ->
            if (title != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
                }
            }
            items(emoji, key = { "$title|$it" }) { e ->
                Box(
                    Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClickLabel = e) { onEmoji(e) },
                    contentAlignment = Alignment.Center,
                ) { Text(e, fontSize = 30.sp) }
            }
        }
    }
}

private fun EmojiCategory.label(): Int = when (this) {
    EmojiCategory.SMILEYS -> R.string.sticker_cat_smileys
    EmojiCategory.PEOPLE -> R.string.sticker_cat_people
    EmojiCategory.ANIMALS -> R.string.sticker_cat_animals
    EmojiCategory.FOOD -> R.string.sticker_cat_food
    EmojiCategory.ACTIVITIES -> R.string.sticker_cat_activities
    EmojiCategory.TRAVEL -> R.string.sticker_cat_travel
    EmojiCategory.OBJECTS -> R.string.sticker_cat_objects
    EmojiCategory.SYMBOLS -> R.string.sticker_cat_symbols
}
