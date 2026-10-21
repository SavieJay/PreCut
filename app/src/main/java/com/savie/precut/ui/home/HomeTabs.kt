package com.savie.precut.ui.home

import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

enum class HomeTab { DRAFTS, TEMPLATES }

/** Drafts | Templates. */
@Composable
fun HomeTabs(tab: HomeTab, onTab: (HomeTab) -> Unit, modifier: Modifier = Modifier) {
    SecondaryTabRow(
        selectedTabIndex = tab.ordinal,
        containerColor = Ink0,
        indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(tab.ordinal), color = Accent) },
        modifier = modifier,
    ) {
        HomeTab.entries.forEach { entry ->
            Tab(
                selected = entry == tab,
                onClick = { onTab(entry) },
                text = { Text(stringResource(if (entry == HomeTab.DRAFTS) R.string.home_tab_drafts else R.string.home_tab_templates)) },
                selectedContentColor = TextPrimary,
                unselectedContentColor = TextSecondary,
            )
        }
    }
}
