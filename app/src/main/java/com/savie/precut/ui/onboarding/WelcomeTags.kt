package com.savie.precut.ui.onboarding

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.AiAccent
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.glassRow

/** RD-O01's title: "Edit like a pro." and, on the next line, "In minutes." in PreCut pink. */
@Composable
internal fun welcomeTitle() = buildAnnotatedString {
    append(stringResource(R.string.onboarding_welcome_title))
    append('\n')
    withStyle(SpanStyle(color = Accent)) { append(stringResource(R.string.onboarding_welcome_title_accent)) }
}

/** RD-O01's two tags under the title: "No watermark" (quiet) and "New: AI edit" (lime). Not buttons. */
@Composable
internal fun WelcomeTags(modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WelcomeTag(R.drawable.ic_verified, stringResource(R.string.onboarding_tag_no_watermark), TextPrimary, Modifier.glassRow(CircleShape))
        WelcomeTag(
            R.drawable.ic_auto_awesome, stringResource(R.string.onboarding_tag_ai_edit), AiAccent,
            Modifier.background(AiAccent.copy(alpha = 0.1f), CircleShape).border(1.dp, AiAccent.copy(alpha = 0.42f), CircleShape),
        )
    }
}

@Composable
private fun WelcomeTag(@DrawableRes icon: Int, label: String, color: Color, surface: Modifier) {
    Row(
        surface.height(32.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(label, style = PreCutType.labelChip.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold), color = color, maxLines = 1)
    }
}
