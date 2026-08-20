package com.localdiary.app.home

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localdiary.app.ui.theme.AppFontFamily

private val TabCornerRadius = 10.dp
private val TabShape = RoundedCornerShape(topEnd = TabCornerRadius)
private val TabSeamColor = Color(0x38000000)
private val FilterActiveColor = Color(0xFFA9834A)
// Shadow cast onto the lower part of each tab by the page edge in front of it — reinforces
// that the tabs sit behind the pages rather than as flat chips floating on top of them.
// Deliberately hard-edged for now rather than a soft fade.
private val TabShadowBrush = Brush.verticalGradient(
    0f to Color.Transparent,
    0.8f to Color.Transparent,
    1f to Color(0x66000000)
)

/**
 * Binder-divider tabs across the top of the home page: new entry, filter, settings, and a
 * fourth slot left reserved. Sits flush against the page below with no gap, matching the
 * validated HTML draft.
 */
@Composable
fun BinderTabRow(
    filterActive: Boolean,
    onNewEntry: () -> Unit,
    onToggleFilter: () -> Unit,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier.fillMaxWidth().height(44.dp).background(BackdropColor)) {
        // The row's own dark background reaches the true left edge (matching the backdrop
        // strip above it); only the first tab's card is pulled in to align with the page's
        // margin rule below, via this spacer rather than padding the whole row.
        Spacer(modifier = Modifier.width(MarginRuleOffset))
        BinderTab(
            label = "New",
            isFirst = true,
            onClick = onNewEntry,
            modifier = Modifier.weight(1f)
        )
        BinderTab(
            label = "Filter",
            isFirst = false,
            active = filterActive,
            onClick = onToggleFilter,
            modifier = Modifier.weight(1f)
        )
        BinderTab(
            label = "Settings",
            isFirst = false,
            onClick = onSettings,
            modifier = Modifier.weight(1f)
        )
        EmptyBinderTab(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun BinderTab(
    label: String,
    isFirst: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    val yOffset by animateDpAsState(targetValue = if (active) 3.dp else 0.dp, label = "tabPress")
    val background = if (active) {
        Brush.linearGradient(listOf(BrassAccent, FilterActiveColor))
    } else {
        Brush.linearGradient(listOf(CoverColor, CoverColorDark))
    }
    val contentColor = if (active) CoverColorDark else PaperColor

    Box(
        modifier = modifier
            .fillMaxHeight()
            .offset(y = yOffset)
            .clip(TabShape)
            .background(background)
            .background(TabShadowBrush)
            .then(
                if (isFirst) Modifier else Modifier.drawBehind {
                    drawLine(TabSeamColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 1.dp.toPx())
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = contentColor,
            fontFamily = AppFontFamily,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun EmptyBinderTab(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .clip(TabShape)
            .background(BackdropColor)
    )
}
