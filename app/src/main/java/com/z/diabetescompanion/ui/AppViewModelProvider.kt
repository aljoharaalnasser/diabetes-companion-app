package com.z.diabetescompanion.ui

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.z.diabetescompanion.ui.auth.ForgotPasswordViewModel
import com.z.diabetescompanion.ui.auth.LoginViewModel
import com.z.diabetescompanion.ui.auth.SignUpViewModel
import com.z.diabetescompanion.ui.navigation.RootViewModel

/**
 * Factory for ViewModels that need no per-screen arguments. Screens with arguments
 * (a patient uid, a reminder id) use `viewModel { ... }` with [container] instead.
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val c = container()
            RootViewModel(
                authRepository = c.authRepository,
                userRepository = c.userRepository,
                preferencesRepository = c.preferencesRepository,
                accountService = c.accountService,
                reminderRepository = c.reminderRepository,
                reminderScheduler = c.reminderScheduler,
            )
        }
        initializer { LoginViewModel(container().authRepository, container().accountService) }
        initializer { SignUpViewModel(container().authRepository, container().accountService) }
        initializer { ForgotPasswordViewModel(container().authRepository) }
    }
}
