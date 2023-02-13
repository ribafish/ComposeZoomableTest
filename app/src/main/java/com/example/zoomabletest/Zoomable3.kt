package com.example.zoomabletest

import android.util.Log
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.MutatorMutex
import androidx.compose.foundation.gestures.TransformScope
import androidx.compose.foundation.gestures.TransformableState
import androidx.compose.foundation.gestures.rememberTransformableState
import kotlinx.coroutines.coroutineScope
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Note: Copied from https://github.com/Mr-Pine/Zoomables
 *
 * Creates a composable that wraps a given [Composable] and adds zoom, pan, double tap and swipe functionality
 *
 *
 * **NOTE** this Composable's functionality is different from using [Modifier.transformable] in multiple ways:
 * * Nicer zooming behaviour by zooming away from the center of the multitouch (instead of the [Composable] center)
 * * Ability to use swipe gestures when not zoomed in and panning when zoomed in
 * * Provides simple functions for swipe and tap events
 * * Wrapping most of the boilerplate code and providing even simpler functions for images with [ZoomableImage] and [EasyZoomableImage]
 *
 * @param coroutineScope used for smooth asynchronous zoom/pan animations
 * @param zoomableState Contains the current transform states - obtained via [rememberZoomableState]
 * @param dragGesturesEnabled A function with a [ZoomableState3] scope that returns a boolean value to enable/disable dragging gestures (swiping and panning). Returns `true` by default. *Note*: For some use cases it may be required that only panning is possible. Use `{!notTransformed}` in that case
 * @param onSwipeLeft Optional function to run when user swipes from right to left - does nothing by default
 * @param onSwipeRight Optional function to run when user swipes from left to right - does nothing by default
 * @param minimumSwipeDistance Minimum distance the user has to travel on the screen for it to count as swiping
 * @param onDoubleTap Optional function to run when user double taps. Zooms in by 2x to the touch point when scale is currently 1 and zooms out to scale = 1 when zoomed in when `null` (default)
 */

