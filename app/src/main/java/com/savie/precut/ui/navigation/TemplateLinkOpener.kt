package com.savie.precut.ui.navigation

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.savie.precut.R
import com.savie.precut.data.TemplateLinkInbox
import com.savie.precut.data.TemplateSharing
import com.savie.precut.model.ai.AiResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Opens shared templates for the navigation host. */
@HiltViewModel
class TemplateLinkViewModel @Inject constructor(val sharing: TemplateSharing) : ViewModel()

/** A template link that arrived: fetched, kept in Projects → Templates, and opened (or a toast why not). */
@Composable
fun TemplateLinkOpener(inbox: TemplateLinkInbox, navController: NavController, viewModel: TemplateLinkViewModel = hiltViewModel()) {
    val pending by inbox.pending.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(pending) {
        val id = pending ?: return@LaunchedEffect
        inbox.handled()
        when (val result = viewModel.sharing.open(id)) {
            is AiResult.Ok -> navController.navigate(TemplateRoute(result.value))
            is AiResult.Failed -> Toast.makeText(context, R.string.template_link_failed, Toast.LENGTH_LONG).show()
        }
    }
}
