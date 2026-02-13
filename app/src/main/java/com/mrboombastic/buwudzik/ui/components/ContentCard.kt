package com.mrboombastic.buwudzik.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Reusable content card component for general content display.
 * Uses standard Card with consistent styling.
 */
@Composable
fun ContentCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = containerColor
        )
    ) {
        content()
    }
}

