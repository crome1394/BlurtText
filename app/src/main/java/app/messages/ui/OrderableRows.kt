package app.messages.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
    val surface = MaterialTheme.colorScheme.surface
    val stripe = if (surface.luminance() >= 0.5f) {
        Color(surface.red * 0.90f, surface.green * 0.90f, surface.blue * 0.90f, surface.alpha)
    } else {
        Color(
            (surface.red + 0.07f).coerceAtMost(1f),
            (surface.green + 0.07f).coerceAtMost(1f),
            (surface.blue + 0.07f).coerceAtMost(1f),
            surface.alpha,
        )
    }
    val shift = if (dragFrom < 0 || rowPx <= 0f) 0 else (dragDy / rowPx).toInt()
    val target = if (dragFrom < 0) -1 else (dragFrom + shift).coerceIn(0, items.lastIndex)
    androidx.compose.foundation.layout.Column(
        modifier.verticalScroll(rememberScrollState()),
    ) {
        items.forEachIndexed { index, item ->
            val name = label(item)
            val dragging = index == dragFrom
            val visual = when {
                dragFrom < 0 || target == dragFrom -> 0f
                dragging -> dragDy
                dragFrom < target && index in (dragFrom + 1)..target -> -rowPx
                dragFrom > target && index in target until dragFrom -> rowPx
                else -> 0f
            }
            val bg = when {
                dragging -> MaterialTheme.colorScheme.surfaceVariant
                index % 2 == 1 -> stripe
                else -> Color.Transparent
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = visual }
                    .background(bg)
                    .pointerInput(item.id) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                dragFrom = index
                                dragDy = 0f
                            },
                            onDragEnd = {
                                val from = dragFrom
                                val slots = if (rowPx <= 0f) 0 else (dragDy / rowPx).toInt()
                                val to = if (from < 0) -1 else (from + slots).coerceIn(0, items.lastIndex)
                                dragFrom = -1
                                dragDy = 0f
                                if (from >= 0 && to >= 0 && to != from) {
                                    val next = items.toMutableList()
                                    val moved = next.removeAt(from)
                                    next.add(to, moved)
                                    onReorder(next)
                                }
                            },
                            onDragCancel = {
                                dragFrom = -1
                                dragDy = 0f
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragDy += amount.y
                            },
                        )
                    }
                    .padding(horizontal = 16.dp)
                    .semantics { contentDescription = "Press and hold to move $name" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
