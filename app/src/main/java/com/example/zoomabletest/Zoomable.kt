package com.example.zoomabletest


import androidx.annotation.FloatRange
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
/**
 * The source of the code is: https://github.com/Tlaster/Zoomable#zoomable
 *
 * Had to copy the master branch here because an important feature we want to use was not included in
 * the latest version and there was no scheduled release for the project.
 */

/**
 * A zoomable layout that can handle zoom in and out with drag support.
 *
 * @param state the state object to be used to observe the [Zoomable] state.
 * @param modifier the modifier to apply to this layout.
 * @param doubleTapScale a function called on double tap gesture, will scale to returned value.
 * @param finishDragNotConsumeDirection the direction does not consume touch when scrolling to the edge.
 * @param content a block which describes the content.
 */
@Composable
fun Zoomable(
    state: ZoomableState,
    modifier: Modifier = Modifier,
    enable: Boolean = true,
    doubleTapScale: (() -> Float)? = null,
    onSingleTap: (() -> Unit)? = null,
    finishDragNotConsumeDirection: ZoomableConsumeDirection? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(
        modifier = modifier,
    ) {
        var childWidth by remember { mutableStateOf(0) }
        var childHeight by remember { mutableStateOf(0) }
        LaunchedEffect(
            childHeight,
            childWidth,
            state.scale,
        ) {
            val maxX = (childWidth * state.scale - constraints.maxWidth)
                .coerceAtLeast(0F) / 2F
            val maxY = (childHeight * state.scale - constraints.maxHeight)
                .coerceAtLeast(0F) / 2F
            state.updateBounds(maxX, maxY)
        }
        val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
            if (enable) {
                scope.launch {
                    state.onZoomChange(zoomChange)
                    // we want also support double finger dragging
                    state.drag(panChange)
                }
            }
        }
        val doubleTapModifier = if (doubleTapScale != null && enable) {
            Modifier.pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSingleTap?.invoke()},
                    onDoubleTap = {
                        scope.launch {
                            state.animateScaleTo(doubleTapScale())
                        }
                    }
                )
            }
        } else {
            Modifier
        }
        Box(
            modifier = Modifier
                .pointerInput(Unit) {
                    detectDrag(
                        onDrag = { change, dragAmount ->
                            if (enable) {
                                when (finishDragNotConsumeDirection) {
                                    ZoomableConsumeDirection.Horizontal -> {
                                        if (state.isHorizontalDragFinis(dragAmount).not()) {
                                            change.consumePositionChange()
                                        }
                                    }

                                    ZoomableConsumeDirection.Vertical -> {
                                        if (state.isVerticalDragFinish(dragAmount).not()) {
                                            change.consumePositionChange()
                                        }
                                    }

                                    null -> {
                                        change.consumePositionChange()
                                    }
                                }
                            }
                            if (state.zooming && enable) {
                                scope.launch {
                                    state.drag(dragAmount)
                                    state.addPosition(
                                        change.uptimeMillis,
                                        change.position
                                    )
                                }
                            }
                        },
                        onDragCancel = {
                            if (enable) {
                                state.resetTracking()
                            }
                        },
                        onDragEnd = {
                            if (state.zooming && enable) {
                                scope.launch {
                                    state.dragEnd()
                                }
                            }
                        },
                    )
                }
                .then(doubleTapModifier)
                .transformable(state = transformableState)
                .layout { measurable, constraints ->
                    val placeable =
                        measurable.measure(constraints = constraints)
                    childHeight = placeable.height
                    childWidth = placeable.width
                    layout(
                        width = constraints.maxWidth,
                        height = constraints.maxHeight
                    ) {
                        placeable.placeRelativeWithLayer(
                            (constraints.maxWidth - placeable.width) / 2,
                            (constraints.maxHeight - placeable.height) / 2
                        ) {
                            scaleX = state.scale
                            scaleY = state.scale
                            translationX = state.translateX
                            translationY = state.translateY
                        }
                    }
                }
        ) {
            content.invoke(this)
        }
    }
}

enum class ZoomableConsumeDirection {
    Horizontal,
    Vertical,
    ;
}

private suspend fun PointerInputScope.detectDrag(
    onDragStart: (Offset) -> Unit = { },
    onDragEnd: () -> Unit = { },
    onDragCancel: () -> Unit = { },
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    forEachGesture {
        awaitPointerEventScope {
            val down = awaitFirstDown(requireUnconsumed = false)
            var drag: PointerInputChange?
            do {
                drag = awaitTouchSlopOrCancellation(down.id, onDrag)
            } while (drag != null && !drag.positionChangeConsumed())
            if (drag != null) {
                onDragStart.invoke(drag.position)
                if (
                    !drag(drag.id) {
                        onDrag(it, it.positionChange())
                    }
                ) {
                    onDragCancel()
                } else {
                    onDragEnd()
                }
            }
        }
    }
}

/**
 * Create a [ZoomableState] that is remembered across compositions.
 *
 * Changes to the provided values for [minScale] and [maxScale] will **not** result
 * in the state being recreated or changed in any way if it has already been created.
 *
 * @param minScale the minimum scale value for [ZoomableState.minScale]
 * @param maxScale the maximum scale value for [ZoomableState.maxScale]
 */
@Composable
fun rememberZoomableState(
    @FloatRange(from = 0.0) minScale: Float = 1f,
    @FloatRange(from = 0.0) maxScale: Float = Float.MAX_VALUE,
): ZoomableState = rememberSaveable(
    saver = ZoomableState.Saver
) {
    ZoomableState(
        minScale = minScale,
        maxScale = maxScale,
    )
}

