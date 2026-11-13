package com.savie.precut.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.savie.precut.auth.AccountRepository
import com.savie.precut.auth.CloudAccount
import com.savie.precut.data.AppSettings
import com.savie.precut.model.account.AuthResult
import com.savie.precut.model.account.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccountUi(
    /** False in a build without accounts: the section isn't shown. */
    val shown: Boolean = false,
    val email: String? = null,
    /** Null until the server answered (offline, it stays null). */
    val cloud: CloudAccount? = null,
    val mobileData: Boolean = false,
    val deleting: Boolean = false,
    val deleteFailed: Boolean = false,
)

/**
 * Settings → Account (Phase 30): who is signed in, cloud storage, the mobile-data switch, sign out
 * and delete account. Signing out or deleting changes the account's state; navigation then shows
 * Sign-in by itself.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val account: AccountRepository,
    private val settings: AppSettings,
) : ViewModel() {
    private val cloud = MutableStateFlow<CloudAccount?>(null)
    private val deleting = MutableStateFlow(Deleting())

    val ui: StateFlow<AccountUi> = combine(account.state, cloud, settings.values, deleting) { state, cloud, values, deleting ->
        AccountUi(
            shown = state is AuthState.SignedIn,
            email = (state as? AuthState.SignedIn)?.email,
            cloud = cloud,
            mobileData = values.syncOnMobileData,
            deleting = deleting.running,
            deleteFailed = deleting.failed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AccountUi())

    init {
        viewModelScope.launch {
            if (account.state.value is AuthState.SignedIn) {
                (account.account() as? AuthResult.Ok)?.let { cloud.value = it.value }
            }
        }
    }

    fun onMobileData(on: Boolean) = settings.update { it.copy(syncOnMobileData = on) }

    fun onSignOut() {
        viewModelScope.launch { account.signOut() }
    }

    fun onDelete() {
        if (deleting.value.running) return
        deleting.value = Deleting(running = true)
        viewModelScope.launch {
            val result = account.deleteAccount()
            deleting.value = Deleting(failed = result is AuthResult.Failed)
        }
    }

    fun onDeleteFailedSeen() {
        deleting.value = Deleting()
    }

    private data class Deleting(val running: Boolean = false, val failed: Boolean = false)
}
