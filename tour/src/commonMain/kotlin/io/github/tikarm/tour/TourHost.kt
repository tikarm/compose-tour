package io.github.tikarm.tour

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics

/**
 * Shows [content] with a [TourOverlay] on top of it, so the overlay covers everything and does not
 * need to be placed by hand. Wrap a whole screen in it, usually at the root of the window.
 *
 * Fills the space it is given and passes that size on to [content], so wrapping a screen does not
 * change its layout. While the tour is shown, [content] is hidden from screen readers, so they
 * only reach the tip, the same way touches only reach the tip. The other parameters are the same
 * as [TourOverlay]'s.
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
    Box(modifier = modifier.fillMaxSize(), propagateMinConstraints = true) {
        Box(
            modifier = if (target != null) Modifier.clearAndSetSemantics {} else Modifier,
            propagateMinConstraints = true,
        ) {
            content()
        }
        TourOverlay(
            target = target,
            targets = targets,
            scrimColor = scrimColor,
            ringColor = ringColor,
            dimensions = dimensions,
            onDismissRequest = onDismissRequest,
            tip = tip,
        )
    }
}
