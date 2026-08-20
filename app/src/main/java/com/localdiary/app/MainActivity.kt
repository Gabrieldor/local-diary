package com.localdiary.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.localdiary.app.home.DiaryBook
import com.localdiary.app.home.HomeScreen
import com.localdiary.app.home.HomeViewModel
import com.localdiary.app.home.PaperColor
import com.localdiary.app.ui.theme.appColorScheme
import com.localdiary.app.ui.theme.appTypography

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = appColorScheme(), typography = appTypography()) {
                // The book's own closed/opening backdrop draws over this while locked,
                // so this color is only ever seen once the diary is open — its "paper"
                // identity carrying through as the app's base background.
                Surface(modifier = Modifier.fillMaxSize(), color = PaperColor) {
                    AppRoot()
                }
            }
        }
    }
}

@Composable
private fun AppRoot() {
    DiaryBook { DiaryHome() }
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
                    onOpenEntry = { id -> screen = DiaryScreen.Editor(entryId = id) },
                    onSettings = {}
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
