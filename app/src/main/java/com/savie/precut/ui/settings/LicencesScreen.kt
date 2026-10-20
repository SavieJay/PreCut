package com.savie.precut.ui.settings

import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A third-party component shipped in the app, with its licence's full text. */
private class Notice(@StringRes val name: Int, @StringRes val licence: Int, @RawRes val text: Int)

private val APACHE = R.string.licence_apache
private val OFL = R.string.licence_ofl

private val LIBRARIES = listOf(
    Notice(R.string.notice_androidx, APACHE, R.raw.licence_apache_2_0),
    Notice(R.string.notice_compose, APACHE, R.raw.licence_apache_2_0),
    Notice(R.string.notice_media3, APACHE, R.raw.licence_apache_2_0),
    Notice(R.string.notice_hilt, APACHE, R.raw.licence_apache_2_0),
    Notice(R.string.notice_kotlin, APACHE, R.raw.licence_apache_2_0),
    Notice(R.string.notice_coil, APACHE, R.raw.licence_apache_2_0),
    Notice(R.string.notice_material_symbols, APACHE, R.raw.licence_apache_2_0),
)

private val FONTS = listOf(
    Notice(R.string.notice_font_roboto_flex, OFL, R.raw.licence_ofl_robotoflex),
    Notice(R.string.notice_font_anton, OFL, R.raw.licence_ofl_anton),
    Notice(R.string.notice_font_bebas_neue, OFL, R.raw.licence_ofl_bebasneue),
    Notice(R.string.notice_font_caveat, OFL, R.raw.licence_ofl_caveat),
    Notice(R.string.notice_font_lobster, OFL, R.raw.licence_ofl_lobster),
    Notice(R.string.notice_font_pacifico, OFL, R.raw.licence_ofl_pacifico),
    Notice(R.string.notice_font_playfair_display, OFL, R.raw.licence_ofl_playfairdisplay),
    Notice(R.string.notice_font_space_mono, OFL, R.raw.licence_ofl_spacemono),
)

/** Open-source licences: what PreCut ships, and each licence's full text (Apache 2.0 and the OFL ask for it). */
@Composable
fun LicencesScreen(onBack: () -> Unit) {
    var openName by rememberSaveable { mutableStateOf<Int?>(null) }
    val open = (LIBRARIES + FONTS).firstOrNull { it.name == openName }
    BackHandler(enabled = open != null) { openName = null }
    Column(Modifier.fillMaxSize().background(Ink0).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (open != null) openName = null else onBack() }) {
                Icon(painterResource(R.drawable.ic_back), stringResource(R.string.action_back), tint = TextPrimary)
            }
            Text(stringResource(open?.name ?: R.string.settings_licences), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        }
        if (open != null) {
            LicenceText(open.text)
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
                Section(R.string.licences_libraries, LIBRARIES) { openName = it.name }
                Section(R.string.licences_fonts, FONTS) { openName = it.name }
            }
        }
    }
}

@Composable
private fun Section(@StringRes title: Int, notices: List<Notice>, onOpen: (Notice) -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = TextSecondary, modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp))
    notices.forEach { notice ->
        Row(
            Modifier.fillMaxWidth().clickable { onOpen(notice) }.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(notice.name), style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
                Text(stringResource(notice.licence), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            }
            Icon(painterResource(R.drawable.ic_chevron_right), contentDescription = null, tint = TextSecondary)
        }
    }
}

@Composable
private fun LicenceText(@RawRes text: Int) {
    val resources = LocalResources.current
    val content by produceState("", text) {
        value = withContext(Dispatchers.IO) { resources.openRawResource(text).bufferedReader().use { it.readText() } }
    }
    Text(
        content,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        color = TextPrimary,
        modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
    )
}
