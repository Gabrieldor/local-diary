package com.localdiary.app.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localdiary.app.lock.PasswordPromptDialog
import com.localdiary.app.ui.theme.AppFontFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CoverEasing = CubicBezierEasing(0.32f, 0.68f, 0.24f, 1.02f)

// Palette pulled directly from the validated prototype's CSS custom properties
// (fetched from the live artifact, not from memory). Cover/backdrop/accent/shadow
// use the dark-theme tokens; the paper tokens deliberately stay on the light-theme
// ("yellow") values — a mixed palette, by request, not a themed surface.
// Also used as the app-wide button container color (see ui/theme/Theme.kt).
internal val CoverColor = Color(0xFF7A3049) // dark --color-cover
internal val CoverColorDark = Color(0xFF4A1C2C) // dark --color-cover-dark
private val CoverShine = Color(0x1AFFF4DE) // dark --color-cover-shine: rgba(255,244,222,0.10)
// Also used for the binder tab icons/labels and the page wordmark.
internal val BrassAccent = Color(0xFFC7A565) // dark --color-accent
// Also the app's base background for everything other than the closed/opening book
// (see MainActivity's Surface) — the diary's "paper" identity carries through once open.
internal val PaperColor = Color(0xFFF2ECD9) // light --color-paper
// Also exposed for card surfaces elsewhere in the app — "page color, slightly darker".
internal val PaperShade = Color(0xFFE6DFC9) // light --color-paper-shade
// Also used for entry titles on the home screen's ruled page.
internal val PaperText = Color(0xFF2A2118) // light --color-paper-text
// Also used for entry dates and dimmed text on the home screen.
internal val PaperTextDim = Color(0xFF5C5348) // light --color-text-dim
// Also used for the home screen's ruled-paper lines.
internal val BorderColor = Color(0x24241D22) // light --color-border: rgba(36,29,34,0.14)
private val ShadowColor = Color(0xA6000000) // dark --color-shadow: rgba(0,0,0,0.65)

// Matches the prototype's stage panel (--color-bg-dim) — the backdrop the closed book
// sits on, replacing the plain Material background while the book isn't full-screen.
// Also used behind the home screen's binder tab row, so the reserved empty tab's dashed
// border and the seams between tabs read against a dark ground instead of the cream page.
internal val BackdropColor = Color(0xFF100E0B) // --color-bg-dim

/**
 * CSS's `linear-gradient(angleDeg, ...)` line length depends on the box's aspect ratio —
 * for a non-square box it is NOT the same as a plain corner-to-corner brush (which is what
 * Compose's default start/end gives you). This reproduces the CSS gradient-line algorithm
 * exactly: 0deg points up, angle increases clockwise, and the line is exactly long enough
 * to span the box at that angle.
 */
private fun cssAngleGradient(angleDeg: Float, size: Size, vararg colorStops: Pair<Float, Color>): Brush {
    val theta = Math.toRadians(angleDeg.toDouble())
    val dx = kotlin.math.sin(theta).toFloat()
    val dy = -kotlin.math.cos(theta).toFloat()
    val halfLength = kotlin.math.abs(size.width / 2f * dx) + kotlin.math.abs(size.height / 2f * dy)
    val center = Offset(size.width / 2f, size.height / 2f)
    val start = center - Offset(dx, dy) * halfLength
    val end = center + Offset(dx, dy) * halfLength
    return Brush.linearGradient(colorStops = colorStops, start = start, end = end)
}

private const val ClosedWidthFraction = 0.8f
private const val ClosedAspectRatio = 0.7f
private val BookShape = RoundedCornerShape(topStart = 3.dp, topEnd = 8.dp, bottomEnd = 8.dp, bottomStart = 3.dp)

// 140ms is the fastest a single flip can be while its own rotation is still visible —
// confirmed by catching a mid-flip frame in a screen recording; below that (tried 55ms)
// it reads as a hard cut between flat pages, not a flip.
private const val FlipPageCount = 7
private const val FlipStartOffsetMs = 100
private const val FlipDurationMs = 140
private val FlipLinePatterns = listOf(
    listOf(0.7f, 0.95f, 0.55f),
    listOf(0.88f, 0.6f, 0.75f),
    listOf(0.65f, 0.9f, 0.5f, 0.8f),
    listOf(0.92f, 0.58f),
    listOf(0.7f, 0.85f, 0.6f),
    listOf(0.55f, 0.9f, 0.7f, 0.62f)
)

