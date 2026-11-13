package com.savie.precut.ui.auth

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.savie.precut.R
import com.savie.precut.model.account.AuthFailure
import com.savie.precut.ui.theme.ErrorRed
import com.savie.precut.ui.theme.TextPrimary
import com.savie.precut.ui.theme.TextSecondary
import com.savie.precut.ui.theme.afterglowBackground

/**
 * Sign-in (Phase 30): before anything else in the app. Google, or an email and its code. When
 * signing in works the navigation moves on by itself (it follows the account's state).
 */
@Composable
fun SignInScreen(viewModel: SignInViewModel = hiltViewModel()) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val activity = LocalContext.current
    Column(
        Modifier
            .fillMaxSize()
            .afterglowBackground()
            .safeDrawingPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(painterResource(R.drawable.ic_logo), contentDescription = null, tint = Color.Unspecified, modifier = Modifier.size(64.dp))
        Text(stringResource(R.string.auth_title), style = MaterialTheme.typography.headlineMedium, color = TextPrimary, textAlign = TextAlign.Center)
        Text(
            stringResource(if (ui.expired) R.string.auth_expired else R.string.auth_subtitle),
            style = MaterialTheme.typography.bodyLarge,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        AnimatedContent(ui.step, label = "sign-in step") { step ->
            when (step) {
                SignInStep.Email -> EmailStep(
                    ui = ui,
                    onEmailChange = viewModel::onEmailChange,
                    onContinue = viewModel::onContinue,
                    onGoogle = { viewModel.onGoogle(activity) },
                )
                SignInStep.Code -> CodeStep(
                    ui = ui,
                    onCodeChange = viewModel::onCodeChange,
                    onVerify = viewModel::onVerify,
                    onResend = viewModel::onResend,
                    onChangeEmail = viewModel::onChangeEmail,
                )
            }
        }
        ui.error?.let { failure ->
            Text(
                stringResource(failure.message()),
                style = MaterialTheme.typography.bodyMedium,
                color = ErrorRed,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** What the screen says for each failure. */
internal fun AuthFailure.message(): Int = when (this) {
    AuthFailure.Offline -> R.string.auth_error_offline
    AuthFailure.BadCode -> R.string.auth_error_bad_code
    AuthFailure.TooMany -> R.string.auth_error_too_many
    AuthFailure.BadEmail -> R.string.auth_error_bad_email
    AuthFailure.NoGoogleAccount -> R.string.auth_error_no_google
    AuthFailure.NotSetUp -> R.string.auth_error_not_set_up
    AuthFailure.SessionEnded -> R.string.auth_error_session_ended
    AuthFailure.Cancelled, AuthFailure.Busy -> R.string.auth_error_busy
}