/**
 * A state object that can be hoisted to observe scale and translate for [Zoomable].
 *
 * In most cases, this will be created via [rememberZoomableState].
 *
 * @param minScale the minimum scale value for [ZoomableState.minScale]
 * @param maxScale the maximum scale value for [ZoomableState.maxScale]
 * @param initialTranslateX the initial translateX value for [ZoomableState.translateX]
 * @param initialTranslateY the initial translateY value for [ZoomableState.translateY]
 * @param initialScale the initial scale value for [ZoomableState.scale]
 */
@Stable
class ZoomableState(
    @FloatRange(from = 0.0) val minScale: Float = 1f,
    @FloatRange(from = 0.0) val maxScale: Float = Float.MAX_VALUE,
    @FloatRange(from = 0.0) initialTranslateX: Float = 0f,
    @FloatRange(from = 0.0) initialTranslateY: Float = 0f,
    @FloatRange(from = 0.0) initialScale: Float = minScale,
) {
    private val velocityTracker = VelocityTracker()
    private val _translateY = Animatable(initialTranslateY)
    private val _translateX = Animatable(initialTranslateX)
    private val _scale = Animatable(initialScale)

    init {
        require(minScale < maxScale) { "minScale must be < maxScale" }
    }

    /**
     * The current scale value for [Zoomable]
     */
    @get:FloatRange(from = 0.0)
    val scale: Float
        get() = _scale.value

    /**
     * The current translateY value for [Zoomable]
     */
    @get:FloatRange(from = 0.0)
    val translateY: Float
        get() = _translateY.value

    /**
     * The current translateX value for [Zoomable]
     */
    @get:FloatRange(from = 0.0)
    val translateX: Float
        get() = _translateX.value

    internal val zooming: Boolean
        get() = scale > minScale

    /**
     * Instantly sets scale of [Zoomable] to given [scale]
     */
    suspend fun snapScaleTo(scale: Float) = coroutineScope {
        _scale.snapTo(scale.coerceIn(minimumValue = minScale, maximumValue = maxScale))
    }

    /**
     * Animates scale of [Zoomable] to given [scale]
     */
    suspend fun animateScaleTo(
        scale: Float,
        animationSpec: AnimationSpec<Float> = spring(),
        initialVelocity: Float = 0f,
    ) = coroutineScope {
        _scale.animateTo(
            targetValue = scale.coerceIn(minimumValue = minScale, maximumValue = maxScale),
            animationSpec = animationSpec,
            initialVelocity = initialVelocity,
        )
    }

    private suspend fun fling(velocity: Offset) = coroutineScope {
        launch {
            _translateY.animateDecay(
                velocity.y / 2f,
                exponentialDecay()
            )
        }
        launch {
            _translateX.animateDecay(
                velocity.x / 2f,
                exponentialDecay()
            )
        }
    }

    internal suspend fun drag(dragDistance: Offset) = coroutineScope {
        launch {
            _translateY.snapTo((_translateY.value + dragDistance.y))
        }
        launch {
            _translateX.snapTo((_translateX.value + dragDistance.x))
        }
    }

    internal suspend fun dragEnd() {
        val velocity = velocityTracker.calculateVelocity()
        fling(Offset(velocity.x, velocity.y))
    }

    internal suspend fun updateBounds(maxX: Float, maxY: Float) = coroutineScope {
        _translateY.updateBounds(-maxY, maxY)
        _translateX.updateBounds(-maxX, maxX)
    }

    internal suspend fun onZoomChange(zoomChange: Float) = snapScaleTo(scale * zoomChange)

    internal fun addPosition(timeMillis: Long, position: Offset) {
        velocityTracker.addPosition(timeMillis = timeMillis, position = position)
    }

    internal fun resetTracking() {
        velocityTracker.resetTracking()
    }

    internal fun isHorizontalDragFinis(dragDistance: Offset): Boolean {
        val lowerBounds = _translateX.lowerBound ?: return false
        val upperBounds = _translateX.upperBound ?: return false
        if (lowerBounds == 0f && upperBounds == 0f) return true

        val newPosition = _translateX.value + dragDistance.x
        if (newPosition <= lowerBounds) {
            return true
        }

        if (newPosition >= upperBounds) {
            return true
        }
        return false
    }

    internal fun isVerticalDragFinish(dragDistance: Offset): Boolean {
        val lowerBounds = _translateY.lowerBound ?: return false
        val upperBounds = _translateY.upperBound ?: return false
        if (lowerBounds == 0f && upperBounds == 0f) return true

        val newPosition = _translateY.value + dragDistance.y
        if (newPosition <= lowerBounds) {
            return true
        }

        if (newPosition >= upperBounds) {
            return true
        }
        return false
    }

    override fun toString(): String = "ZoomableState(" +
            "minScale=$minScale, " +
            "maxScale=$maxScale, " +
            "translateY=$translateY" +
            "translateX=$translateX" +
            "scale=$scale" +
            ")"

    companion object {
        /**
         * The default [Saver] implementation for [ZoomableState].
         */
        val Saver: Saver<ZoomableState, *> = listSaver(
            save = {
                listOf(
                    it.translateX,
                    it.translateY,
                    it.scale,
                    it.minScale,
                    it.maxScale,
                )
            },
            restore = {
                ZoomableState(
                    initialTranslateX = it[0],
                    initialTranslateY = it[1],
                    initialScale = it[2],
                    minScale = it[3],
                    maxScale = it[4],
                )
            }
        )
    }
}