private enum class BookState { Closed, Opening, Open }

/**
 * The diary itself: one persistent book, closed on the lock screen, that hinges open in
 * place once the password is confirmed and grows to fill the screen as [content] (the real
 * home screen) takes over. There is no scene-swap between a "lock screen" and a separate
 * reveal — it's the same object throughout, matching the physical metaphor of actually
 * opening a book rather than cutting between two screens.
 */
@Composable
fun DiaryBook(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var state by remember { mutableStateOf(BookState.Closed) }
    var showPasswordPrompt by remember { mutableStateOf(false) }

    val density = LocalDensity.current.density
    val cameraDistance = 8 * density * 30

    val backdropModifier = if (state == BookState.Open) Modifier else Modifier.background(BackdropColor)
    BoxWithConstraints(modifier = modifier.fillMaxSize().then(backdropModifier)) {
        val closedWidth = maxWidth * ClosedWidthFraction
        val closedHeight = closedWidth / ClosedAspectRatio
        // The cover fades out well before the book finishes expanding, so its own
        // rotated/gradiented life is spent entirely at the closed size — safe to use
        // as a fixed size for the angle-accurate gradients below.
        val closedSizePx = Size(closedWidth.value * density, closedHeight.value * density)

        val bookWidth = remember { Animatable(closedWidth.value) }
        val bookHeight = remember { Animatable(closedHeight.value) }

        val coverAngle = remember { Animatable(0f) }
        val coverTitleAlpha = remember { Animatable(1f) }
        var coverVisible by remember { mutableStateOf(true) }

        val flipAngles = remember { List(FlipPageCount) { Animatable(0f) } }
        val flipVisible = remember { mutableStateListOf<Boolean>().apply { repeat(FlipPageCount) { add(true) } } }

        val shadowAlpha = remember { Animatable(0f) }
        val contentAlpha = remember { Animatable(0f) }
        val miniPageAlpha = remember { Animatable(1f) }

        LaunchedEffect(state) {
            if (state != BookState.Opening) return@LaunchedEffect
            launch {
                coverAngle.animateTo(-155f, tween(620, easing = CoverEasing))
                coverVisible = false
            }
            launch { coverTitleAlpha.animateTo(0f, tween(160)) }
            launch { shadowAlpha.animateTo(0.4f, tween(300)) }
            launch {
                // Sequential by construction: each page's animateTo suspends this coroutine
                // until it's fully done before the next one starts, so no two pages are ever
                // mid-flip at the same time.
                delay(FlipStartOffsetMs.toLong())
                for (i in 0 until FlipPageCount) {
                    flipAngles[i].animateTo(-170f, tween(FlipDurationMs, easing = FastOutSlowInEasing))
                    flipVisible[i] = false
                }
            }
            launch {
                delay(700)
                launch { bookWidth.animateTo(maxWidth.value, tween(500, easing = FastOutSlowInEasing)) }
                bookHeight.animateTo(maxHeight.value, tween(500, easing = FastOutSlowInEasing))
            }
            launch {
                delay(FlipStartOffsetMs + FlipDurationMs.toLong() * FlipPageCount)
                launch { shadowAlpha.animateTo(0f, tween(260)) }
                launch { miniPageAlpha.animateTo(0f, tween(260)) }
                contentAlpha.animateTo(1f, tween(300))
                state = BookState.Open
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(width = closedWidth, height = 28.dp)
                .offset(y = closedHeight / 2 + 8.dp)
                .graphicsLayer { alpha = shadowAlpha.value }
                .background(Brush.radialGradient(listOf(ShadowColor, Color.Transparent)))
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(bookWidth.value.dp)
                .height(bookHeight.value.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = contentAlpha.value }) {
                content()
            }

            if (miniPageAlpha.value > 0f) {
                Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = miniPageAlpha.value }) {
                    MiniPageLayer()
                }
            }

            for (i in FlipPageCount - 1 downTo 0) {
                if (flipVisible[i]) {
                    PageFlipLayer(
                        angle = flipAngles[i].value,
                        cameraDistance = cameraDistance,
                        // Alternate light/dark so every flip contrasts against the page it
                        // reveals underneath, the same way flip1/flip2 did.
                        pageColor = if (i % 2 == 0) PaperColor else PaperShade,
                        lineWidths = FlipLinePatterns[i % FlipLinePatterns.size],
                        pageNumber = (i + 1).toString()
                    )
                }
            }
            if (coverVisible) {
                CoverLayer(
                    angle = coverAngle.value,
                    titleAlpha = coverTitleAlpha.value,
                    cameraDistance = cameraDistance,
                    size = closedSizePx,
                    showLock = state == BookState.Closed,
                    onLockClick = { showPasswordPrompt = true }
                )
            }
        }
    }

    if (showPasswordPrompt) {
        PasswordPromptDialog(
            onConfirm = {
                showPasswordPrompt = false
                state = BookState.Opening
            },
            onDismiss = { showPasswordPrompt = false }
        )
    }
}

