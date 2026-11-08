package com.z.diabetescompanion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.di.AppContainer
import com.z.diabetescompanion.ui.navigation.AppNavHost
import com.z.diabetescompanion.ui.navigation.RootViewModel
import com.z.diabetescompanion.ui.theme.DiabetesCompanionTheme

/**
 * Top of the Compose tree: theme, text-size preference (FR-22) and the root navigation.
 * Text size multiplies the system font scale so it composes with the device setting.
 */
@Composable
fun DiabetesCompanionApp(
    container: AppContainer,
    rootViewModel: RootViewModel = viewModel(factory = AppViewModelProvider.Factory),
) {
    val prefs by rootViewModel.preferences.collectAsStateWithLifecycle()
    val baseDensity = LocalDensity.current
    val scaledDensity = Density(
        density = baseDensity.density,
        fontScale = baseDensity.fontScale * prefs.textSize.scale,
    )

    DiabetesCompanionTheme {
        CompositionLocalProvider(
            LocalDensity provides scaledDensity,
            LocalAppContainer provides container,
        ) {
            AppNavHost(rootViewModel = rootViewModel)
        }
    }
}
