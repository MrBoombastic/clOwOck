package com.mrboombastic.buwudzik.ui.utils

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * Consistent navigation animations across the app.
 * All screen transitions use horizontal slide with fade.
 */
object NavigationAnimations {
    private const val ANIMATION_DURATION = 300

    /**
     * Enter animation when navigating forward (slide in from right)
     */
    fun enterTransition(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = ANIMATION_DURATION)
            ) + fadeIn(animationSpec = tween(durationMillis = ANIMATION_DURATION))
        }

    /**
     * Exit animation when navigating forward (slide out to left)
     */
    fun exitTransition(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(
            targetOffsetX = { fullWidth -> -fullWidth / 3 },
            animationSpec = tween(durationMillis = ANIMATION_DURATION)
        ) + fadeOut(animationSpec = tween(durationMillis = ANIMATION_DURATION))
    }

    /**
     * Enter animation when navigating back (slide in from left)
     */
    fun popEnterTransition(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
        {
            slideInHorizontally(
                initialOffsetX = { fullWidth -> -fullWidth / 3 },
                animationSpec = tween(durationMillis = ANIMATION_DURATION)
            ) + fadeIn(animationSpec = tween(durationMillis = ANIMATION_DURATION))
        }

    /**
     * Exit animation when navigating back (slide out to right)
     */
    fun popExitTransition(): AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
        {
            slideOutHorizontally(
                targetOffsetX = { fullWidth -> fullWidth },
                animationSpec = tween(durationMillis = ANIMATION_DURATION)
            ) + fadeOut(animationSpec = tween(durationMillis = ANIMATION_DURATION))
        }
}

