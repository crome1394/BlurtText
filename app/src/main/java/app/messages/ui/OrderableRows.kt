package app.messages.ui

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.messages.data.RibbonItem

@Composable
fun OrderableRows(
    items: List<RibbonItem>,
    label: (RibbonItem) -> String,
    onReorder: (List<RibbonItem>) -> Unit,
    onShown: (RibbonItem, Boolean) -> Unit,
    icon: @Composable (RibbonItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rowHeight = 64.dp
    val rowPx = with(LocalDensity.current) { rowHeight.toPx() }
    var dragFrom by remember { mutableIntStateOf(-1) }
    var dragDy by remember { mutableFloatStateOf(0f) }
    val itemsNow = rememberUpdatedState(items)
    val onReorderNow = rememberUpdatedState(onReorder)
    val shift = reorderSlots(dragDy, rowPx, dragFrom >= 0)
    val target = if (dragFrom < 0) -1 else (dragFrom + shift).coerceIn(0, items.lastIndex)
    val view = LocalView.current
    Column(
        // Scrolling and the handle fight for the same vertical drag. While a handle is
        // held, the list stays still so the row follows the finger.
        modifier.verticalScroll(rememberScrollState(), enabled = dragFrom < 0),
    ) {
        for (index in items.indices) {
            val item = items[index]
            androidx.compose.runtime.key(item.id) {
                val name = label(item)
                val dragging = index == dragFrom
                val visual = when {
                    dragFrom < 0 || target == dragFrom -> 0f
                    dragging -> dragDy
                    dragFrom < target && index in (dragFrom + 1)..target -> -rowPx
                    dragFrom > target && index in target until dragFrom -> rowPx
                    else -> 0f
                }
                val bg = if (dragging) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(rowHeight)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = visual }
                        .background(bg)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ReorderHandle(
                        name = name,
                        onStart = {
                            dragFrom = index
                            dragDy = 0f
                            view.performHapticFeedback(reorderGrabHaptic())
                        },
                        onMove = { dragDy += it },
                        onEnd = {
                            val from = dragFrom
                            val slots = reorderSlots(dragDy, rowPx, from >= 0)
                            val current = itemsNow.value
                            val to = if (from < 0) -1 else (from + slots).coerceIn(0, current.lastIndex)
                            dragFrom = -1
                            dragDy = 0f
                            if (from >= 0 && to >= 0 && to != from && from <= current.lastIndex) {
                                val next = current.toMutableList()
                                val moved = next.removeAt(from)
                                next.add(to, moved)
                                onReorderNow.value(next)
                            }
                        },
                    )
                    icon(item)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        name,
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium,
                        color = if (item.shown) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    AppSwitch(
                        checked = item.shown,
                        onCheckedChange = { onShown(item, it) },
                        modifier = Modifier.semantics { contentDescription = "Show $name" },
                    )
                }
            }
        }
    }
}

/** How many rows the finger has crossed. Half a row is enough, in either direction. */
internal fun reorderSlots(dragDy: Float, rowPx: Float, dragging: Boolean): Int {
    if (!dragging || rowPx <= 0f) return 0
    val rows = dragDy / rowPx
    val whole = rows.toInt()
    val fraction = rows - whole
    return when {
        fraction >= 0.5f -> whole + 1
        fraction <= -0.5f -> whole - 1
        else -> whole
    }
}

@Composable
private fun ReorderHandle(
    name: String,
    onStart: () -> Unit,
    onMove: (Float) -> Unit,
    onEnd: () -> Unit,
) {
    val start = rememberUpdatedState(onStart)
    val move = rememberUpdatedState(onMove)
    val end = rememberUpdatedState(onEnd)
    Box(
        Modifier
            .width(44.dp)
            .fillMaxHeight()
            .semantics { contentDescription = "Drag to move $name" }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Consume the down immediately. A long-press drag loses to the
                    // scrolling list, so the row sometimes never leaves its place.
                    down.consume()
                    start.value()
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                change.consume()
                                break
                            }
                            val dy = change.positionChange().y
                            change.consume()
                            if (dy != 0f) move.value(dy)
                        }
                    } finally {
                        end.value()
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.DragIndicator,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun reorderGrabHaptic(): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.GESTURE_START
    else HapticFeedbackConstants.CLOCK_TICK
