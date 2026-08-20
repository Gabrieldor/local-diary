package com.localdiary.app.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Placeholder book cover (a plain panel) until the real artwork is supplied — see
 * CLAUDE.md's "never generate art" rule. Swap the Surface below for an Image once the
 * asset exists; the layout/positioning around it should not need to change.
 */
@Composable
fun LockScreen(onUnlock: () -> Unit) {
    var showPasswordPrompt by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .aspectRatio(0.7f)
                .align(Alignment.Center),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {}

        FilledIconButton(
            onClick = { showPasswordPrompt = true },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 24.dp)
        ) {
            Icon(imageVector = Icons.Filled.Lock, contentDescription = "Unlock diary")
        }
    }

    if (showPasswordPrompt) {
        PasswordPromptDialog(
            onConfirm = {
                showPasswordPrompt = false
                onUnlock()
            },
            onDismiss = { showPasswordPrompt = false }
        )
    }
}
