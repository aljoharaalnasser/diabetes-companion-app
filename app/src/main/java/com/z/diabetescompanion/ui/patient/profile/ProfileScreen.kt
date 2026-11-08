package com.z.diabetescompanion.ui.patient.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cake
import androidx.compose.material.icons.rounded.PersonOutline
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.firestore.toFirestoreMessage
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import com.z.diabetescompanion.data.repository.UserRepository
import com.z.diabetescompanion.ui.auth.AuthValidation
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.ChoiceChips
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val name: String,
    val ageText: String,
    val diabetesType: String,
    val relevantHistory: String,
    val carePlan: String,
    val nameError: String? = null,
    val ageError: String? = null,
    val formError: String? = null,
    val isSaving: Boolean = false,
    val savedTick: Int = 0,
)

private val diabetesTypes = listOf("Type 1", "Type 2", "Gestational", "Other")

/** Patient profile (FR-02); doctors and caregivers only edit their name here. */
class ProfileViewModel(
    private val profile: UserProfile,
    private val userRepository: UserRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        ProfileUiState(
            name = profile.displayName,
            ageText = profile.age?.toString().orEmpty(),
            diabetesType = profile.diabetesType.ifBlank { diabetesTypes.first() },
            relevantHistory = profile.relevantHistory,
            carePlan = profile.carePlan,
        )
    )
    val uiState = _uiState.asStateFlow()

    fun onNameChange(v: String) = _uiState.update { it.copy(name = v, nameError = null, formError = null) }
    fun onAgeChange(v: String) = _uiState.update { it.copy(ageText = v.filter { c -> c.isDigit() }.take(3), ageError = null) }
    fun onTypeChange(v: String) = _uiState.update { it.copy(diabetesType = v) }
    fun onHistoryChange(v: String) = _uiState.update { it.copy(relevantHistory = v.take(1000)) }
    fun onCarePlanChange(v: String) = _uiState.update { it.copy(carePlan = v.take(1000)) }

    fun save() {
        val s = _uiState.value
        if (s.isSaving) return
        val nameError = AuthValidation.nameError(s.name)
        val age = s.ageText.toIntOrNull()
        val ageError = when {
            s.ageText.isBlank() -> null
            age == null || age < 1 || age > 120 -> "Enter an age between 1 and 120."
            else -> null
        }
        if (nameError != null || ageError != null) {
            _uiState.update { it.copy(nameError = nameError, ageError = ageError) }
            return
        }
        _uiState.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            try {
                userRepository.updateProfile(
                    profile.copy(
                        displayName = s.name.trim(),
                        age = age,
                        diabetesType = s.diabetesType,
                        relevantHistory = s.relevantHistory.trim(),
                        carePlan = s.carePlan.trim(),
                    )
                )
                _uiState.update { it.copy(isSaving = false, savedTick = it.savedTick + 1) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, formError = e.toFirestoreMessage()) }
            }
        }
    }
}

@Composable
fun ProfileScreen(onBack: () -> Unit) {
    val session = currentSession()
    val viewModel: ProfileViewModel = viewModel { ProfileViewModel(session.profile, container().userRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val isPatient = session.profile.role == UserRole.Patient

    LaunchedEffect(state.savedTick) {
        if (state.savedTick > 0) snackbar.showSnackbar("Profile saved")
    }

    ScreenScaffold(title = "Profile", onBack = onBack, snackbarHostState = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                "${session.profile.role.label} · ${session.profile.email}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
            AppTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = "Full name",
                leadingIcon = Icons.Rounded.PersonOutline,
                errorText = state.nameError,
                enabled = !state.isSaving,
            )
            if (isPatient) {
                Spacer(Modifier.height(14.dp))
                AppTextField(
                    value = state.ageText,
                    onValueChange = viewModel::onAgeChange,
                    label = "Age",
                    leadingIcon = Icons.Rounded.Cake,
                    errorText = state.ageError,
                    enabled = !state.isSaving,
                    keyboardType = KeyboardType.Number,
                )
                Spacer(Modifier.height(16.dp))
                Text("Diabetes type", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                ChoiceChips(options = diabetesTypes, selected = state.diabetesType, onSelected = viewModel::onTypeChange, label = { it }, enabled = !state.isSaving)
                Spacer(Modifier.height(16.dp))
                AppTextField(
                    value = state.relevantHistory,
                    onValueChange = viewModel::onHistoryChange,
                    label = "Relevant history",
                    placeholder = "Diagnosis year, other conditions, allergies",
                    enabled = !state.isSaving,
                    imeAction = ImeAction.Next,
                )
                Spacer(Modifier.height(14.dp))
                AppTextField(
                    value = state.carePlan,
                    onValueChange = viewModel::onCarePlanChange,
                    label = "Existing care plan",
                    placeholder = "Current medication plan, diet or activity guidance",
                    enabled = !state.isSaving,
                    imeAction = ImeAction.Done,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "This context is shown to your assigned doctor and used for future insulin-advice requests. Keep it brief.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(20.dp))
            InlineMessage(message = state.formError, tone = MessageTone.Error, modifier = Modifier.padding(bottom = 12.dp))
            PrimaryButton(text = "Save profile", onClick = viewModel::save, loading = state.isSaving, enabled = state.name.isNotBlank())
            Spacer(Modifier.height(24.dp))
        }
    }
}
