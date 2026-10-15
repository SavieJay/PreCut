package com.savie.precut.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.StrokeCap
import com.savie.precut.R
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AccentContainer
import com.savie.precut.ui.theme.Ink0
import com.savie.precut.ui.theme.Ink2
import com.savie.precut.ui.theme.Ink3
import com.savie.precut.ui.theme.TextSecondary

/** H-01: illustration, "Start your first video" and a full-width New project button. */
@Composable
fun EmptyProjects(onNewProject: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterVertically),
        ) {
            FilmIllustration()
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.home_empty_title), style = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold))
                Text(
                    stringResource(R.string.home_empty_body),
                    style = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
                    color = TextSecondary,
                    textAlign = TextAlign.Center,
                )
            }
        }
        FilledButton(
            text = stringResource(R.string.home_new_project),
            onClick = onNewProject,
            icon = R.drawable.ic_add,
            large = true,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 56.dp).fillMaxWidth().height(64.dp),
        )
    }
}

/** Two tilted film frames and a scissors badge. */
@Composable
private fun FilmIllustration() {
    Box(Modifier.size(width = 200.dp, height = 150.dp)) {
        Box(Modifier.offset(10.dp, 18.dp).size(168.dp, 114.dp).rotate(-6f).clip(RoundedCornerShape(14.dp)).background(Ink2))
        Box(Modifier.offset(24.dp, 14.dp).size(168.dp, 114.dp).clip(RoundedCornerShape(14.dp)).background(Ink3)) {
            Box(Modifier.padding(horizontal = 14.dp, vertical = 24.dp).fillMaxSize().clip(RoundedCornerShape(6.dp)).background(Ink2))
        }
        Box(
            Modifier.offset(144.dp, 90.dp).size(68.dp).clip(CircleShape).background(Ink0).padding(6.dp).clip(CircleShape).background(AccentContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_split), contentDescription = null, tint = Accent, modifier = Modifier.size(28.dp))
        }
    }
}

/** H-07: shown while picked media is copied in. The importer reports no progress, so the bar is indeterminate. */
@Composable
fun ImportingMedia(modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterVertically),
    ) {
        Text(stringResource(R.string.home_preparing), style = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold))
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = Accent,
            trackColor = Ink3,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
        )
    }
}
