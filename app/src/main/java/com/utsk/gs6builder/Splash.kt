package com.utsk.gs6builder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Launch animation: the app icon fades in with the hammer raised, the hammer drops once, the icon
// takes the hit (a quick squash), the hammer bounces to rest, then the whole thing fades into the app.
// The icon is drawn from two layers exported from the design (splash_art = the artwork without the
// hammer, splash_hammer = the hammer alone on transparency). They share one square canvas, 768 px
// (the 192 px export upscaled 4x), so stacking them at the same size lines them up exactly.

private const val LAYER_PX = 768f

// Muted green/cyan blurred gradient background: soft green and cyan glows fading into a calm
// grey-green base, offset diagonally so it reads like a blurred colour wash rather than bands.
private val SPLASH_BG_BASE = Color(0xFF3B4642)
private val SPLASH_GREEN = Color(0xFF437055)
private val SPLASH_CYAN = Color(0xFF3E6E74)

private val HAMMER_PIVOT = Offset(173f, 591f)        // centre of the handle's rounded end, in layer pixels
private const val RAISED = -24f                      // degrees; negative lifts the head
private const val WINDUP = -31f
private const val STRIKE = 7f

// Shared with the Settings → About → App info page: it reuses the icon artwork and the hammer
// thump animation on its interactive icon.
internal const val APP_ICON_LAYER_PX = 768f
internal val APP_ICON_HAMMER_PIVOT = Offset(173f, 591f)
internal const val APP_ICON_WINDUP = -31f
internal const val APP_ICON_STRIKE = 7f

@Composable
internal fun HammerSplash(onFinished: () -> Unit) {
    val art = ImageBitmap.imageResource(R.drawable.splash_art)
    val hammer = ImageBitmap.imageResource(R.drawable.splash_hammer)

    val intro = remember { Animatable(0f) }
    val angle = remember { Animatable(RAISED) }
    val squash = remember { Animatable(1f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { intro.animateTo(1f, tween(260, easing = FastOutSlowInEasing)) }
            delay(300)
            angle.animateTo(WINDUP, tween(110, easing = LinearOutSlowInEasing))           // wind up
            angle.animateTo(STRIKE, tween(90, easing = CubicBezierEasing(0.6f, 0f, 1f, 0.5f)))  // drop
            // Impact: the icon squashes and recovers while the hammer bounces back to rest.
            launch {
                squash.animateTo(0.95f, tween(45, easing = LinearOutSlowInEasing))
                squash.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 420f))
            }
            launch { angle.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 380f)) }
            delay(520)
        }
        fade.animateTo(0f, tween(220))
        onFinished()
    }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .drawBehind {
                // Blurred green/cyan wash: two soft radial glows over a muted base.
                drawRect(SPLASH_BG_BASE)
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(SPLASH_GREEN, SPLASH_GREEN.copy(alpha = 0f)),
                        center = Offset(size.width * 0.25f, size.height * 0.25f),
                        radius = size.minDimension * 1.05f,
                        tileMode = TileMode.Clamp,
                    ),
                )
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(SPLASH_CYAN, SPLASH_CYAN.copy(alpha = 0f)),
                        center = Offset(size.width * 0.78f, size.height * 0.72f),
                        radius = size.minDimension * 0.95f,
                        tileMode = TileMode.Clamp,
                    ),
                )
            }
            .pointerInput(Unit) { },                  // swallow touches until the app is revealed
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            Modifier.size(160.dp).graphicsLayer {
                alpha = intro.value
                val s = 0.86f + 0.14f * intro.value
                scaleX = s; scaleY = s
            },
        ) {
            val k = size.width / LAYER_PX
            val dst = IntSize(size.width.toInt(), size.height.toInt())
            scale(squash.value, squash.value, pivot = center) {
                // Launcher mask: the circle inscribed in the artwork square. The hammer is not clipped.
                val circle = Path().apply { addOval(Rect(center, radius = size.width / 2f)) }
                clipPath(circle) { drawImage(art, dstSize = dst, filterQuality = FilterQuality.High) }
                rotate(angle.value, pivot = HAMMER_PIVOT * k) {
                    drawImage(hammer, dstSize = dst, filterQuality = FilterQuality.High)
                }
            }
        }
    }
}
