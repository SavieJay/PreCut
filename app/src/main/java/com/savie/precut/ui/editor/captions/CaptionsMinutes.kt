package com.savie.precut.ui.editor.captions

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.model.ai.AiFailure
import com.savie.precut.ui.cloud.DAY_FLAGS
import com.savie.precut.ui.cloud.aiFailureMessage
import com.savie.precut.ui.components.formatDurationMs
import com.savie.precut.ui.editor.CaptionFailure
import com.savie.precut.ui.editor.CaptionQuota
import com.savie.precut.ui.editor.CaptionsUi
import com.savie.precut.ui.theme.ErrorRed
import com.savie.precut.ui.theme.TextSecondary

/** The minutes Generate needs against those left, or why it can't run now. */
@Composable
fun CaptionsMinutes(options: CaptionsUi.Options) {
    val blocker = options.blocker
    if (blocker != null) {
        Text(captionFailureMessage(blocker), style = MaterialTheme.typography.bodyMedium, color = ErrorRed)
        return
    }
    val needed = (options.neededSeconds + 59) / 60
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val text = when (val quota = options.quota) {
            CaptionQuota.Checking -> stringResource(R.string.captions_minutes_checking)
            CaptionQuota.NotUsed -> stringResource(R.string.captions_minutes_not_used, needed)
            is CaptionQuota.Known -> stringResource(R.string.captions_minutes_known, needed, quota.status.secondsLeft / 60)
            // A blocker above.
            is CaptionQuota.Unavailable -> ""
        }
        Text(text, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        if (options.minutesShort) {
            Text(stringResource(R.string.captions_minutes_short, formatDurationMs(options.reachMs)), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** What to tell the user about captions that couldn't be made, or can't be now. */
@Composable
fun captionFailureMessage(failure: CaptionFailure): String = when (failure) {
    CaptionFailure.NoSound -> stringResource(R.string.captions_no_sound)
    CaptionFailure.SoundNotReadable -> stringResource(R.string.captions_sound_unreadable)
    CaptionFailure.NoSpeech -> stringResource(R.string.captions_no_speech)
    CaptionFailure.NoSpeechInLine -> stringResource(R.string.captions_no_speech_line)
    is CaptionFailure.Cloud -> {
        val cloud = failure.failure
        if (cloud is AiFailure.QuotaUsed && cloud.secondsLeft > 0) {
            val day = cloud.resetsAtMs?.let { DateUtils.formatDateTime(LocalContext.current, it, DAY_FLAGS) }
            day?.let { stringResource(R.string.captions_not_enough_minutes, it) } ?: stringResource(R.string.captions_not_enough_minutes_soon)
        } else {
            aiFailureMessage(cloud)
        }
    }
}
