package io.github.tikarm.tour

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.TwoWayConverter
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.collectLatest

private const val FadeMillis = 200
private const val TipEnterMillis = 220
private const val TipInitialScale = 0.96f

/**
 * Sizes and spacing of [TourOverlay]'s cutout and tip.
 *
 * Not a data class, so fields can be added later without breaking apps built against an older
 * version.
 */
@Immutable
public class TourOverlayDimensions(
    /** Space between the target's bounds and the cutout's edge. */
    public val cutoutPadding: Dp = 6.dp,
    public val cutoutRadius: Dp = 12.dp,
    /** Keeps the cutout off the window edges, so a target at the edge does not get a clipped highlight. */
    public val cutoutEdgeMargin: Dp = 4.dp,
    public val ringWidth: Dp = 2.dp,
    /**
     * The tip stays at least this far from the overlay's edges, and from the system bars, display
     * cutouts and keyboard where they cover the overlay.
     */
    public val tipMargin: Dp = 16.dp,
    /** Space between the cutout and the tip. */
    public val tipGap: Dp = 12.dp,
) {
    public fun copy(
        cutoutPadding: Dp = this.cutoutPadding,
        cutoutRadius: Dp = this.cutoutRadius,
        cutoutEdgeMargin: Dp = this.cutoutEdgeMargin,
        ringWidth: Dp = this.ringWidth,
        tipMargin: Dp = this.tipMargin,
        tipGap: Dp = this.tipGap,
    ): TourOverlayDimensions = TourOverlayDimensions(
        cutoutPadding = cutoutPadding,
        cutoutRadius = cutoutRadius,
        cutoutEdgeMargin = cutoutEdgeMargin,
        ringWidth = ringWidth,
        tipMargin = tipMargin,
        tipGap = tipGap,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TourOverlayDimensions) return false
        return cutoutPadding == other.cutoutPadding &&
            cutoutRadius == other.cutoutRadius &&
            cutoutEdgeMargin == other.cutoutEdgeMargin &&
            ringWidth == other.ringWidth &&
            tipMargin == other.tipMargin &&
            tipGap == other.tipGap
    }

    override fun hashCode(): Int {
        var result = cutoutPadding.hashCode()
        result = 31 * result + cutoutRadius.hashCode()
        result = 31 * result + cutoutEdgeMargin.hashCode()
        result = 31 * result + ringWidth.hashCode()
        result = 31 * result + tipMargin.hashCode()
        result = 31 * result + tipGap.hashCode()
        return result
    }

    override fun toString(): String =
        "TourOverlayDimensions(cutoutPadding=$cutoutPadding, cutoutRadius=$cutoutRadius, " +
            "cutoutEdgeMargin=$cutoutEdgeMargin, ringWidth=$ringWidth, tipMargin=$tipMargin, tipGap=$tipGap)"
}

/** Default values for [TourOverlay] and [TourHost]. */
public object TourOverlayDefaults {
    /** The current [MaterialTheme]'s scrim, darker than a dialog's so the cutout stands out. */
    public val ScrimColor: Color
        @Composable
        @ReadOnlyComposable
        get() = MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)

    /** The default sizes; use [TourOverlayDimensions.copy] on it to change just some of them. */
    public val Dimensions: TourOverlayDimensions = TourOverlayDimensions()
}

