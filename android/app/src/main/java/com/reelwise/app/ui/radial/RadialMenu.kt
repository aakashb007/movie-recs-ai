package com.reelwise.app.ui.radial

import android.graphics.CornerPathEffect
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.ThumbDown
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.reelwise.app.data.model.Signal
import com.reelwise.app.data.model.TitleSummary
import com.reelwise.app.ui.components.ArtworkImage
import com.reelwise.app.ui.components.Haptics
import com.reelwise.app.ui.components.Tmdb
import com.reelwise.app.ui.components.rememberHaptics
import com.reelwise.app.ui.theme.BrandRed
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class RadialOption(val label: String, val icon: ImageVector, val onSelect: () -> Unit, val active: Boolean = false)

data class RadialRequest(val title: TitleSummary, val options: List<RadialOption>)

/** The quick actions every title gets, in petal order clockwise from the top. */
fun titleOptions(
    title: TitleSummary,
    saved: Boolean,
    onOpen: (TitleSummary) -> Unit,
    onRate: (TitleSummary, Signal) -> Unit,
    onToggleSaved: (TitleSummary) -> Unit,
): List<RadialOption> = listOf(
    RadialOption("Details", Icons.Rounded.Info, { onOpen(title) }),
    RadialOption("Loved it", Icons.Rounded.Favorite, { onRate(title, Signal.LIKE) }),
    RadialOption("More like this", Icons.Rounded.AutoAwesome, { onRate(title, Signal.MORE) }),
    RadialOption(if (saved) "On my list" else "My list", if (saved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkAdd, { onToggleSaved(title) }, active = saved),
    RadialOption("Seen it", Icons.Rounded.Visibility, { onRate(title, Signal.SEEN) }),
    RadialOption("Not for me", Icons.Rounded.ThumbDown, { onRate(title, Signal.DISLIKE) }),
    RadialOption("Not interested", Icons.Rounded.Block, { onRate(title, Signal.DISMISS) }),
)

/**
 * One circle menu for the whole app. Posters open it with a long press; the finger then slides
 * onto a petal (a tick on every change) and lifting it chooses. Lifting in the middle leaves the
 * menu open so petals can be tapped instead.
 */
@Stable
class RadialMenuState {
    var request by mutableStateOf<RadialRequest?>(null)
        private set
    var hovered by mutableIntStateOf(-1)
        private set
    internal var center = Offset.Zero
    internal var innerRadius = 0f
    private var haptics: Haptics? = null

    val isOpen: Boolean get() = request != null

    fun open(request: RadialRequest, haptics: Haptics?) {
        this.haptics = haptics
        this.request = request
        hovered = -1
        haptics?.open()
    }

    fun move(pointer: Offset) {
        val i = segmentAt(pointer)
        if (i != hovered) {
            hovered = i
            if (i >= 0) haptics?.tick()
        }
    }

    fun release() {
        if (hovered >= 0) select(hovered) // else stay open for taps
    }

    fun select(index: Int) {
        val option = request?.options?.getOrNull(index) ?: return
        haptics?.confirm()
        close()
        option.onSelect()
    }

    fun close() {
        request = null
        hovered = -1
    }

    /** Which petal a point (in root coordinates) is over: by angle, anywhere outside the centre. */
    fun segmentAt(p: Offset): Int {
        val n = request?.options?.size ?: return -1
        val d = p - center
        if (d.getDistance() < innerRadius) return -1
        val deg = Math.toDegrees(atan2(d.y, d.x).toDouble())
        val step = 360.0 / n
        val fromTop = ((deg + 90 + step / 2) % 360 + 360) % 360
        return floor(fromTop / step).toInt().coerceIn(0, n - 1)
    }
}

val LocalRadialMenu = staticCompositionLocalOf { RadialMenuState() }

/** Long press opens the circle menu for this element; slide and lift to choose. */
fun Modifier.radialMenu(request: () -> RadialRequest): Modifier = composed {
    val state = LocalRadialMenu.current
    val haptics = rememberHaptics()
    val build by rememberUpdatedState(request)
    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    this
        .onGloballyPositioned { coords = it }
        .semantics {
            customActions = listOf(CustomAccessibilityAction("Quick actions") { state.open(build(), haptics); true })
        }
        .pointerInput(Unit) {
            detectDragGesturesAfterLongPress(
                onDragStart = { state.open(build(), haptics) },
                onDrag = { change, _ ->
                    change.consume()
                    coords?.let { state.move(it.localToRoot(change.position)) }
                },
                onDragEnd = { state.release() },
                onDragCancel = { state.release() },
            )
        }
}

/** Draw this above everything else, filling the window. */
@Composable
fun RadialMenuHost(state: RadialMenuState) {
    var last by remember { mutableStateOf<RadialRequest?>(null) }
    state.request?.let { last = it }
    BackHandler(enabled = state.isOpen) { state.close() }
    AnimatedVisibility(state.isOpen, enter = fadeIn(tween(160)), exit = fadeOut(tween(160))) {
        val request = last ?: return@AnimatedVisibility
        // Behind the menu the app is blurred (Android 12+); older phones get a darker scrim instead.
        val scrim = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) 0.2f else 0.55f
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val i = state.segmentAt(p)
                        if (i >= 0) state.select(i) else state.close()
                    }
                },
        ) {
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val density = LocalDensity.current
                val outer = min(min(maxWidth.value, maxHeight.value) * 0.44f, 178f).dp
                val inner = outer * 0.40f
                val centerPx = with(density) { Offset(maxWidth.toPx() / 2, maxHeight.toPx() / 2) }
                LaunchedEffect(centerPx, inner) {
                    state.center = centerPx
                    state.innerRadius = with(density) { inner.toPx() }
                }
                AnimatedVisibility(
                    visible = true,
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMediumLow), initialScale = 0.6f) + fadeIn(),
                    exit = scaleOut() + fadeOut(),
                    modifier = Modifier.align(Alignment.Center),
                ) {
                    Wheel(request, state.hovered, outer, inner)
                }
            }
        }
    }
}

