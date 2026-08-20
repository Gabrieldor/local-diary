package com.localdiary.app.lock

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.PasswordVisualTransformation

/**
 * UI-only placeholder: any submitted value (including empty) unlocks. Real auth
 * (BiometricPrompt / PIN check) is separate, planned M2 work — see docs/GDD.md.
 */
@Composable
fun PasswordPromptDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    var input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Enter password") },
        text = {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text("Password") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(input) }) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
