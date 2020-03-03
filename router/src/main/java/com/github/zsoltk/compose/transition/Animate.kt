package com.github.zsoltk.compose.transition

import androidx.animation.FloatPropKey
import androidx.animation.MutableTransitionState
import androidx.animation.TransitionDefinition
import androidx.animation.TransitionState
import androidx.animation.transitionDefinition
import androidx.compose.Composable
import androidx.compose.Immutable
import androidx.compose.Stable
import androidx.compose.key
import androidx.compose.remember
import androidx.ui.animation.Transition
import androidx.ui.core.Clip
import androidx.ui.core.drawWithContent
import androidx.ui.foundation.shape.RectangleShape
import androidx.ui.layout.Container
import androidx.ui.layout.LayoutHeight
import androidx.ui.layout.LayoutWidth
import androidx.ui.layout.Stack
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
        val keys = animState.items.mapNotNull { it.key.takeIf { it != current } }
        animState.items.clear()
        keys.mapTo(animState.items) { key ->
            AnimationItem(key) { children ->
                ExitTransition(transitionDefinition, children) {
                    if (it == Finish && animState.current == current) {
                        animState.items.removeAll { it.key != current }
                    }
                }
            }
        }

        val isFirst = keys.isEmpty()
        animState.items += AnimationItem(current) { children ->
            EnterTransition(
                isFirst,
                transitionDefinition,
                children
            )
        }
    }

    Stack(LayoutWidth.Fill + LayoutHeight.Fill) {
        animState.items.forEach { item ->
            key(item.key) {
                item.transition {
                    AnimateTransition(state = it) {
                        children(item.key)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExitTransition(
    def: TransitionDef,
    children: @Composable() (TransitionState) -> Unit,
    onFinish: (TransitionStates) -> Unit
) {
    Transition(
        definition = def.exitTransition,
        initState = Start,
        toState = Finish,
        onStateChangeFinished = onFinish
    ) {
        children(it)
    }
}

@Composable
private fun EnterTransition(
    firstChild: Boolean,
    def: TransitionDef,
    children: @Composable() (TransitionState) -> Unit
) {
    Transition(
        definition = def.enterTransition,
        initState = if (!firstChild) Start else Finish,
        toState = Finish
    ) {
        children(it)
    }
}

@Composable
private fun RotateTranslateOpacity(state: TransitionState, f: @Composable() () -> Unit) {
    val x = state[X]
    val y = state[Y]
    val rotation = state[Rotation]
    val opacity = state[Opacity]
    val modifier = remember(x, y, rotation, opacity) {
        drawWithContent { canvas, size ->
            canvas.nativeCanvas.saveLayerAlpha(
                0f,
                0f,
                size.width.value,
                size.height.value,
                (opacity * 255).roundToInt()
            )
            val startX = size.width.value
            val startY = size.height.value
            canvas.translate(startX * x, startY * y)
            canvas.nativeCanvas.rotate(
                rotation,
                size.width.value / 2,
                size.height.value / 2
            )
            drawChildren()
            canvas.restore()
        }
    }

    Container(expanded = true, modifier = modifier) {
        f()
    }
    return
}



@Composable
private fun AnimateTransition(
    state: TransitionState,
    f: @Composable() () -> Unit
) {

    Container(expanded = true) {
        Clip(shape = RectangleShape) {
            RotateTranslateOpacity(state = state) {
                f()
            }
        }
    }
}

@Stable
private data class AnimationState<T>(
    var current: T? = null,
    val items: MutableList<AnimationItem<T>> = mutableListOf()
)

@Immutable
private data class AnimationItem<T>(
    val key: T,
    val transition: @Composable() (@Composable() (TransitionState) -> Unit) -> Unit
)

@Immutable
private data class TransitionDef(
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