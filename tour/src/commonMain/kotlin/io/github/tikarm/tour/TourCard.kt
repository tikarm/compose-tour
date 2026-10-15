package io.github.tikarm.tour

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** The card fills the width between the overlay's margins, up to this. */
private val CardMaxWidth = 300.dp
private val CardShape = RoundedCornerShape(16.dp)
private val CardPadding = 16.dp
private val MinTouchTarget = 48.dp
private const val DotAnimationMillis = 200
private val SwipeThreshold = 72.dp
private const val PageSlideMillis = 250
/** How much a swipe toward a missing step still moves the card, as a hint that it is the end. */
private const val EdgeResistance = 0.25f

private object TourText {
    val Counter = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.5.sp)
    val Title = TextStyle(fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
    val Body = TextStyle(fontSize = 14.sp, lineHeight = 20.sp)
    val Skip = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    val Next = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold)
}

/**
 * Colors of [TourCard] and [TourCardText]. [TourCardDefaults.colors] makes one from the current
 * theme.
 *
 * Not a data class, so fields can be added later without breaking apps built against an older
 * version.
 */
@Immutable
public class TourCardColors(
    public val card: Color,
    public val shadow: Color,
    public val title: Color,
    public val body: Color,
    public val counter: Color,
    public val skip: Color,
    public val nextBackground: Color,
    public val nextText: Color,
    public val dotActive: Color,
    public val dotDone: Color,
    public val dotIdle: Color,
) {
    public fun copy(
        card: Color = this.card,
        shadow: Color = this.shadow,
        title: Color = this.title,
        body: Color = this.body,
        counter: Color = this.counter,
        skip: Color = this.skip,
        nextBackground: Color = this.nextBackground,
        nextText: Color = this.nextText,
        dotActive: Color = this.dotActive,
        dotDone: Color = this.dotDone,
        dotIdle: Color = this.dotIdle,
    ): TourCardColors = TourCardColors(
        card = card,
        shadow = shadow,
        title = title,
        body = body,
        counter = counter,
        skip = skip,
        nextBackground = nextBackground,
        nextText = nextText,
        dotActive = dotActive,
        dotDone = dotDone,
        dotIdle = dotIdle,
    )

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TourCardColors) return false
        return card == other.card &&
            shadow == other.shadow &&
            title == other.title &&
            body == other.body &&
            counter == other.counter &&
            skip == other.skip &&
            nextBackground == other.nextBackground &&
            nextText == other.nextText &&
            dotActive == other.dotActive &&
            dotDone == other.dotDone &&
            dotIdle == other.dotIdle
    }

    override fun hashCode(): Int {
        var result = card.hashCode()
        result = 31 * result + shadow.hashCode()
        result = 31 * result + title.hashCode()
        result = 31 * result + body.hashCode()
        result = 31 * result + counter.hashCode()
        result = 31 * result + skip.hashCode()
        result = 31 * result + nextBackground.hashCode()
        result = 31 * result + nextText.hashCode()
        result = 31 * result + dotActive.hashCode()
        result = 31 * result + dotDone.hashCode()
        result = 31 * result + dotIdle.hashCode()
        return result
    }

    override fun toString(): String =
        "TourCardColors(card=$card, shadow=$shadow, title=$title, body=$body, counter=$counter, " +
            "skip=$skip, nextBackground=$nextBackground, nextText=$nextText, dotActive=$dotActive, " +
            "dotDone=$dotDone, dotIdle=$dotIdle)"
}

/**
 * Button texts of [TourCard]. [finish] replaces [next] on the last step.
 *
 * Not a data class, so fields can be added later without breaking apps built against an older
 * version.
 */
@Immutable
public class TourCardLabels(
    public val skip: String = "Skip",
    public val next: String = "Next",
    public val finish: String = "Finish",
) {
    public fun copy(
        skip: String = this.skip,
        next: String = this.next,
        finish: String = this.finish,
    ): TourCardLabels = TourCardLabels(skip = skip, next = next, finish = finish)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TourCardLabels) return false
        return skip == other.skip && next == other.next && finish == other.finish
    }

    override fun hashCode(): Int {
        var result = skip.hashCode()
        result = 31 * result + next.hashCode()
        result = 31 * result + finish.hashCode()
        return result
    }

    override fun toString(): String = "TourCardLabels(skip=$skip, next=$next, finish=$finish)"
}