@Composable
private fun MiniPageLayer() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperShade, BookShape)
            .padding(20.dp)
    ) {
        Column {
            Text(
                text = "LOCAL DIARY",
                color = BrassAccent,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
                fontFamily = AppFontFamily
            )
            Column(modifier = Modifier.padding(top = 14.dp)) {
                listOf("New entry", "Entries", "Search", "Settings").forEach { label ->
                    Text(
                        text = label,
                        color = PaperText,
                        fontSize = 14.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 9.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverLayer(
    angle: Float,
    titleAlpha: Float,
    cameraDistance: Float,
    size: Size,
    showLock: Boolean,
    onLockClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = angle
                this.cameraDistance = cameraDistance
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            // Matches box-shadow: 0 10px 26px var(--color-shadow) — lifts the cover
            // off the backdrop instead of it reading as a flat colored rectangle.
            .shadow(elevation = 16.dp, shape = BookShape, ambientColor = ShadowColor, spotColor = ShadowColor)
            .background(
                cssAngleGradient(160f, size, 0f to CoverColor, 1f to CoverColorDark),
                BookShape
            )
            .background(
                cssAngleGradient(135f, size, 0f to CoverShine, 0.4f to Color.Transparent),
                BookShape
            )
            // Matches the CSS's `inset 0 0 0 1px rgba(0,0,0,0.25)` — a thin dark bevel
            // along the inner edge for a little more depth.
            .border(1.dp, Color(0x40000000), BookShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Local\nDiary",
            color = PaperColor,
            fontSize = 24.sp,
            lineHeight = 27.6.sp,
            textAlign = TextAlign.Center,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            fontFamily = AppFontFamily,
            modifier = Modifier.graphicsLayer { alpha = titleAlpha }
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 12.dp)
                .size(width = 8.dp, height = 34.dp)
                .background(
                    Brush.verticalGradient(listOf(BrassAccent, CoverColorDark)),
                    RoundedCornerShape(2.dp)
                )
        )
        if (showLock) {
            FilledIconButton(
                onClick = onLockClick,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 24.dp)
            ) {
                Icon(imageVector = Icons.Filled.Lock, contentDescription = "Unlock diary")
            }
        }
    }
}

@Composable
private fun PageFlipLayer(angle: Float, cameraDistance: Float, pageColor: Color, lineWidths: List<Float>, pageNumber: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = angle
                this.cameraDistance = cameraDistance
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .background(pageColor, BookShape)
            .border(1.dp, BorderColor, BookShape)
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.padding(top = 28.dp)) {
            lineWidths.forEach { fraction ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(2.dp)
                        .padding(vertical = 5.dp)
                        .background(BorderColor)
                )
            }
        }
        Text(
            text = pageNumber,
            color = PaperTextDim,
            fontSize = 10.sp,
            fontFamily = AppFontFamily,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}
