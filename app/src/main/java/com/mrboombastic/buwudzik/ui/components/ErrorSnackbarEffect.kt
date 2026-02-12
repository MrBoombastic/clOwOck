package com.mrboombastic.buwudzik.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * Reusable effect to show error messages via Snackbar
 * @param errorMessage The error message to display, or null if no error
 * @param snackbarHostState The SnackbarHostState to use for showing the message
 * @param duration Duration for the snackbar (default: Long)
 * @param onErrorShown Callback invoked after error is shown
 */
@Composable
fun ErrorSnackbarEffect(
    errorMessage: String?,
    snackbarHostState: SnackbarHostState,
    duration: SnackbarDuration = SnackbarDuration.Long,
    onErrorShown: () -> Unit = {}
) {
    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            snackbarHostState.showSnackbar(
                message = it,
                duration = duration
            )
            onErrorShown()
        }
    }
}