/** Default values for [TourCard] and [TourCardText]. */
public object TourCardDefaults {
    /**
     * Colors taken from the current [MaterialTheme], so the card follows the app's theme, light or
     * dark. Pass any of them to change just that one.
     */
    @Composable
    @ReadOnlyComposable
    public fun colors(
        card: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadow: Color = Color.Black.copy(alpha = 0.3f),
        title: Color = MaterialTheme.colorScheme.onSurface,
        body: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        counter: Color = MaterialTheme.colorScheme.onSurfaceVariant,
        skip: Color = MaterialTheme.colorScheme.primary,
        nextBackground: Color = MaterialTheme.colorScheme.primary,
        nextText: Color = MaterialTheme.colorScheme.onPrimary,
        dotActive: Color = MaterialTheme.colorScheme.primary,
        dotDone: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
        dotIdle: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
    ): TourCardColors = TourCardColors(
        card = card,
        shadow = shadow,
        title = title,
        body = body,
        counter = counter,
        skip = skip,
        nextBackground = nextBackground,
        nextText = nextText,
        dotActive = dotActive,
        dotDone = dotDone,
        dotIdle = dotIdle,
    )
}

/**
 * A ready made tip for [TourOverlay]: step dots and a counter on top, the step's [content] in the
 * middle, and Skip and Next buttons at the bottom. Swiping the card moves between steps.
 *
 * [step] is 0-based, out of [stepCount]. [content] is shown for each step and slides like a pager
 * when the step changes; [TourCardText] fits it for a title and a message.
 * [stepGroups] splits the dots into groups by size, like a photo carousel; by default it is one
 * group of all steps. Its sizes must add up to [stepCount].
 */
@Composable
public fun TourCard(
    step: Int,
    stepCount: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    labels: TourCardLabels = TourCardLabels(),
    colors: TourCardColors = TourCardDefaults.colors(),
    stepGroups: List<Int> = listOf(stepCount),
    content: @Composable (step: Int) -> Unit,
) {
    require(stepGroups.sum() == stepCount) {
        "stepGroups $stepGroups add up to ${stepGroups.sum()}, but stepCount is $stepCount"
    }
    val isLast = step >= stepCount - 1
    // Moves only the step's content, like a pager; the card itself stays in place
    val swipe = rememberSwipeState()
    val currentStep by rememberUpdatedState(step)
    val currentStepCount by rememberUpdatedState(stepCount)
    val currentOnNext by rememberUpdatedState(onNext)
    val currentOnPrevious by rememberUpdatedState(onPrevious)

    Column(
        modifier = modifier
            .horizontalSwipe(
                state = swipe,
                canSwipe = { direction ->
                    if (direction < 0) currentStep < currentStepCount - 1 else currentStep > 0
                },
                onSwipe = { direction -> if (direction < 0) currentOnNext() else currentOnPrevious() },
            )
            .widthIn(max = CardMaxWidth)
            .fillMaxWidth()
            .shadow(24.dp, CardShape, ambientColor = colors.shadow)
            .clip(CardShape)
            .background(colors.card)
            .padding(CardPadding),
    ) {
        // Progress on top: the dots get the full width, the buttons get the footer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StepDots(step = step + 1, groupSizes = stepGroups, colors = colors)
            BasicText(
                text = "${step + 1} / $stepCount",
                style = TourText.Counter.copy(color = colors.counter),
            )
        }
        AnimatedContent(
            targetState = step,
            modifier = Modifier.offset { IntOffset(swipe.offset.roundToInt(), 0) },
            transitionSpec = {
                // Forward slides in from the right, back from the left, like swiping a pager
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally(tween(PageSlideMillis)) { width -> direction * width } + fadeIn())
                    .togetherWith(
                        slideOutHorizontally(tween(PageSlideMillis)) { width -> -direction * width } +
                            fadeOut()
                    )
                    .using(SizeTransform(clip = false))
            },
            label = "tourStepContent",
        ) { shown ->
            content(shown)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        ) {
            if (!isLast) {
                SkipButton(text = labels.skip, color = colors.skip, onClick = onSkip)
            }
            NextButton(text = if (isLast) labels.finish else labels.next, colors = colors, onClick = onNext)
        }
    }
}

