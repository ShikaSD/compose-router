package com.github.zsoltk.compose.transition

import androidx.animation.FloatPropKey
import androidx.animation.MutableTransitionState
import androidx.animation.TransitionDefinition
import androidx.animation.TransitionState
import androidx.animation.transitionDefinition
import androidx.compose.Composable
import androidx.compose.key
import androidx.compose.remember
import androidx.ui.animation.Transition
import androidx.ui.core.Draw
import androidx.ui.core.Layout
import androidx.ui.unit.px
import com.github.zsoltk.compose.transition.AnimationParams.Opacity
import com.github.zsoltk.compose.transition.AnimationParams.Rotation
import com.github.zsoltk.compose.transition.AnimationParams.X
import com.github.zsoltk.compose.transition.AnimationParams.Y
import com.github.zsoltk.compose.transition.TransitionStates.Finish
import com.github.zsoltk.compose.transition.TransitionStates.Start
import kotlin.math.roundToInt

object AnimationParams {
    val X = FloatPropKey()
    val Y = FloatPropKey()
    val Opacity = FloatPropKey()
    val Rotation = FloatPropKey()
}

enum class TransitionStates {
    Start, Finish
}

@Composable
fun <T : Any> AnimateChange(
    current: T,
    enterAnim: TransitionDefinition<TransitionStates>,
    exitAnim: TransitionDefinition<TransitionStates>,
    children: @Composable() (T) -> Unit
) {
    val animState = remember { AnimationState<T>() }
    val transitionDefinition = remember {
        TransitionDef(
            enterTransition = enterAnim.fillDefault(),
            exitTransition = exitAnim.fillDefault()
        )
    }
    if (animState.current != current) {
        animState.current = current
        val keys = animState.items.map { it.key }.toMutableSet()
        animState.items.clear()
        keys.mapTo(animState.items) { key ->
            AnimationItem(key) { children ->
                Transition(
                    definition = transitionDefinition.exitTransition,
                    initState = Start,
                    toState = Finish,
                    onStateChangeFinished = {
                        if (it == Finish && animState.current == current) {
                            animState.items.removeAll { it.key != current }
                        }
                    }
                ) {
                    children(it)
                }
            }
        }

        animState.items += AnimationItem(current) { children ->
            Transition(
                definition = transitionDefinition.enterTransition,
                initState = if (animState.items.size == 1) Finish else Start,
                toState = Finish
            ) {
                children(it)
            }
        }
    }

    val layoutContent = @Composable() {
        animState.items.forEach { item ->
            key(item.key) {
                item.transition { transitionState ->
                    val composable = @Composable() {
                        children(item.key)
                    }
                    val xFraction = transitionState[X]
                    val yFraction = transitionState[Y]
                    val opacityFraction = transitionState[Opacity]
                    val rotationDeg = transitionState[Rotation]
                    Draw(children = composable) { canvas, parentSize ->
                        canvas.nativeCanvas.saveLayerAlpha(
                            0f,
                            0f,
                            parentSize.width.value,
                            parentSize.height.value,
                            (opacityFraction * 255f).roundToInt()
                        )
                        val startX = parentSize.width.value
                        val startY = parentSize.height.value
                        canvas.translate(startX * xFraction, startY * yFraction)
                        canvas.nativeCanvas.rotate(
                            rotationDeg,
                            parentSize.width.value / 2,
                            parentSize.height.value / 2
                        )
                        drawChildren()
                        canvas.restore()
                    }
                }
            }
        }
    }

    Layout(children = layoutContent) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints) }
        val width = placeables.maxBy { it.width }!!.width
        val height = placeables.maxBy { it.height }!!.height
        layout(width, height) {
            placeables.forEach {
                it.place(0.px, 0.px)
            }
        }
    }
}

private class AnimationState<T> {
    var current: T? = null
    val items = mutableListOf<AnimationItem<T>>()
}

private class AnimationItem<T>(
    val key: T,
    val transition: @Composable() (@Composable() (TransitionState) -> Unit) -> Unit
)

private class TransitionDef(
    val enterTransition: TransitionDefinition<TransitionStates>,
    val exitTransition: TransitionDefinition<TransitionStates>
)

private val Defaults = listOf(
    X to 0f,
    Y to 0f,
    Opacity to 1f,
    Rotation to 0f
)

private fun TransitionDefinition<TransitionStates>.fillDefault(): TransitionDefinition<TransitionStates> {
    TransitionStates.values().forEach {
        val oldState = getStateFor(it) as MutableTransitionState
        // hack state
        Defaults.forEach { (key, value) ->
            try {
                oldState[key] = value
            } catch (e: IllegalArgumentException) {
                // value exists
            }
        }
    }
    return this
}

/**
 * Example
 */
private val enterAnim = transitionDefinition {
    state(Start) {
        this[X] = 1f
        this[Y] = 0f
        this[Opacity] = 1f
        this[Rotation] = 45f
    }

    state(Finish) {
        this[X] = 0f
        this[Y] = 0f
        this[Opacity] = 1f
        this[Rotation] = 0f
    }

    transition {
        X using tween { duration = 300 }
    }
}

private val exitAnim = transitionDefinition {
    state(Start) {
        this[X] = 0f
        this[Y] = 0f
        this[Opacity] = 1f
    }

    state(Finish) {
        this[X] = -1f
        this[Y] = 0f
        this[Opacity] = 1f
    }

    transition {
        X using tween { duration = 300 }
    }
}