@Composable
public fun Zoomable3(
    coroutineScope: CoroutineScope = rememberCoroutineScope(),
    zoomableState: ZoomableState3 = rememberZoomableState3(),
    dragGesturesEnabled: ZoomableState3.() -> Boolean = { true },
    onSwipeLeft: () -> Unit = { Log.d("Zoomable3", "Swipe left") },
    onSwipeRight: () -> Unit = { Log.d("Zoomable3", "Swipe right")},
    minimumSwipeDistance: Int = 0,
    onDoubleTap: ((Offset) -> Unit)? = null,
    Content: @Composable (BoxScope.() -> Unit),
) {

    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var composableCenter by remember { mutableStateOf(Offset.Zero) }
    var transformOffset by remember { mutableStateOf(Offset.Zero) }

    val doubleTapFunction = onDoubleTap?: {
        if (zoomableState.scale.value != 1f) {
            coroutineScope.launch {
                zoomableState.animateBy(
                    zoomChange = 1 / zoomableState.scale.value,
                    panChange = -zoomableState.offset.value,
                )
            }
            Unit
        } else {
            coroutineScope.launch {
                zoomableState.animateZoomToPosition(2f, position = it, composableCenter)
                //zoomableState.animateZoomBy(2f)
            }
            Unit
        }
    }

    fun onTransformGesture(
        centroid: Offset,
        pan: Offset,
        zoom: Float
    ) {
        val tempOffset = zoomableState.offset.value + pan

        val x0 = centroid.x - composableCenter.x
        val y0 = centroid.y - composableCenter.y

        val hyp0 = sqrt(x0 * x0 + y0 * y0)
        val hyp1 = zoom * hyp0 * (if (x0 > 0) {
            1f
        } else {
            -1f
        })

        val alpha0 = atan(y0 / x0)

        val x1 = cos(alpha0) * hyp1
        val y1 = sin(alpha0) * hyp1

        transformOffset =
            centroid - (composableCenter - tempOffset) - Offset(x1, y1)

        coroutineScope.launch {
            zoomableState.transform {
                transformBy(
                    zoomChange = zoom,
                    panChange = transformOffset - zoomableState.offset.value
                )
            }
        }

    }

    Box(
        Modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = doubleTapFunction
                )
            }
            .pointerInput(Unit) {
                forEachGesture {
                    awaitPointerEventScope {
                        var zoom = 1f
                        var pan = Offset.Zero
                        var pastTouchSlop = false
                        val touchSlop = viewConfiguration.touchSlop
                        var drag: PointerInputChange?
                        var overSlop = Offset.Zero

                        val down = awaitFirstDown(requireUnconsumed = false)


                        var transformEventCounter = 0
                        do {
                            val event = awaitPointerEvent()
                            val canceled = event.changes.fastAny { it.positionChangeConsumed() }
                            var relevant = true
                            if (event.changes.size > 1) {
                                if (!canceled) {
                                    val zoomChange = event.calculateZoom()
                                    val panChange = event.calculatePan()

                                    if (!pastTouchSlop) {
                                        zoom *= zoomChange
                                        pan += panChange

                                        val centroidSize =
                                            event.calculateCentroidSize(useCurrent = false)
                                        val zoomMotion = abs(1 - zoom) * centroidSize
                                        val panMotion = pan.getDistance()

                                        if (zoomMotion > touchSlop ||
                                            panMotion > touchSlop
                                        ) {
                                            pastTouchSlop = true
                                        }
                                    }

                                    if (pastTouchSlop) {
                                        val eventCentroid =
                                            event.calculateCentroid(useCurrent = false)
                                        if (
                                            zoomChange != 1f ||
                                            panChange != Offset.Zero
                                        ) {
                                            onTransformGesture(
                                                eventCentroid,
                                                panChange,
                                                zoomChange
                                            )
                                        }
                                        event.changes.fastForEach {
                                            if (it.positionChanged()) {
                                                it.consumeAllChanges()
                                            }
                                        }
                                    }
                                }
                            } else if (transformEventCounter > 3) relevant = false
                            transformEventCounter++
                        } while (!canceled && event.changes.fastAny { it.pressed } && relevant)

                        if (zoomableState.dragGesturesEnabled()) {
                            do {
                                awaitPointerEvent()
                                drag = awaitTouchSlopOrCancellation(down.id) { change, over ->
                                    change.consumePositionChange()
                                    overSlop = over
                                }
                            } while (drag != null && !drag.positionChangeConsumed())
                            if (drag != null) {
                                dragOffset = Offset.Zero
                                if (zoomableState.scale.value !in 0.92f..1.08f) {
                                    coroutineScope.launch {
                                        zoomableState.transform {
                                            transformBy(1f, overSlop, 0f)
                                        }
                                    }
                                } else {
                                    dragOffset += overSlop
                                }
                                if (drag(drag.id) {
                                        if (zoomableState.scale.value !in 0.92f..1.08f) {
                                            zoomableState.offset.value += it.positionChange()
                                        } else {
                                            dragOffset += it.positionChange()
                                        }
                                        it.consumePositionChange()
                                    }
                                ) {
                                    if (zoomableState.scale.value in 0.92f..1.08f) {
                                        val offsetX = dragOffset.x
                                        if (offsetX > minimumSwipeDistance) {
                                            onSwipeRight()

                                        } else if (offsetX < -minimumSwipeDistance) {
                                            onSwipeLeft()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
    ) {
        Box(
            modifier = Modifier
                .clip(RectangleShape)
                .offset {
                    IntOffset(
                        zoomableState.offset.value.x.roundToInt(),
                        zoomableState.offset.value.y.roundToInt()
                    )
                }
                .graphicsLayer(
                    scaleX = zoomableState.scale.value,
                    scaleY = zoomableState.scale.value,
                )
                .onGloballyPositioned { coordinates ->
                    val localOffset =
                        Offset(
                            coordinates.size.width.toFloat() / 2,
                            coordinates.size.height.toFloat() / 2
                        )
                    val windowOffset = coordinates.localToWindow(localOffset)
                    composableCenter =
                        coordinates.parentLayoutCoordinates?.windowToLocal(windowOffset)
                            ?: Offset.Zero
                },
        ) {
            Content()
        }
    }
}


/**
 * An implementation of [TransformableState] containing the values for the current [scale] and [offset]. It's normally obtained using [rememberTransformableState]
 * Other than [TransformableState] obtained by [rememberTransformableState], [ZoomableState3] exposes [scale] and [offset]
 *
 * @param scale [MutableState]<[Float]> of the scale this state is initialized with
 * @param offset [MutableState]<[Offset]> of the offset this state is initialized with
 *
 * @param onTransformation callback invoked when transformation occurs. The callback receives the
 * change from the previous event. It's relative scale multiplier for zoom and [Offset] in pixels
 * for pan
 *
 * @property scale The current scale as [MutableState]<[Float]>
 * @property offset The current offset as [MutableState]<[Offset]>
 * @property notTransformed `true` if [scale] is `1` and [offset] is [Offset.Zero]
 */
public class ZoomableState3(
    public var scale: MutableState<Float>,
    public var offset: MutableState<Offset>,
    onTransformation: ZoomableState3.(zoomChange: Float, panChange: Offset, rotationChange: Float) -> Unit
) : TransformableState {

    public val notTransformed: Boolean
        get() {
            return scale.value in (1 - 1.0E-3f)..(1 + 1.0E-3f) && offset.value.getDistanceSquared()  in -1.0E-6f..1.0E-6f
        }

    private val transformScope: TransformScope = object : TransformScope {
        override fun transformBy(zoomChange: Float, panChange: Offset, rotationChange: Float) =
            onTransformation(zoomChange, panChange, rotationChange)
    }

    private val transformMutex = MutatorMutex()

    private val isTransformingState = mutableStateOf(false)


    override suspend fun transform(
        transformPriority: MutatePriority,
        block: suspend TransformScope.() -> Unit
    ): Unit = coroutineScope {
        transformMutex.mutateWith(transformScope, transformPriority) {
            isTransformingState.value = true
            try {
                block()
            } finally {
                isTransformingState.value = false
            }
        }
    }

    override val isTransformInProgress: Boolean
        get() = isTransformingState.value

    public suspend fun animateBy(
        zoomChange: Float, panChange: Offset,
        animationSpec: AnimationSpec<Float> = SpringSpec(stiffness = Spring.StiffnessLow)
    ) {
        val baseScale = scale.value
        var previous = 0f
        transform {
            AnimationState(initialValue = previous).animateTo(1f, animationSpec) {
                val delta = this.value - previous
                previous = this.value
                transformBy(
                    zoomChange = (baseScale * (1 + (zoomChange - 1) * this.value)) / scale.value,
                    panChange = panChange * delta,
                )
            }
        }
    }

    public suspend fun animateZoomToPosition(
        zoomChange: Float,
        position: Offset,
        currentComposableCenter: Offset = Offset.Zero
    ) {
        val offsetBuffer = offset.value

        val x0 = position.x - currentComposableCenter.x
        val y0 = position.y - currentComposableCenter.y

        val hyp0 = sqrt(x0 * x0 + y0 * y0)
        val hyp1 = zoomChange * hyp0 * (if (x0 > 0) {
            1f
        } else {
            -1f
        })

        val alpha0 = atan(y0 / x0)

        val x1 = cos(alpha0) * hyp1
        val y1 = sin(alpha0) * hyp1

        val transformOffset =
            position - (currentComposableCenter - offsetBuffer) - Offset(x1, y1)

        animateBy(zoomChange = zoomChange, panChange = transformOffset)
    }
}

/**
 * @param initialZoom The initial zoom level of this State.
 * @param initialOffset The initial zoom level of this State
 *
 * @param onTransformation callback invoked when transformation occurs. The callback receives the
 * change from the previous event. It's relative scale multiplier for zoom and [Offset] in pixels
 * for pan. If not provided the default behaviour is
 * zooming, panning and rotating by the supplied changes.
 *
 * @return A [ZoomableState3] initialized with the given [initialZoom] and [initialOffset]
 */
@Composable
public fun rememberZoomableState3(
    initialZoom: Float = 1f,
    initialOffset: Offset = Offset.Zero,
    onTransformation: ZoomableState3.(zoomChange: Float, panChange: Offset, rotationChange: Float) -> Unit = { zoomChange, panChange, _ ->
        scale.value *= zoomChange
        offset.value += panChange
    }
): ZoomableState3 {
    val zoomR = remember { mutableStateOf(initialZoom) }
    val panR = remember { mutableStateOf(initialOffset) }
    val lambdaState = rememberUpdatedState(newValue = onTransformation)
    return remember {
        ZoomableState3(
            zoomR,
            panR,
            lambdaState.value::invoke
        )
    }
}
