package com.mrboombastic.buwudzik.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val EXIT_ANIMATION_DURATION_MS = 500L

@Composable
fun CustomSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    // Retain the last snackbar data even after currentSnackbarData becomes null
    var lastSnackbarData by remember { mutableStateOf<SnackbarData?>(null) }
    
    // Determine if we should show the snackbar
    val isVisible = hostState.currentSnackbarData != null
    
    // Update lastSnackbarData whenever a new snackbar appears
    LaunchedEffect(hostState.currentSnackbarData) {
        if (hostState.currentSnackbarData != null) {
            lastSnackbarData = hostState.currentSnackbarData
        } else if (lastSnackbarData != null) {
            // Wait for exit animation to complete before clearing retained data
            kotlinx.coroutines.delay(EXIT_ANIMATION_DURATION_MS)
            lastSnackbarData = null
        }
    }
    
    // Render AnimatedVisibility outside of SnackbarHost's lambda
    // This ensures the exit animation can complete before removal from composition
    lastSnackbarData?.let { data ->
        AnimatedVisibility(
            visible = isVisible,
            modifier = modifier,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeOut()
        ) {
            Snackbar(
                snackbarData = data,
                containerColor = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                actionColor = MaterialTheme.colorScheme.inversePrimary,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
