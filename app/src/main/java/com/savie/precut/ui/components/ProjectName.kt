package com.savie.precut.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.savie.precut.R
import java.text.DateFormat
import java.util.Date

/** A project's name, or "Project <date>" when it has none yet. */
@Composable
fun projectDisplayName(name: String, createdAt: Long): String = name.ifBlank {
    stringResource(R.string.home_untitled, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(createdAt)))
}
