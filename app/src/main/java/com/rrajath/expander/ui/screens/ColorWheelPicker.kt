package com.rrajath.expander.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.*

/** Local draft only: dragging never writes preferences or restarts the overlay. */
@Composable
internal fun ColorWheelPicker(initialColor: Int, enabled: Boolean, onColorChange: (Int) -> Unit) {
    val initial = remember(initialColor) { FloatArray(3).also { android.graphics.Color.colorToHSV(initialColor, it) } }
    var hue by rememberSaveable(initialColor) { mutableFloatStateOf(initial[0]) }
    var saturation by rememberSaveable(initialColor) { mutableFloatStateOf(initial[1]) }
    var brightness by rememberSaveable(initialColor) { mutableFloatStateOf(initial[2]) }
    val notify by rememberUpdatedState(onColorChange)
    fun publish() = notify(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness)))
    val spectrum = remember { listOf(Color.Red, Color.Yellow, Color.Green, Color.Cyan, Color.Blue, Color.Magenta, Color.Red) }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        Text("Drag to choose a color", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.padding(top = 12.dp).size(208.dp)
            .semantics {
                contentDescription = "Color wheel. Hue ${hue.toInt()} degrees, saturation ${(saturation * 100).toInt()} percent"
                if (enabled) customActions = listOf(
                    CustomAccessibilityAction("Next hue") { hue = (hue + 15f) % 360f; publish(); true },
                    CustomAccessibilityAction("Previous hue") { hue = (hue + 345f) % 360f; publish(); true },
                    CustomAccessibilityAction("More saturated") { saturation = (saturation + .1f).coerceAtMost(1f); publish(); true },
                    CustomAccessibilityAction("Less saturated") { saturation = (saturation - .1f).coerceAtLeast(0f); publish(); true }
                )
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val radius = minOf(size.width, size.height) / 2f - 10.dp.toPx()
                    if ((down.position - center).getDistance() <= radius) {
                        fun choose(position: Offset) {
                            val delta = position - center
                            hue = ((atan2(delta.y, delta.x) * 180f / PI.toFloat()) + 360f) % 360f
                            saturation = (delta.getDistance() / radius).coerceIn(0f, 1f)
                            publish()
                        }
                        down.consume()
                        choose(down.position)
                        do {
                            val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: break
                            if (change.isConsumed) break
                            choose(change.position)
                            change.consume()
                        } while (change.pressed)
                    }
                }
            }) {
            val radius = size.minDimension / 2f - 10.dp.toPx()
            drawCircle(Brush.sweepGradient(spectrum, center), radius)
            drawCircle(Brush.radialGradient(listOf(Color.White, Color.Transparent), center, radius), radius)
            drawCircle(Color.Black.copy(alpha = 1f - brightness), radius)
            val angle = hue * PI.toFloat() / 180f
            val marker = center + Offset(cos(angle), sin(angle)) * (radius * saturation)
            drawCircle(Color.Black.copy(alpha = .55f), 9.dp.toPx(), marker, style = Stroke(4.dp.toPx()))
            drawCircle(Color.White, 9.dp.toPx(), marker, style = Stroke(2.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Brightness", style = MaterialTheme.typography.labelMedium)
            Slider(value = brightness, onValueChange = { brightness = it; publish() }, enabled = enabled,
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp).semantics { contentDescription = "Color brightness" })
            Text("${(brightness * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium)
        }
    }
}
