package com.daytoday.ui.screen.steps

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.daytoday.usecase.StepsBucket

@Composable
fun StepsBarChart(
    buckets: List<StepsBucket>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return
    val primary = MaterialTheme.colorScheme.primary
    val current = MaterialTheme.colorScheme.tertiary
    val track = MaterialTheme.colorScheme.surfaceVariant
    val selectedWash = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val goalColor = MaterialTheme.colorScheme.onSurfaceVariant
    val goalDash = remember { PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f) }
    val safeIndex = selectedIndex.coerceIn(0, buckets.lastIndex)
    val description = "Steps chart, ${buckets.size} bars; selected ${buckets[safeIndex].label}"
    val hasGoal = buckets.any { it.goal > 0L }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .semantics { contentDescription = description }
            .pointerInput(buckets) {
                detectTapGestures { offset ->
                    val width = this.size.width.toFloat()
                    val slot = width / buckets.size
                    if (slot > 0f) {
                        onSelect((offset.x / slot).toInt().coerceIn(0, buckets.lastIndex))
                    }
                }
            }
    ) {
        val slot = size.width / buckets.size
        val barWidth = (slot * 0.56f).coerceAtLeast(2f)
        val baseline = size.height - 2.dp.toPx()
        val topPad = 12.dp.toPx()
        val chartHeight = baseline - topPad

        // Over-goal bars must remain visible: scale to at least 1.0 goal, or the
        // largest bucket when the user exceeded the goal.
        val maxFraction = buckets.maxOf { bucket ->
            if (bucket.goal > 0L) bucket.steps.toFloat() / bucket.goal else 0f
        }.coerceAtLeast(1f)

        buckets.forEachIndexed { index, bucket ->
            val left = index * slot
            if (index == safeIndex) {
                drawRoundRect(
                    color = selectedWash,
                    topLeft = Offset(left + 1.dp.toPx(), 0f),
                    size = Size(slot - 2.dp.toPx(), size.height),
                    cornerRadius = CornerRadius(6.dp.toPx()),
                )
            }
            val fraction = if (bucket.goal > 0L) bucket.steps.toFloat() / bucket.goal else 0f
            val barHeight = (chartHeight * (fraction / maxFraction)).coerceIn(0f, chartHeight)
            if (barHeight > 0f) {
                drawRoundRect(
                    color = if (bucket.isCurrent) current else primary,
                    topLeft = Offset(left + (slot - barWidth) / 2f, baseline - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f),
                )
            } else {
                // hairline "empty" bar so a known zero is distinguishable from a gap
                drawRoundRect(
                    color = track,
                    topLeft = Offset(left + (slot - barWidth) / 2f, baseline - 1.dp.toPx()),
                    size = Size(barWidth, 1.dp.toPx()),
                )
            }
        }

        // dashed goal line at 1.0 normalized
        if (hasGoal) {
            val goalY = baseline - chartHeight * (1f / maxFraction)
            drawLine(
                color = goalColor,
                start = Offset(0f, goalY),
                end = Offset(size.width, goalY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = goalDash,
            )
        }
    }
}

@Composable
fun StepsChartLabels(
    buckets: List<StepsBucket>,
    selectedIndex: Int,
    modifier: Modifier = Modifier,
) {
    if (buckets.isEmpty()) return
    val indices: List<Int> = when {
        buckets.size <= 7 -> buckets.indices.toList()
        buckets.size in 28..31 -> listOf(0, 6, 13, 20, buckets.lastIndex)
            .filter { it in buckets.indices }
            .distinct()
            .sorted()
        buckets.size == 12 -> {
            val everyOther = buckets.indices.filter { it % 2 == 0 }
            val currentIndex = buckets.indexOfFirst { it.isCurrent }
            (everyOther + listOfNotNull(currentIndex.takeIf { it >= 0 })).distinct().sorted()
        }
        else -> buckets.indices.filter { it % 2 == 0 }
    }
    val selected = selectedIndex.coerceIn(0, buckets.lastIndex)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        indices.forEach { index ->
            val bucket = buckets[index]
            val highlighted = index == selected || bucket.isCurrent
            Text(
                text = bucket.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Normal,
                color = if (highlighted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
