package com.localdiary.app.entry

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.localdiary.app.ml.WhisperModelState
import com.localdiary.app.ml.WhisperModelTier

/** [entryId] is null to create a new entry, or an existing entry's id to edit it. */
@Composable
fun EntryEditorScreen(viewModel: EntryEditorViewModel, entryId: Long?, onDone: () -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var showMicRationale by remember { mutableStateOf(false) }

    // viewModel() retains one instance per class across visits to this screen, so entering
    // it — for a new entry or a specific existing one — must (re)target it explicitly.
    remember(entryId) { viewModel.load(entryId) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startRecording() }

    fun onMicTapped() {
        if (uiState.isRecording) {
            viewModel.stopRecordingAndTranscribe()
            return
        }
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (hasPermission) {
            viewModel.startRecording()
        } else {
            showMicRationale = true
        }
    }

    val modelReady = uiState.modelState is WhisperModelState.Ready
    val modelFailed = uiState.modelState is WhisperModelState.Failed

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { viewModel.closeWithoutExplicitSave(onDone) }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModelTierDropdown(
                    selected = uiState.modelTier,
                    enabled = !uiState.isRecording && !uiState.isTranscribing,
                    onSelect = viewModel::selectModelTier,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Button(onClick = { viewModel.save(onSaved = onDone) }) {
                    Text("Save")
                }
            }
        }

        OutlinedTextField(
            value = uiState.text,
            onValueChange = viewModel::onTextChanged,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 16.dp),
            placeholder = { Text("Write your entry, or tap the mic to speak it...") }
        )

        uiState.errorMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.isTranscribing) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            } else {
                FilledIconButton(
                    onClick = { onMicTapped() },
                    enabled = modelReady || uiState.isRecording,
                    modifier = Modifier.height(64.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = if (uiState.isRecording) "Stop recording" else "Record voice entry"
                    )
                }
            }
        }

        Text(
            text = when {
                uiState.isRecording -> "Recording… tap to stop"
                uiState.isTranscribing -> "Transcribing…"
                modelFailed -> "Voice model failed to load — you can still type"
                !modelReady -> "Loading voice model…"
                else -> "Tap the mic to speak your entry"
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            textAlign = TextAlign.Center
        )
    }

    if (showMicRationale) {
        AlertDialog(
            onDismissRequest = { showMicRationale = false },
            title = { Text("Microphone access") },
            text = {
                Text(
                    "Local Diary uses your microphone only to transcribe voice entries on this " +
                        "device. Nothing is recorded to a server or leaves your phone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMicRationale = false
                    permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }) { Text("Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showMicRationale = false }) { Text("Not now") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelTierDropdown(
    selected: WhisperModelTier,
    enabled: Boolean,
    onSelect: (WhisperModelTier) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selected.label,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            singleLine = true,
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded && enabled) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .width(130.dp)
        )
        ExposedDropdownMenu(expanded = expanded && enabled, onDismissRequest = { expanded = false }) {
            WhisperModelTier.entries.forEach { tier ->
                DropdownMenuItem(
                    text = { Text(tier.label) },
                    onClick = {
                        expanded = false
                        onSelect(tier)
                    }
                )
            }
        }
    }
}
