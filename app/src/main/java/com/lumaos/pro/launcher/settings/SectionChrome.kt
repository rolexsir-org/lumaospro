package com.lumaos.pro.launcher.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Section shell shared by Settings and the Theme Studio: a full-width column
 * introduced by the standard divider, so every preference section starts on the
 * same rule. The content lambda intentionally takes no receiver — call sites
 * compose against the scope they are written in.
 */
@Composable
internal fun CleanSection(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        content()
    }
}

/** Thin inset divider shared by Settings and the Theme Studio. */
@Composable
internal fun CleanDivider() {
    HorizontalDivider(
        thickness = 0.6.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
        modifier = Modifier.padding(start = 16.dp)
    )
}
