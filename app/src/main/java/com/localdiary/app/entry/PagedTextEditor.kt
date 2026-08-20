package com.localdiary.app.entry

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private val PAGE_PADDING = 16.dp

/**
 * A book-like paginated text editor: each page has a fixed size and never scrolls. [text] is
 * a single continuous string; page boundaries are computed from layout on every
 * recomposition, not persisted. A page is editable only if it's the last page with real
 * content, or the fresh blank page one past it (reachable once that last page is full) — every
 * earlier page is read-only, matching flipping back through an already-written diary.
 *
 * Typed input that would overflow a page is rejected outright (never silently scrolled),
 * forcing the user to swipe or tap Next to continue on a new page. Text arriving from voice
 * dictation is exempt from that gate — it always lands in [text] regardless of overflow, since
 * dropping transcribed speech would lose captured audio content.
 *
 * Page capacity is always computed against the box's height with the keyboard closed, even
 * while it's open — the on-screen keyboard shrinks the visible viewport, not the page itself.
 * While the keyboard covers part of a page, that single page scrolls within the viewport (never
 * across pages) so the rest of it stays reachable; pagination itself never depends on whether
 * the keyboard happens to be open.
 */
@Composable
fun PagedTextEditor(
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = ""
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val textStyle = MaterialTheme.typography.bodyLarge
    val scope = rememberCoroutineScope()

    var boxSizePx by remember { mutableStateOf(IntSize.Zero) }

    // The keyboard shrinks this box's measured height while it's open. Adding its current
    // height back recovers what the box would measure with the keyboard closed, so page
    // capacity stays constant regardless of keyboard state — only the visible viewport shrinks.
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val fullPageHeightPx = boxSizePx.height + imeBottomPx

    val paddingPx = with(density) { (PAGE_PADDING * 2).roundToPx() }
    val contentConstraints = Constraints(
        maxWidth = (boxSizePx.width - paddingPx).coerceAtLeast(0),
        maxHeight = (fullPageHeightPx - paddingPx).coerceAtLeast(0)
    )
    val fullPageHeightDp = with(density) { fullPageHeightPx.toDp() }

    val realPages = remember(text, contentConstraints) {
        computePages(text, contentConstraints, textStyle, measurer)
    }
    val lastPageFull = remember(realPages, contentConstraints) {
        contentConstraints.maxWidth > 0 && contentConstraints.maxHeight > 0 &&
            isPageFull(realPages.last(), contentConstraints, textStyle, measurer)
    }
    val pageCount = if (lastPageFull) realPages.size + 1 else realPages.size

    val pagerState = rememberPagerState(pageCount = { pageCount })

    // If new real pages appear (dictation overflowing the tail) while the user was looking
    // at what was previously the last page, follow forward so their freshly dictated text
    // stays in view. If they'd navigated back to reread an earlier page, leave them there.
    var previousLastRealIndex by remember { mutableStateOf(realPages.lastIndex) }
    LaunchedEffect(realPages.size) {
        val newLastIndex = realPages.lastIndex
        if (pagerState.currentPage == previousLastRealIndex && newLastIndex != previousLastRealIndex) {
            pagerState.scrollToPage(newLastIndex)
        }
        previousLastRealIndex = newLastIndex
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .onSizeChanged { boxSizePx = it }
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val isEditable = page == realPages.lastIndex || page == realPages.size
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(fullPageHeightDp)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                            .padding(PAGE_PADDING)
                    ) {
                        if (isEditable) {
                            val activeText = if (page < realPages.size) realPages[page] else ""
                            if (activeText.isEmpty() && placeholder.isNotEmpty()) {
                                Text(
                                    text = placeholder,
                                    style = textStyle,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BasicTextField(
                                value = activeText,
                                onValueChange = { candidate ->
                                    if (contentConstraints.maxWidth <= 0 || contentConstraints.maxHeight <= 0) return@BasicTextField
                                    val fits = !measurer.measure(
                                        text = candidate,
                                        style = textStyle,
                                        constraints = contentConstraints
                                    ).hasVisualOverflow
                                    if (fits) {
                                        val prefix = realPages.take(page).joinToString(separator = "")
                                        onTextChange(prefix + candidate)
                                    }
                                },
                                textStyle = textStyle.copy(color = MaterialTheme.colorScheme.onSurface),
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(text = realPages[page], style = textStyle)
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                enabled = pagerState.currentPage > 0
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous page")
            }
            Text(
                text = "Page ${pagerState.currentPage + 1} of $pageCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            IconButton(
                onClick = { scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) } },
                enabled = pagerState.currentPage < pageCount - 1
            ) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next page")
            }
        }
    }
}

/** Greedily splits [text] into pages that each fit within [constraints], recomputed fresh every call. */
private fun computePages(
    text: String,
    constraints: Constraints,
    style: TextStyle,
    measurer: TextMeasurer
): List<String> {
    if (constraints.maxWidth <= 0 || constraints.maxHeight <= 0 || text.isEmpty()) {
        return listOf(text)
    }
    val pages = mutableListOf<String>()
    var start = 0
    while (start < text.length) {
        val end = findPageEnd(text, start, constraints, style, measurer)
        pages.add(text.substring(start, end))
        start = end
    }
    return pages
}

/** Binary-searches the furthest offset from [start] whose substring still fits [constraints]. */
private fun findPageEnd(
    text: String,
    start: Int,
    constraints: Constraints,
    style: TextStyle,
    measurer: TextMeasurer
): Int {
    fun fits(end: Int): Boolean {
        val result = measurer.measure(
            text = text.substring(start, end),
            style = style,
            constraints = Constraints(maxWidth = constraints.maxWidth)
        )
        return result.size.height <= constraints.maxHeight
    }

    var lo = start + 1
    var hi = text.length
    var best = lo
    while (lo <= hi) {
        val mid = (lo + hi) / 2
        if (fits(mid)) {
            best = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return best
}

/** Whether even one more character would overflow [constraints]. */
private fun isPageFull(
    pageText: String,
    constraints: Constraints,
    style: TextStyle,
    measurer: TextMeasurer
): Boolean {
    val probe = pageText + "M"
    return measurer.measure(text = probe, style = style, constraints = constraints).hasVisualOverflow
}
