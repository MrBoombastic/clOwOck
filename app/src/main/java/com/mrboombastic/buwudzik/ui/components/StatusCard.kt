package com.mrboombastic.buwudzik.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class StatusType {
    ERROR,
    WARNING,
    INFO
}

/**
 * Reusable status card for displaying messages to the user
 * @param message The message to display
 * @param type The type of status (ERROR, WARNING, INFO)
 * @param modifier Optional modifier
 */
@Composable
fun StatusCard(
    message: String,
    type: StatusType = StatusType.ERROR,
    modifier: Modifier = Modifier
) {
    val color = when (type) {
        StatusType.ERROR -> MaterialTheme.colorScheme.error
        StatusType.WARNING -> MaterialTheme.colorScheme.tertiary
        StatusType.INFO -> MaterialTheme.colorScheme.primary
    }

    Text(
        text = message,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    )
}
