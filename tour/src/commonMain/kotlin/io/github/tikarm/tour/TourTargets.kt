package io.github.tikarm.tour

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.node.GlobalPositionAwareModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.requireLayoutCoordinates

/**
 * Where each tour target is, in root coordinates of its window, keyed by the tour's own step type.
 * Use one instance per window: a dialog or bottom sheet opens in its own window and needs its own.
 */
@Stable
public class TourTargets<K> {
    private val bounds = mutableStateMapOf<K, Rect>()

    // Which element registered each key. Two elements can briefly share one (a list row handing
    // the target to another), so only the current owner may remove it.
    private val owners = mutableMapOf<K, Any>()

    public fun boundsOf(key: K): Rect? = bounds[key]

    internal fun update(key: K, rect: Rect, owner: Any) {
        owners[key] = owner
        // Targets report on every layout pass; only a real move should notify the overlay
        if (bounds[key] != rect) bounds[key] = rect
    }

    internal fun remove(key: K, owner: Any) {
        if (owners[key] !== owner) return
        owners.remove(key)
        bounds.remove(key)
    }
}

@Composable
public fun <K> rememberTourTargets(): TourTargets<K> = remember { TourTargets() }

/** Marks this element as the target of [key]. It stops being a target once it leaves the screen. */
public fun <K> Modifier.tourTarget(key: K, targets: TourTargets<K>): Modifier =
    this then TourTargetElement(key, targets)

private data class TourTargetElement<K>(
    val key: K,
    val targets: TourTargets<K>,
) : ModifierNodeElement<TourTargetNode<K>>() {
    override fun create() = TourTargetNode(key, targets)

    override fun update(node: TourTargetNode<K>) = node.update(key, targets)
}

private class TourTargetNode<K>(
    private var key: K,
    private var targets: TourTargets<K>,
) : Modifier.Node(), GlobalPositionAwareModifierNode {

    override fun onGloballyPositioned(coordinates: LayoutCoordinates) {
        targets.update(key, coordinates.boundsInRoot(), owner = this)
    }

    override fun onDetach() {
        targets.remove(key, owner = this)
    }

    fun update(key: K, targets: TourTargets<K>) {
        if (key == this.key && targets === this.targets) return
        this.targets.remove(this.key, owner = this)
        this.key = key
        this.targets = targets
        // Register under the new key now, instead of waiting for the next layout pass
        if (isAttached) {
            val coordinates = requireLayoutCoordinates()
            if (coordinates.isAttached) targets.update(key, coordinates.boundsInRoot(), owner = this)
        }
    }
}
