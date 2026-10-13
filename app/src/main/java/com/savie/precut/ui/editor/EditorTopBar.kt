package com.savie.precut.ui.editor

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.savie.precut.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorTopBar(canExport: Boolean, onBack: () -> Unit, onExport: () -> Unit) {
    TopAppBar(
        title = {},
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = {
            Button(
                onClick = onExport,
                enabled = canExport,
                contentPadding = PaddingValues(horizontal = 18.dp),
                modifier = Modifier.padding(end = 8.dp),
            ) {
                Text(stringResource(R.string.action_export))
            }
        },
    )
}
