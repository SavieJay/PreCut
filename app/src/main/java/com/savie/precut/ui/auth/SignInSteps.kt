package com.savie.precut.ui.auth

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.savie.precut.R
import com.savie.precut.ui.components.AccentTextButton
import com.savie.precut.ui.components.FilledButton
import com.savie.precut.ui.components.PreCutSpinner
import com.savie.precut.ui.components.TonalButton
import com.savie.precut.ui.theme.Accent
import com.savie.precut.ui.theme.RowFill
import com.savie.precut.ui.theme.RowLine
import com.savie.precut.ui.theme.TextMuted
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary

private val FieldShape = RoundedCornerShape(18.dp)

/** Google first (one tap), then the email field. */
@Composable
internal fun EmailStep(ui: SignInUi, onEmailChange: (String) -> Unit, onContinue: () -> Unit, onGoogle: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (ui.googleAvailable) {
            TonalButton(stringResource(R.string.auth_google), onGoogle, Modifier.fillMaxWidth().heightIn(min = 56.dp), icon = R.drawable.ic_account_circle)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                HorizontalDivider(Modifier.weight(1f), color = RowLine)
                Text(stringResource(R.string.auth_or), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                HorizontalDivider(Modifier.weight(1f), color = RowLine)
            }
        }
        AuthField(
            value = ui.email,
            onValueChange = onEmailChange,
            label = stringResource(R.string.auth_email_label),
            placeholder = stringResource(R.string.auth_email_hint),
            icon = R.drawable.ic_mail,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Go, autoCorrectEnabled = false),
            onGo = onContinue,
        )
        Action(ui.busy, stringResource(R.string.auth_email_continue), ui.canContinue, onContinue)
        Text(stringResource(R.string.auth_privacy), style = MaterialTheme.typography.bodySmall, color = TextMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}

/** The code sent to the email: six digits verify by themselves; a new code after a minute. */
@Composable
internal fun CodeStep(ui: SignInUi, onCodeChange: (String) -> Unit, onVerify: () -> Unit, onResend: () -> Unit, onChangeEmail: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.auth_code_title), style = MaterialTheme.typography.titleLarge, color = TextPrimary)
        Text(stringResource(R.string.auth_code_body, ui.email), style = MaterialTheme.typography.bodyMedium, color = TextSecondary, textAlign = TextAlign.Center)
        AuthField(
            value = ui.code,
            onValueChange = onCodeChange,
            label = stringResource(R.string.auth_code_label),
            placeholder = "000000",
            icon = null,
            keyboard = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
            onGo = onVerify,
            textStyle = MaterialTheme.typography.headlineSmall.copy(color = TextPrimary, letterSpacing = 8.sp, textAlign = TextAlign.Center),
        )
        Action(ui.busy, stringResource(R.string.auth_code_verify), ui.canVerify, onVerify)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            AccentTextButton(stringResource(R.string.auth_code_change_email), onChangeEmail, color = TextSecondary)
            AccentTextButton(
                if (ui.resendInSec > 0) stringResource(R.string.auth_code_resend_in, ui.resendInSec) else stringResource(R.string.auth_code_resend),
                onResend,
                enabled = ui.resendInSec == 0 && !ui.busy,
            )
        }
    }
}

@Composable
private fun Action(busy: Boolean, text: String, enabled: Boolean, onClick: () -> Unit) {
    if (busy) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            PreCutSpinner()
        }
    } else {
        FilledButton(text, onClick, Modifier.fillMaxWidth(), large = true, enabled = enabled)
    }
}

@Composable
private fun AuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    @DrawableRes icon: Int?,
    keyboard: KeyboardOptions,
    onGo: () -> Unit,
    textStyle: TextStyle = MaterialTheme.typography.bodyLarge.copy(color = TextPrimary),
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        label = { Text(label) },
        placeholder = { Text(placeholder, color = TextMuted) },
        leadingIcon = icon?.let { { Icon(painterResource(it), contentDescription = null, tint = TextMuted) } },
        keyboardOptions = keyboard,
        keyboardActions = KeyboardActions(onAny = { onGo() }),
        textStyle = textStyle,
        shape = FieldShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = RowFill,
            unfocusedContainerColor = RowFill,
            focusedBorderColor = Accent,
            unfocusedBorderColor = RowLine,
            focusedLabelColor = Accent,
            unfocusedLabelColor = TextMuted,
            cursorColor = Accent,
        ),
        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
    )
}
