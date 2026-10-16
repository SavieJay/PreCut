package com.savie.precut.ui.editor.tools.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R
import com.savie.precut.media.TtsVoice
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutSlider
import com.savie.precut.ui.components.SliderRow
import com.savie.precut.ui.editor.tools.ToolPanel
import com.savie.precut.ui.theme.PreCutType
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

/** What the text-to-speech panel reports. */
interface TtsActions {
    fun onVoice(name: String)
    fun onPreview(speed: Float, pitch: Float)
    fun onAdd(speed: Float, pitch: Float)
    fun onBack()
}

/** Text to speech (X-06): the phone's voices, speed and pitch, preview, and "Add as voiceover". */
@Composable
fun TtsTool(voices: List<TtsVoice>?, selected: String?, busy: Boolean, actions: TtsActions, onDone: () -> Unit) {
    var speed by remember { mutableFloatStateOf(1f) }
    var pitch by remember { mutableFloatStateOf(0f) }
    fun pitchFactor() = 1f + pitch / 20f
    ToolPanel(
        stringResource(R.string.text_tts_title),
        onDone,
        onBack = actions::onBack,
        actions = {
            IconButton(onClick = { actions.onPreview(speed, pitchFactor()) }, enabled = selected != null) {
                Icon(painterResource(R.drawable.ic_play_circle), contentDescription = stringResource(R.string.text_tts_preview))
            }
        },
    ) {
        Text(stringResource(R.string.text_tts_voices), style = PreCutType.labelChip, color = TextSecondary)
        Column(Modifier.heightIn(max = 180.dp).verticalScroll(rememberScrollState())) {
            when {
                voices == null -> CircularProgressIndicator(Modifier.padding(8.dp))
                voices.isEmpty() -> Text(stringResource(R.string.text_tts_none), color = TextSecondary)
                else -> voices.forEach { voice ->
                    Row(
                        Modifier.fillMaxWidth().clickable(enabled = voice.installed) { actions.onVoice(voice.name) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = voice.name == selected, onClick = { actions.onVoice(voice.name) }, enabled = voice.installed)
                        Column {
                            Text(voice.label, color = if (voice.installed) TextPrimary else TextSecondary)
                            Text(
                                stringResource(
                                    when {
                                        !voice.installed -> R.string.text_tts_download
                                        voice.needsNetwork -> R.string.text_tts_online
                                        else -> R.string.text_tts_offline
                                    },
                                ),
                                style = PreCutType.labelChip,
                                color = TextSecondary,
                            )
                        }
                    }
                }
            }
        }
        SliderRow(value = String.format(Locale.ROOT, "%.1fx", speed), label = stringResource(R.string.motion_speed)) { modifier ->
            PreCutSlider(value = speed, onValueChange = { speed = (it * 10).roundToInt() / 10f }, valueRange = 0.5f..2f, onReset = { speed = 1f }, modifier = modifier)
        }
        SliderRow(value = (if (pitch > 0) "+" else "") + pitch.roundToInt(), label = stringResource(R.string.text_tts_pitch)) { modifier ->
            PreCutSlider(value = pitch, onValueChange = { pitch = it.roundToInt().toFloat() }, valueRange = -10f..10f, anchor = 0f, onReset = { pitch = 0f }, modifier = modifier)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            FilledButton(
                stringResource(if (busy) R.string.text_tts_making else R.string.text_tts_add),
                onClick = { actions.onAdd(speed, pitchFactor()) },
                enabled = selected != null && !busy,
                icon = R.drawable.ic_record_voice_over,
            )
        }
    }
}
