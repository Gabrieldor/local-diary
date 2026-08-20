package com.localdiary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.localdiary.app.entry.EntryEditorScreen
import com.localdiary.app.entry.EntryEditorViewModel
import com.localdiary.app.home.HomeScreen
import com.localdiary.app.home.HomeViewModel
import com.localdiary.app.lock.LockScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot()
                }
            }
        }
    }
}

/**
 * Controller for the locked/unlocked transition. Owns no lock/auth logic itself
 * (that's PasswordPromptDialog's and, later, M2's job) — just coordinates which
 * screen is shown and how the switch animates.
 */
@Composable
private fun AppRoot() {
    var isUnlocked by remember { mutableStateOf(false) }

    AnimatedContent(
        targetState = isUnlocked,
        label = "lock-unlock-transition",
        transitionSpec = {
            (scaleIn(initialScale = 0.85f, animationSpec = tween(450)) + fadeIn(animationSpec = tween(450)))
                .togetherWith(scaleOut(targetScale = 1.1f, animationSpec = tween(450)) + fadeOut(animationSpec = tween(450)))
        }
    ) { unlocked ->
        if (unlocked) {
            DiaryHome()
        } else {
            LockScreen(onUnlock = { isUnlocked = true })
        }
    }
}

private sealed interface DiaryScreen {
    data object Home : DiaryScreen
    data class Editor(val entryId: Long?) : DiaryScreen
}

/**
 * Home ↔ entry-editor switch. Reuses the same AnimatedContent state-switch pattern as the
 * lock/unlock transition above rather than pulling in Navigation Compose for two screens.
 * Editor's entryId is null for a new entry, or an existing entry's id to edit it.
 */
@Composable
private fun DiaryHome() {
    var screen by remember { mutableStateOf<DiaryScreen>(DiaryScreen.Home) }
    val app = LocalContext.current.applicationContext as DiaryApplication

    AnimatedContent(
        targetState = screen,
        label = "home-editor-transition",
        transitionSpec = {
            fadeIn(animationSpec = tween(300)).togetherWith(fadeOut(animationSpec = tween(300)))
        }
    ) { current ->
        when (current) {
            DiaryScreen.Home -> {
                val viewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.Factory(dao = app.database.diaryEntryDao())
                )
                HomeScreen(
                    viewModel = viewModel,
                    onNewEntry = { screen = DiaryScreen.Editor(entryId = null) },
                    onOpenEntry = { id -> screen = DiaryScreen.Editor(entryId = id) }
                )
            }
            is DiaryScreen.Editor -> {
                val viewModel: EntryEditorViewModel = viewModel(
                    factory = EntryEditorViewModel.Factory(
                        dao = app.database.diaryEntryDao(),
                        whisperEngineHolder = app.whisperEngineHolder
                    )
                )
                EntryEditorScreen(
                    viewModel = viewModel,
                    entryId = current.entryId,
                    onDone = { screen = DiaryScreen.Home }
                )
            }
        }
    }
}
