package com.mrboombastic.buwudzik.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mrboombastic.buwudzik.utils.TimeFormatUtils

/**
 * Reusable time picker card component
 * @param label Label for the time picker
 * @param hour Hour value (0-23)
 * @param minute Minute value (0-59)
 * @param enabled Whether the card is enabled
 * @param onClick Callback when card is clicked
 * @param modifier Optional modifier
 */
@Composable
fun TimePickerCard(
    label: String,
    hour: Int,
    minute: Int,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedCard(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = TimeFormatUtils.formatHourMinute(hour, minute),
                style = MaterialTheme.typography.titleLarge
            )
        }
    }
}