/**
 * A step's [title] and [message] for [TourCard]. Parts of [message] wrapped in `**` show in bold,
 * so a translated string can stress a button's name.
 */
@Composable
public fun TourCardText(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    colors: TourCardColors = TourCardDefaults.colors(),
) {
    Column(modifier = modifier) {
        BasicText(
            text = title,
            style = TourText.Title.copy(color = colors.title),
            modifier = Modifier.padding(top = 4.dp),
        )
        BasicText(
            text = remember(message) { message.withBoldMarks() },
            style = TourText.Body.copy(color = colors.body),
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * The step content's drag. It lives as long as the card, not one gesture or step, so the spring
 * back to the middle is never cut off when the step changes.
 */
@Stable
private class SwipeState(private val scope: CoroutineScope) {
    var offset by mutableFloatStateOf(0f)
        private set

    private var settle: Job? = null

    fun drag(to: Float) {
        settle?.cancel()
        offset = to
    }

    fun release() {
        settle = scope.launch {
            animate(
                initialValue = offset,
                targetValue = 0f,
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> offset = value }
        }
    }
}

@Composable
private fun rememberSwipeState(): SwipeState {
    val scope = rememberCoroutineScope()
    return remember(scope) { SwipeState(scope) }
}

/**
 * Drags [state] with the finger and calls [onSwipe] past [SwipeThreshold], with -1 for a swipe to
 * the left and 1 to the right. Where [canSwipe] says no, the content only stretches a little.
 *
 * Hand made instead of detectHorizontalDragGestures: the overlay consumes every touch to block the
 * screen underneath, which makes the standard detector cancel. Set up once for the card, so the
 * callbacks must read current values themselves.
 */
private fun Modifier.horizontalSwipe(
    state: SwipeState,
    canSwipe: (direction: Int) -> Boolean,
    onSwipe: (direction: Int) -> Unit,
): Modifier = pointerInput(state) {
    val threshold = SwipeThreshold.toPx()
    val touchSlop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var dragged = 0f
        var isDragging = false
        while (true) {
            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed) break
            dragged += change.positionChange().x
            if (!isDragging && abs(dragged) > touchSlop) isDragging = true
            if (isDragging) {
                // Taken by the swipe, so buttons under the finger do not also get a tap
                change.consume()
                val direction = if (dragged < 0) -1 else 1
                state.drag(if (canSwipe(direction)) dragged else dragged * EdgeResistance)
            }
        }
        if (isDragging) {
            val direction = if (dragged < 0) -1 else 1
            if (abs(dragged) > threshold && canSwipe(direction)) onSwipe(direction)
            state.release()
        }
    }
}

/** One dot per step, grouped like a photo carousel. [step] is 1-based. */
@Composable
private fun StepDots(
    step: Int,
    groupSizes: List<Int>,
    colors: TourCardColors,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        var dotNumber = 1
        groupSizes.forEachIndexed { groupIndex, size ->
            if (groupIndex > 0) {
                // Wider gap between groups than between dots
                Spacer(modifier = Modifier.width(8.dp))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(size) {
                    val number = dotNumber++
                    val width by animateDpAsState(
                        targetValue = if (number == step) 14.dp else 6.dp,
                        animationSpec = tween(DotAnimationMillis),
                        label = "stepDot",
                    )
                    Box(
                        modifier = Modifier
                            .size(width = width, height = 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                when {
                                    number == step -> colors.dotActive
                                    number < step -> colors.dotDone
                                    else -> colors.dotIdle
                                }
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkipButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = MinTouchTarget)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = text,
            style = TourText.Skip.copy(color = color),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** A pill about 36dp tall, with a full 48dp touch target around it. */
@Composable
private fun NextButton(text: String, colors: TourCardColors, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .heightIn(min = MinTouchTarget)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(colors.nextBackground)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            BasicText(
                text = text,
                style = TourText.Next.copy(color = colors.nextText),
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
