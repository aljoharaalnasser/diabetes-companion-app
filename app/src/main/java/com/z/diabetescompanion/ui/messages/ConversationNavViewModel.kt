package com.z.diabetescompanion.ui.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.z.diabetescompanion.data.model.UserProfile
import com.z.diabetescompanion.data.model.UserRole
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Opening a conversation needs a lookup (who is the doctor?) and possibly a write, so the
 * shells ask this, then navigate once an id comes back. Keeps that async step out of the
 * navigation callbacks themselves.
 */
class ConversationNavViewModel(
    private val launcher: ConversationLauncher,
) : ViewModel() {

    private val _destination = MutableStateFlow<String?>(null)
    val destination = _destination.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    fun consumeDestination() { _destination.value = null }
    fun clearError() { _error.value = null }

    fun openWithDoctor(viewer: UserProfile) = launch {
        when (viewer.role) {
            UserRole.Patient -> launcher.openForPatient(viewer)
            else -> ConversationResult.Unavailable("Only a patient can start their own conversation here.")
        }
    }

    fun openAsCaregiver(caregiver: UserProfile, patientUid: String, patientName: String) = launch {
        launcher.openForCaregiver(caregiver, patientUid, patientName)
    }

    fun openAsDoctor(doctor: UserProfile, patientUid: String, patientName: String) = launch {
        launcher.openForDoctor(doctor, patientUid, patientName)
    }

    private fun launch(block: suspend () -> ConversationResult) {
        viewModelScope.launch {
            when (val result = block()) {
                is ConversationResult.Opened -> _destination.value = result.conversationId
                is ConversationResult.Unavailable -> _error.value = result.message
            }
        }
    }
}
