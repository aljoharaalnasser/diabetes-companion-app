package com.z.diabetescompanion.ui.patient.glucose

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.model.GlucoseReading
import com.z.diabetescompanion.data.model.GlucoseUnit
import com.z.diabetescompanion.domain.formatGlucose
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.AppTextField
import com.z.diabetescompanion.ui.components.ChoiceChips
import com.z.diabetescompanion.ui.components.DateTimeField
import com.z.diabetescompanion.ui.components.InlineMessage
import com.z.diabetescompanion.ui.components.MessageTone
import com.z.diabetescompanion.ui.components.PrimaryButton
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SecondaryButton
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import com.z.diabetescompanion.ui.theme.AppTheme

@Composable
fun AddGlucoseScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val session = currentSession()
    val viewModel: AddGlucoseViewModel = viewModel { AddGlucoseViewModel(session.uid, container().glucoseRepository) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Add glucose reading", onBack = onBack) { padding ->
        AnimatedContent(
            targetState = state.saved,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "addGlucose",
            modifier = Modifier.fillMaxSize().padding(padding),
        ) { saved ->
            if (saved != null) {
                SavedConfirmation(
                    reading = saved,
                    onAddAnother = viewModel::reset,
                    onViewHistory = onOpenHistory,
                    onDone = onBack,
                )
            } else {
                EntryForm(state = state, viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun EntryForm(state: AddGlucoseUiState, viewModel: AddGlucoseViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text("Unit", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        ChoiceChips(
            options = GlucoseUnit.entries,
            selected = state.unit,
            onSelected = viewModel::onUnitChange,
            label = { it.label },
            enabled = !state.isSaving,
        )
        Spacer(Modifier.height(16.dp))
        AppTextField(
            value = state.valueText,
            onValueChange = viewModel::onValueChange,
            label = "Glucose value (${state.unit.label})",
            leadingIcon = Icons.Rounded.Bloodtype,
            errorText = state.valueError,
            enabled = !state.isSaving,
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        )
        Spacer(Modifier.height(16.dp))
        Text("Date and time", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        DateTimeField(
            value = state.recordedAt,
            onValueChange = viewModel::onTimeChange,
            enabled = !state.isSaving,
            errorText = state.timeError,
        )
        Spacer(Modifier.height(16.dp))
        AppTextField(
            value = state.note,
            onValueChange = viewModel::onNoteChange,
            label = "Note (optional)",
            placeholder = "e.g. before breakfast",
            enabled = !state.isSaving,
            imeAction = ImeAction.Done,
        )
        Spacer(Modifier.height(20.dp))
        InlineMessage(message = state.formError, tone = MessageTone.Error, modifier = Modifier.padding(bottom = 12.dp))
        PrimaryButton(text = "Save reading", onClick = viewModel::save, loading = state.isSaving, enabled = state.canSave)

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))
        Text("For demonstrations", style = MaterialTheme.typography.titleSmall)
        Text(
            "Creates a plausible reading labelled \"Demo reading\" so you can try the monitoring workflow without real data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        OutlinedButton(
            onClick = viewModel::generateDemo,
            enabled = !state.isSaving,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().height(54.dp),
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
            Text("Generate demo reading", modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SavedConfirmation(
    reading: GlucoseReading,
    onAddAnother: () -> Unit,
    onViewHistory: () -> Unit,
    onDone: () -> Unit,
) {
    val clinical = AppTheme.clinical
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(96.dp).background(clinical.inRangeContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = clinical.inRange, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("Saved successfully", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            "${reading.enteredValue.formatGlucose(reading.enteredUnit)} ${reading.enteredUnit.label}",
            style = MaterialTheme.typography.displayMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "${Format.dateTime(reading.recordedAt)} · ${reading.source.label}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryButton(text = "Done", onClick = onDone)
        Spacer(Modifier.height(12.dp))
        SecondaryButton(text = "View history", onClick = onViewHistory)
        Spacer(Modifier.height(12.dp))
        SecondaryButton(text = "Add another reading", onClick = onAddAnother)
    }
}
