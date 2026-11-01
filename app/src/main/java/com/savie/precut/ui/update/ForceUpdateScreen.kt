package com.savie.precut.ui.update

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.TextSecondary

/**
 * The blocking update screen (Phase 23): over everything, not dismissable. Back and every touch
 * behind it do nothing; only Update now does something.
 */
@Composable
fun ForceUpdateScreen(message: String?, onUpdate: () -> Unit) {
    // Composed after the app's own handlers, so it wins: Back does nothing.
    BackHandler(enabled = true) {}
    Box(
        Modifier
            .fillMaxSize()
            .background(Ink0)
            // Swallows touches meant for the screens underneath.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.systemBarsPadding().padding(32.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(Modifier.size(72.dp).background(AccentContainer, CircleShape), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_download), contentDescription = null, tint = Accent, modifier = Modifier.size(36.dp))
            }
            Text(stringResource(R.string.update_required_title), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text(
                message ?: stringResource(R.string.update_required_body),
                style = MaterialTheme.typography.bodyLarge,
                color = TextSecondary,
                textAlign = TextAlign.Center,
            )
            FilledButton(stringResource(R.string.update_now), onUpdate, Modifier.fillMaxWidth(), large = true)
        }
    }
}