/**
 * Dims everything except [target]'s element and shows [tip] next to it. Covers the whole window it
 * is placed in and blocks touches underneath, so the [tip] content decides how the tour moves on.
 *
 * A null [target] hides the overlay. It fades in and out, and moving to another target glides the
 * cutout there, with the tip travelling along. How the tip's content changes is up to [tip].
 * When the target is not on screen (a hidden or scrolled away element), the tip shows in the middle
 * without a cutout.
 *
 * Targets are found in [targets], which must belong to the same window as the overlay: a dialog or
 * bottom sheet opens in its own window and needs its own [TourTargets] and overlay.
 *
 * [ringColor] outlines the cutout; null leaves it without an outline.
 *
 * [onDismissRequest] is called on the system back action (Esc on desktop) while the overlay is
 * shown, usually to end the tour. Null leaves back handling to the app.
 *
 * Screen readers announce the tip when it appears or changes. The overlay cannot hide the elements
 * under it from screen readers; [TourHost] does that for the screen it wraps.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
public fun <K : Any> TourOverlay(
    target: K?,
    targets: TourTargets<K>,
    modifier: Modifier = Modifier,
    scrimColor: Color = TourOverlayDefaults.ScrimColor,
    ringColor: Color? = null,
    dimensions: TourOverlayDimensions = TourOverlayDefaults.Dimensions,
    onDismissRequest: (() -> Unit)? = null,
    tip: @Composable (K) -> Unit,
) {
    // Keeps the last target while the overlay fades out after the tour ends
    val lastTarget = remember { LastValue<K>() }
    if (target != null) lastTarget.value = target

    // Added only while the tour is shown: on Android the newest back handler wins, so this one
    // beats any the screen added since it was first composed
    if (onDismissRequest != null && target != null) {
        BackHandler(onBack = onDismissRequest)
    }

    AnimatedVisibility(
        visible = target != null,
        modifier = modifier,
        enter = fadeIn(tween(FadeMillis)),
        exit = fadeOut(tween(FadeMillis)),
    ) {
        val shownTarget = target ?: lastTarget.value ?: return@AnimatedVisibility
        OverlayContent(
            target = shownTarget,
            targets = targets,
            scrimColor = scrimColor,
            ringColor = ringColor,
            dimensions = dimensions,
            // The tip grows in as the overlay opens; changing steps is up to the tip itself
            tipModifier = Modifier.animateEnterExit(
                enter = scaleIn(tween(TipEnterMillis), initialScale = TipInitialScale),
                exit = ExitTransition.None,
            ),
            tip = tip,
        )
    }
}

@Composable
private fun <K : Any> OverlayContent(
    target: K,
    targets: TourTargets<K>,
    scrimColor: Color,
    ringColor: Color?,
    dimensions: TourOverlayDimensions,
    tipModifier: Modifier,
    tip: @Composable (K) -> Unit,
) {
    // Targets are measured from the window root; the overlay may not start there
    var origin by remember { mutableStateOf(Offset.Zero) }
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    var rootSize by remember { mutableStateOf(IntSize.Zero) }
    var tipSize by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val safeInsets = WindowInsets.safeDrawing
    val cutoutPadding = with(density) { dimensions.cutoutPadding.toPx() }
    val edgeMargin = with(density) { dimensions.cutoutEdgeMargin.toPx() }
    val tipMargin = with(density) { dimensions.tipMargin.roundToPx() }
    val tipGap = with(density) { dimensions.tipGap.roundToPx() }

    // Where the tip may go in an overlay of [size]: inside the margins and clear of system bars,
    // notches and the keyboard. Insets are measured from the window's edges, so only the part
    // that reaches into the overlay counts.
    fun tipArea(size: IntSize): IntRect {
        val leftGap = origin.x.roundToInt()
        val topGap = origin.y.roundToInt()
        val rightGap = (rootSize.width - leftGap - size.width).coerceAtLeast(0)
        val bottomGap = (rootSize.height - topGap - size.height).coerceAtLeast(0)
        val left = (safeInsets.getLeft(density, layoutDirection) - leftGap).coerceAtLeast(0) + tipMargin
        val top = (safeInsets.getTop(density) - topGap).coerceAtLeast(0) + tipMargin
        val right = size.width - (safeInsets.getRight(density, layoutDirection) - rightGap).coerceAtLeast(0) - tipMargin
        val bottom = size.height - (safeInsets.getBottom(density) - bottomGap).coerceAtLeast(0) - tipMargin
        return IntRect(left, top, right.coerceAtLeast(left), bottom.coerceAtLeast(top))
    }

    // Read inside the glides only, so moving targets redraw and relayout without recomposing
    fun targetCutout(): Rect? = targets.boundsOf(target)
        // A target scrolled fully out of view has empty bounds, so it counts as not on screen
        ?.takeUnless { it.isEmpty }
        ?.translate(-origin)
        ?.inflate(cutoutPadding)
        ?.keptInside(overlaySize, edgeMargin)

    val cutout = rememberGlidingValue(key = target, converter = Rect.VectorConverter) { targetCutout() }
    // Placed from where the cutout is going, not where it is mid glide, so the tip picks its side
    // (below or above) once per target and glides there instead of jumping sides on the way
    val tipPosition = rememberGlidingValue(key = target, converter = IntOffset.VectorConverter) {
        tipPosition(targetCutout(), tipSize, tipArea(overlaySize), tipGap)
    }
    val scrim = remember { Path() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned {
                origin = it.positionInRoot()
                overlaySize = it.size
                rootSize = it.findRootCoordinates().size
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            }
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            val shownCutout = cutout.value
            val cornerRadius = CornerRadius(dimensions.cutoutRadius.toPx())
            scrim.reset()
            scrim.fillType = PathFillType.EvenOdd
            scrim.addRect(Rect(Offset.Zero, size))
            if (shownCutout != null) {
                scrim.addRoundRect(RoundRect(shownCutout, cornerRadius))
            }
            drawPath(scrim, scrimColor)
            if (shownCutout != null && ringColor != null) {
                drawRoundRect(
                    color = ringColor,
                    topLeft = shownCutout.topLeft,
                    size = shownCutout.size,
                    cornerRadius = cornerRadius,
                    style = Stroke(width = dimensions.ringWidth.toPx()),
                )
            }
        }

        TipPlacement(area = ::tipArea, position = { tipPosition.value ?: IntOffset.Zero }) {
            Box(
                modifier = tipModifier
                    .onSizeChanged { tipSize = it }
                    // Screen readers read the tip out when it appears and when its content changes
                    .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                tip(target)
            }
        }
    }
}

/**
 * A value that glides to its target when the tour moves to another key, and follows the same key's
 * own movement (a bottom sheet sliding up, for example) right away so it never lags behind it.
 * It appears in place when there was nothing shown before, and is null while the target is.
 */
