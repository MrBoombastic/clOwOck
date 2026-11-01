package com.mrboombastic.buwudzik.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mrboombastic.buwudzik.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Duration picker with second precision.
 * A minutes slider handles coarse changes, and a seconds slider fine-tunes the value.
 * The -/+ buttons step by one unit and carry over between minutes and seconds.
 *
 * @param label Picker label (usually includes the formatted value)
 * @param totalSeconds Current value in seconds
 * @param onValueChange Called with the new value in seconds, clamped to the allowed range
 * @param onValueChangeFinished Called when a slider is released or after button taps settle
 * @param modifier Optional modifier
 * @param enabled Whether the picker is enabled
 * @param minTotalSeconds Lowest allowed value in seconds
 * @param maxMinutes Upper bound of the minutes slider
 * @param debounceMs Delay after the last button tap before [onValueChangeFinished] is called
 */
@Composable
fun DurationPicker(
    label: String,
    totalSeconds: Int,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minTotalSeconds: Int = 1,
    maxMinutes: Int = 99,
    debounceMs: Long = 500
) {
    val coroutineScope = rememberCoroutineScope()
    var finishJob by remember { mutableStateOf<Job?>(null) }
    val maxTotalSeconds = maxMinutes * 60 + 59
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    fun update(newTotal: Int) = onValueChange(newTotal.coerceIn(minTotalSeconds, maxTotalSeconds))

    fun step(delta: Int) {
        update(totalSeconds + delta)
        // Debounce taps so repeated presses result in a single save
        finishJob?.cancel()
        finishJob = coroutineScope.launch {
            delay(debounceMs.milliseconds)
            onValueChangeFinished()
        }
    }

    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(4.dp))
        DurationUnitRow(
            unitLabel = stringResource(R.string.duration_minutes),
            value = minutes,
            maxValue = maxMinutes,
            enabled = enabled,
            canDecrease = totalSeconds - 60 >= minTotalSeconds,
            canIncrease = totalSeconds + 60 <= maxTotalSeconds,
            onSlide = { update(it * 60 + seconds) },
            onSlideFinished = onValueChangeFinished,
            onStep = { step(it * 60) }
        )
        DurationUnitRow(
            unitLabel = stringResource(R.string.duration_seconds),
            value = seconds,
            maxValue = 59,
            enabled = enabled,
            canDecrease = totalSeconds > minTotalSeconds,
            canIncrease = totalSeconds < maxTotalSeconds,
            onSlide = { update(minutes * 60 + it) },
            onSlideFinished = onValueChangeFinished,
            onStep = { step(it) }
        )
    }
}

@Composable
private fun DurationUnitRow(
    unitLabel: String,
    value: Int,
    maxValue: Int,
    enabled: Boolean,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onSlide: (Int) -> Unit,
    onSlideFinished: () -> Unit,
    onStep: (Int) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = unitLabel,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(64.dp)
        )
        IconButton(onClick = { onStep(-1) }, enabled = enabled && canDecrease) {
            Icon(
                Icons.Default.Remove,
                contentDescription = stringResource(R.string.decrease_value, unitLabel)
            )
        }
        Slider(
            value = value.toFloat(),
            enabled = enabled,
            onValueChange = { onSlide(it.roundToInt()) },
            onValueChangeFinished = onSlideFinished,
            valueRange = 0f..maxValue.toFloat(),
            steps = maxValue - 1,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = { onStep(1) }, enabled = enabled && canIncrease) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.increase_value, unitLabel)
            )
        }
    }
}
