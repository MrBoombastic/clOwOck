package com.mrboombastic.buwudzik.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Reusable small button component for secondary actions.
 * Similar to MenuTile but smaller and using TextButton style.
 */
@Composable
fun SmallButton(
    modifier: Modifier = Modifier,
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    contentColor: Color = MaterialTheme.colorScheme.primary
) {
    TextButton(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth(0.9f)
            .height(56.dp),
        colors = ButtonDefaults.textButtonColors(
            contentColor = contentColor
        )
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontWeight = FontWeight.Medium
        )
    }
}