@Composable
private fun Wheel(request: RadialRequest, hovered: Int, outer: androidx.compose.ui.unit.Dp, inner: androidx.compose.ui.unit.Dp) {
    val n = request.options.size
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    // Frosted glass: low-opacity fills over the blurred app, bright hairline edges and a diagonal sheen.
    val ring = Color.White.copy(alpha = if (dark) 0.07f else 0.22f)
    val petal = Color.White.copy(alpha = if (dark) 0.14f else 0.38f)
    val activePetal = BrandRed.copy(alpha = 0.28f)
    val hoverPetal = BrandRed.copy(alpha = 0.78f)
    val edge = Color.White.copy(alpha = if (dark) 0.35f else 0.75f)
    val onPetal = if (dark) Color.White else Color(0xFF141414)
    val grows = List(n) { i -> animateFloatAsState(if (i == hovered) 1f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "petal$i") }
    val density = LocalDensity.current
    val size = outer * 2 + 28.dp

    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Offset(this.size.width / 2, this.size.height / 2)
            val outerPx = outer.toPx()
            val innerPx = inner.toPx() + 6.dp.toPx()
            val ringR = outerPx + 10.dp.toPx()
            // The glass disc behind the petals, with a light rim.
            drawCircle(ring, radius = ringR, center = c)
            drawCircle(
                Brush.linearGradient(
                    0f to Color.White.copy(alpha = if (dark) 0.16f else 0.45f),
                    0.45f to Color.Transparent,
                    1f to Color.White.copy(alpha = if (dark) 0.05f else 0.15f),
                    start = Offset(c.x - ringR, c.y - ringR),
                    end = Offset(c.x + ringR, c.y + ringR),
                ),
                radius = ringR,
                center = c,
            )
            drawCircle(edge, radius = ringR, center = c, style = Stroke(1.2.dp.toPx()))

            val gap = 7.dp.toPx()
            val step = 360f / n
            val corner = CornerPathEffect(16.dp.toPx())
            val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                pathEffect = corner
                style = android.graphics.Paint.Style.FILL
            }
            val sheenPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                pathEffect = corner
                style = android.graphics.Paint.Style.FILL
                shader = android.graphics.LinearGradient(
                    c.x - outerPx, c.y - outerPx, c.x + outerPx * 0.2f, c.y + outerPx * 0.2f,
                    Color.White.copy(alpha = if (dark) 0.22f else 0.5f).toArgb(),
                    Color.Transparent.toArgb(),
                    android.graphics.Shader.TileMode.CLAMP,
                )
            }
            val edgePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                pathEffect = corner
                style = android.graphics.Paint.Style.STROKE
                strokeWidth = 1.dp.toPx()
                color = edge.toArgb()
            }
            for (i in 0 until n) {
                val grow = grows[i].value
                val r = outerPx + grow * 10.dp.toPx()
                val start = -90f - step / 2 + i * step
                val og = Math.toDegrees((gap / r).toDouble()).toFloat()
                val ig = Math.toDegrees((gap / innerPx).toDouble()).toFloat()
                val path = Path().apply {
                    arcTo(Rect(c, r), start + og / 2, step - og, forceMoveTo = true)
                    arcTo(Rect(c, innerPx), start + step - ig / 2, -(step - ig), forceMoveTo = false)
                    close()
                }.asAndroidPath()
                fillPaint.color = when {
                    i == hovered -> hoverPetal
                    request.options[i].active -> activePetal
                    else -> petal
                }.toArgb()
                drawIntoCanvas {
                    it.nativeCanvas.drawPath(path, fillPaint)
                    it.nativeCanvas.drawPath(path, sheenPaint)
                    it.nativeCanvas.drawPath(path, edgePaint)
                }
            }
        }
        // Icons and labels sit in the middle of each petal.
        val mid = (outer + inner + 6.dp) / 2
        request.options.forEachIndexed { i, option ->
            val angle = (-90.0 + i * 360.0 / n) * PI / 180
            val grow = grows[i].value
            val radiusPx = with(density) { (mid + 5.dp * grow).toPx() }
            val x = (cos(angle) * radiusPx).roundToInt()
            val y = (sin(angle) * radiusPx).roundToInt()
            val selected = i == hovered
            Column(
                Modifier
                    .offset { IntOffset(x, y) }
                    .width(84.dp)
                    .graphicsLayer { scaleX = 1f + 0.12f * grow; scaleY = 1f + 0.12f * grow }
                    .semantics { contentDescription = option.label; role = Role.Button },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(option.icon, null, tint = if (selected) Color.White else if (option.active) BrandRed else onPetal, modifier = Modifier.size(24.dp))
                Text(
                    option.label,
                    color = if (selected) Color.White else onPetal,
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(Color.Black.copy(alpha = if (dark) 0.6f else 0.15f), blurRadius = 6f),
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        // The title in the middle; it names the petal under the finger.
        Box(
            Modifier
                .size(inner * 2 - 8.dp)
                .clip(CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            ArtworkImage(Tmdb.poster(request.title.posterPath, "w185"), request.title.title, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Black.copy(alpha = 0.7f)))))
            Text(
                request.options.getOrNull(hovered)?.label ?: request.title.title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(10.dp),
            )
        }
    }
}
