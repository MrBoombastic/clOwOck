package com.mrboombastic.buwudzik.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

/**
 * Standard top app bar with back navigation and optional progress indicator.
 *
 * @param title The title text to display in the top bar
 * @param navController Navigation controller for back navigation
 * @param showProgress Whether to show a loading indicator in the actions area
 * @param navigationEnabled Whether the back button is enabled
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandardTopBar(
    title: String,
    navController: NavController,
    showProgress: Boolean = false,
    navigationEnabled: Boolean = true
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            BackNavigationButton(navController, enabled = navigationEnabled)
        },
        actions = {
            if (showProgress) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .size(24.dp),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    )
}
