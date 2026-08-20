package com.localdiary.app.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localdiary.app.ui.theme.AppFontFamily
import com.localdiary.coredata.DiaryEntry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val entryDateFormatter: DateTimeFormatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

private val RuleColor = BorderColor
private val MarginRuleColor = CoverColor.copy(alpha = 0.28f)
private val RuleSpacing = 32.dp
internal val MarginRuleOffset = 26.dp
private val PageEndInset = 18.dp

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNewEntry: () -> Unit,
    onOpenEntry: (Long) -> Unit,
    onSettings: () -> Unit
) {
    val entries by viewModel.entries.collectAsState()
    var filterActive by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Extends the tab row's dark backdrop up behind the status bar/camera cutout too,
        // so the two read as one continuous dark strip rather than the tabs floating on a
        // cream gap above them.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsTopHeight(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
                .background(BackdropColor)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
        ) {
            BinderTabRow(
                filterActive = filterActive,
                onNewEntry = onNewEntry,
                onToggleFilter = { filterActive = !filterActive },
                onSettings = onSettings
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PaperColor)
                    .drawBehind {
                        val spacingPx = RuleSpacing.toPx()
                        var y = spacingPx
                        while (y < size.height) {
                            drawLine(RuleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
                            y += spacingPx
                        }
                        val marginX = MarginRuleOffset.toPx()
                        drawLine(MarginRuleColor, Offset(marginX, 0f), Offset(marginX, size.height), strokeWidth = 1.dp.toPx())
                    }
                    .padding(start = 40.dp, end = PageEndInset)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxWidth().height(RuleSpacing), contentAlignment = Alignment.BottomStart) {
                        Text(
                            text = "LOCAL DIARY",
                            color = BrassAccent,
                            fontSize = 10.sp,
                            letterSpacing = 2.sp,
                            fontFamily = AppFontFamily
                        )
                    }
                    if (filterActive) {
                        Box(modifier = Modifier.fillMaxWidth().height(RuleSpacing), contentAlignment = Alignment.CenterStart) {
                            FilterPill()
                        }
                    }
                    if (entries.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().height(RuleSpacing), contentAlignment = Alignment.BottomStart) {
                            Text(text = "No entries yet.", color = PaperTextDim, fontFamily = AppFontFamily)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(entries, key = { it.id }) { entry ->
                                EntryRow(entry, onClick = { onOpenEntry(entry.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterPill() {
    Row(
        modifier = Modifier
            .background(BrassAccent, RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = Icons.Filled.FilterAlt, contentDescription = null, tint = CoverColorDark, modifier = Modifier.width(11.dp))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = "Filter active", color = CoverColorDark, fontSize = 11.sp, fontFamily = AppFontFamily)
    }
}

@Composable
private fun EntryRow(entry: DiaryEntry, onClick: () -> Unit) {
    val date = remember(entry.createdAtEpochMillis) {
        Instant.ofEpochMilli(entry.createdAtEpochMillis)
            .atZone(ZoneId.systemDefault())
            .toLocalDate()
            .format(entryDateFormatter)
    }
    val title = remember(entry.transcript, date) {
        entry.transcript.lineSequence().firstOrNull { it.isNotBlank() }?.trim() ?: date
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RuleSpacing)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = PaperText,
            maxLines = 1,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = date, style = MaterialTheme.typography.bodySmall, color = PaperTextDim)
    }
}