private class GlidingValue<T, V : AnimationVector>(private val converter: TwoWayConverter<T, V>) {
    private var animatable: Animatable<T, V>? = null
    private var isShown by mutableStateOf(false)
    private var isGliding = false

    /** Read in the draw or layout phase, so only that phase repeats while the value moves. */
    val value: T? get() = if (isShown) animatable?.value else null

    suspend fun moveTo(target: T?, startGlide: Boolean) {
        if (target == null) {
            isShown = false
            return
        }
        val current = animatable ?: Animatable(target, converter).also { animatable = it }
        if (!isShown) {
            current.snapTo(target)
            isShown = true
            isGliding = false
            return
        }
        // A glide that gets a new target mid way keeps going toward it with its current speed
        if (startGlide) isGliding = true
        if (isGliding) {
            current.animateTo(
                targetValue = target,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
            isGliding = false
        } else {
            current.snapTo(target)
        }
    }
}

/** One coroutine per [key], following [target] as it changes, instead of one per change. */
@Composable
private fun <K, T, V : AnimationVector> rememberGlidingValue(
    key: K,
    converter: TwoWayConverter<T, V>,
    target: () -> T?,
): GlidingValue<T, V> {
    val glide = remember { GlidingValue(converter) }
    val currentTarget by rememberUpdatedState(target)
    LaunchedEffect(key) {
        var isFirstForKey = true
        snapshotFlow { currentTarget() }.collectLatest { value ->
            val startGlide = isFirstForKey
            isFirstForKey = false
            glide.moveTo(value, startGlide)
        }
    }
    return glide
}

/** Holds a value across recompositions without being state, so writing it never recomposes. */
private class LastValue<T>(var value: T? = null)

private fun Rect.keptInside(size: IntSize, margin: Float): Rect {
    if (size == IntSize.Zero) return this
    val keptLeft = left.coerceAtLeast(margin)
    val keptTop = top.coerceAtLeast(margin)
    // A thin target at the very edge must not end up with its sides swapped
    return Rect(
        left = keptLeft,
        top = keptTop,
        right = right.coerceAtMost(size.width - margin).coerceAtLeast(keptLeft),
        bottom = bottom.coerceAtMost(size.height - margin).coerceAtLeast(keptTop),
    )
}

/**
 * Where the tip goes for [cutout]: below it, above it when there is no room, or centered in [area]
 * without one. Horizontally it is centered on the cutout. It stays within [area] where it fits.
 */
private fun tipPosition(cutout: Rect?, tip: IntSize, area: IntRect, gap: Int): IntOffset {
    val centeredX = cutout?.center?.x?.roundToInt()?.minus(tip.width / 2) ?: (area.center.x - tip.width / 2)
    val x = centeredX.coerceIn(area.left, (area.right - tip.width).coerceAtLeast(area.left))
    val y = when {
        cutout == null -> (area.center.y - tip.height / 2).coerceAtLeast(area.top)
        cutout.bottom + gap + tip.height <= area.bottom -> (cutout.bottom + gap).roundToInt().coerceAtLeast(area.top)
        else -> (cutout.top - gap - tip.height).roundToInt().coerceAtLeast(area.top)
    }
    return IntOffset(x, y)
}

/**
 * Measures the tip to fit the width of [area], which gets the overlay's size, and places it at
 * [position]. Both are read while measuring and placing, so changes skip composition.
 */
@Composable
private fun TipPlacement(area: (IntSize) -> IntRect, position: () -> IntOffset, content: @Composable () -> Unit) {
    Layout(content = content, modifier = Modifier.fillMaxSize()) { measurables, constraints ->
        require(constraints.hasBoundedWidth && constraints.hasBoundedHeight) {
            "TourOverlay needs a bounded size; place it in a layout that gives it one"
        }
        val maxTipWidth = area(IntSize(constraints.maxWidth, constraints.maxHeight)).width
        val tip = measurables.first().measure(Constraints(maxWidth = maxTipWidth))
        layout(constraints.maxWidth, constraints.maxHeight) {
            tip.place(position())
        }
    }
}
