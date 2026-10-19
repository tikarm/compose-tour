package io.github.tikarm.tour

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * Shows [content] with a [TourOverlay] on top of it, so the overlay covers everything and does not
 * need to be placed by hand. Wrap a whole screen in it, usually at the root of the window.
 *
 * Fills the space it is given and passes that size on to [content], so wrapping a screen does not
 * change its layout. While the tour is shown, screen readers and keyboard focus only reach the
 * tip, the same way touches do: [content] is hidden from screen readers, and focus moving into it
 * goes to the tip instead. The other parameters are the same as [TourOverlay]'s.
 */
@Composable
public fun <K : Any> TourHost(
    target: K?,
    targets: TourTargets<K>,
    tip: @Composable (K) -> Unit,
    modifier: Modifier = Modifier,
    scrimColor: Color = TourOverlayDefaults.ScrimColor,
    ringColor: Color? = null,
    dimensions: TourOverlayDimensions = TourOverlayDefaults.Dimensions,
    onDismissRequest: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val isShown = target != null
    val tipFocus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    var contentHasFocus by remember { mutableStateOf(false) }

    // A focused button under the overlay would still react to Enter, so focus leaves the screen
    // when the tour starts. Only when something there has focus, as touch input usually leaves
    // nothing focused and should not get a focus highlight on the tip.
    LaunchedEffect(isShown) {
        if (!isShown || !contentHasFocus) return@LaunchedEffect
        // The tip is composed but not yet placed, and focus only finds placed elements
        withFrameNanos {}
        if (!tipFocus.requestFocus()) focusManager.clearFocus()
    }

    Box(modifier = modifier.fillMaxSize(), propagateMinConstraints = true) {
        Box(
            modifier = Modifier
                .onFocusChanged { contentHasFocus = it.hasFocus }
                .focusProperties {
                    if (isShown) {
                        // Tab and arrow keys go to the tip, or stay put if it has nothing to focus
                        onEnter = { if (!tipFocus.requestFocus()) cancelFocusChange() }
                    }
                }
                .focusGroup()
                // Hidden from screen readers too, like touches are blocked by the overlay
                .then(if (isShown) Modifier.clearAndSetSemantics {} else Modifier),
            propagateMinConstraints = true,
        ) {
            content()
        }
        TourOverlay(
            target = target,
            targets = targets,
            modifier = Modifier,
            scrimColor = scrimColor,
            ringColor = ringColor,
            dimensions = dimensions,
            onDismissRequest = onDismissRequest,
            tipFocusRequester = tipFocus,
            tip = tip,
        )
    }
}
