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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.localdiary.app.lock.PasswordPromptDialog
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val CoverEasing = CubicBezierEasing(0.32f, 0.68f, 0.24f, 1.02f)

// Palette matches the validated book-cover-reveal prototype: oxblood cover, cream paper,
// brass hardware. Fixed regardless of app theme (light/dark) — this is the diary's own
// identity, not a themed surface.
private val CoverColor = Color(0xFF5E2438)
private val CoverColorDark = Color(0xFF3A1522)
private val BrassAccent = Color(0xFFAB8A4E)
private val PaperColor = Color(0xFFF2ECD9)
private val PaperShade = Color(0xFFD9D2C1)
private val PaperText = Color(0xFF2A2118)
private val PaperTextDim = Color(0xFF5C5348)
private val ShadowColor = Color(0xFF140E0A)

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

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val closedWidth = maxWidth * ClosedWidthFraction
        val closedHeight = closedWidth / ClosedAspectRatio

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
                fontFamily = FontFamily.Monospace
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
            .background(
                Brush.linearGradient(listOf(CoverColor, CoverColorDark)),
                BookShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Local Diary",
            color = PaperColor,
            fontSize = 22.sp,
            fontStyle = FontStyle.Italic,
            fontWeight = FontWeight.Medium,
            fontFamily = FontFamily.Serif,
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
            .border(1.dp, PaperTextDim.copy(alpha = 0.3f), BookShape)
            .padding(18.dp)
    ) {
        Column(modifier = Modifier.padding(top = 28.dp)) {
            lineWidths.forEach { fraction ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction)
                        .height(2.dp)
                        .padding(vertical = 5.dp)
                        .background(PaperShade)
                )
            }
        }
        Text(
            text = pageNumber,
            color = PaperTextDim,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}
