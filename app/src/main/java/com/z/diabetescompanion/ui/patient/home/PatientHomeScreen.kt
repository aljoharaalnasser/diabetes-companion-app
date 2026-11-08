package com.z.diabetescompanion.ui.patient.home

import com.z.diabetescompanion.ui.theme.Red80
import com.z.diabetescompanion.ui.theme.Red50
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.Bloodtype
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.z.diabetescompanion.data.model.GlucoseUnit
import com.z.diabetescompanion.domain.GlucoseStatus
import com.z.diabetescompanion.domain.Trend
import com.z.diabetescompanion.domain.formatGlucose
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.components.ErrorState
import com.z.diabetescompanion.ui.components.GlucoseChart
import com.z.diabetescompanion.ui.components.LinkButton
import com.z.diabetescompanion.ui.components.LoadingState
import com.z.diabetescompanion.ui.components.PrototypeNotice
import com.z.diabetescompanion.ui.components.ScreenScaffold
import com.z.diabetescompanion.ui.components.SectionHeader
import com.z.diabetescompanion.ui.components.StatusChip
import com.z.diabetescompanion.ui.components.TrendLabel
import com.z.diabetescompanion.ui.container
import com.z.diabetescompanion.ui.currentSession
import java.util.Calendar
import com.z.diabetescompanion.ui.theme.Blue30
import com.z.diabetescompanion.ui.theme.Blue40
import com.z.diabetescompanion.ui.theme.Blue50
import com.z.diabetescompanion.ui.theme.Blue80
import com.z.diabetescompanion.ui.theme.Blue90

/** Patient home (S1-08): latest reading with status and trend, chart, reminders, quick actions. */
@Composable
fun PatientHomeScreen(
    onAddGlucose: () -> Unit,
    onLogMedication: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenReminders: () -> Unit,
    onAskDoctor: () -> Unit,
    onOpenChatbot: () -> Unit,
) {
    val session = currentSession()
    val viewModel: PatientHomeViewModel = viewModel {
        val c = container()
        PatientHomeViewModel(session.uid, c.glucoseRepository, c.thresholdRepository, c.reminderRepository)
    }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    ScreenScaffold(title = "Home") { padding ->
        when {
            state.isLoading -> LoadingState(modifier = Modifier.padding(padding))
            state.error != null -> ErrorState(message = state.error!!, modifier = Modifier.padding(padding))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item { Greeting(name = session.profile.firstName) }
                item { LatestReadingCard(state = state, onClick = onOpenHistory, onAdd = onAddGlucose) }
                item { QuickActions(onAddGlucose, onLogMedication, onAskDoctor, onOpenChatbot) }
                item { RemindersCard(state = state, onOpenReminders = onOpenReminders) }
                item { PrototypeNotice() }
            }
        }
    }
}

@Composable
private fun Greeting(name: String) {
    val greeting = remember {
        when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Hello"
        }
    }
    Column {
        Text(Format.today(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = buildAnnotatedString {
                append("$greeting, ")
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.tertiary)) { append(name) }
            },
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}

@Composable
private fun LatestReadingCard(state: PatientHomeUiState, onClick: () -> Unit, onAdd: () -> Unit) {
    val latest = state.latest
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(Brush.linearGradient(listOf(Blue50, Blue30)))
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        if (latest == null) {
            Column {
                Text("No readings yet", style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Add your first glucose reading to start tracking your trend.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Blue90,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onAdd,
                    shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                ) { Text("Add a reading") }
            }
            return@Box
        }
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(28.dp).background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.WaterDrop, contentDescription = null, tint = Red50, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Latest reading", style = MaterialTheme.typography.labelLarge, color = Blue90, modifier = Modifier.weight(1f))
                StatusChip(status = state.status, onDark = true)
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    latest.valueMgdl.formatGlucose(GlucoseUnit.MgDl),
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White,
                )
                Spacer(Modifier.width(8.dp))
                Text("mg/dL", style = MaterialTheme.typography.titleMedium, color = Blue90, modifier = Modifier.padding(bottom = 10.dp))
                Spacer(Modifier.weight(1f))
                TrendLabel(trend = state.trend, tint = Blue80, modifier = Modifier.padding(bottom = 12.dp))
            }
            Text(
                "${Format.relative(latest.recordedAt)} · ${latest.source.label}",
                style = MaterialTheme.typography.bodyMedium,
                color = Blue90.copy(alpha = 0.9f),
            )
            if (state.status == GlucoseStatus.NoThresholds) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your doctor hasn't set a target range yet, so no status is shown.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Blue90.copy(alpha = 0.8f),
                )
            }
            if (state.chartReadings.size >= 2) {
                Spacer(Modifier.height(14.dp))
                GlucoseChart(
                    readings = state.chartReadings,
                    thresholds = null,
                    height = 72.dp,
                    lineColor = Red80,
                    accentColor = Red50,
                    showAxes = false,
                )
                Spacer(Modifier.height(4.dp))
                Text("Last 7 days · ${state.chartReadings.size} readings", style = MaterialTheme.typography.labelSmall, color = Blue90.copy(alpha = 0.7f))
            } else if (state.trend == Trend.Unavailable) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Trend unavailable: add at least 3 readings within 48 hours.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Blue90.copy(alpha = 0.75f),
                )
            }
        }
    }
}

private data class QuickAction(val label: String, val icon: ImageVector, val accent: Boolean, val onClick: () -> Unit)

@Composable
private fun QuickActions(
    onAddGlucose: () -> Unit,
    onLogMedication: () -> Unit,
    onAskDoctor: () -> Unit,
    onOpenChatbot: () -> Unit,
) {
    val actions = listOf(
        QuickAction("Add glucose", Icons.Rounded.Bloodtype, accent = true, onClick = onAddGlucose),
        QuickAction("Log medication", Icons.Rounded.Medication, accent = false, onClick = onLogMedication),
        QuickAction("Ask the doctor about your insulin dose", Icons.Rounded.MedicalServices, accent = false, onClick = onAskDoctor),
        QuickAction("Open chatbot", Icons.AutoMirrored.Rounded.Chat, accent = true, onClick = onOpenChatbot),
    )
    Column {
        SectionHeader("Quick actions")
        Spacer(Modifier.height(12.dp))
        actions.chunked(2).forEachIndexed { index, row ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { action -> QuickActionTile(action, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun QuickActionTile(action: QuickAction, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = action.onClick)
            .padding(14.dp)
            .height(52.dp),
    ) {
        val container = if (action.accent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
        val content = if (action.accent) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onPrimaryContainer
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(container, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(action.icon, contentDescription = null, tint = content)
        }
        Spacer(Modifier.width(10.dp))
        Text(
            action.label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun RemindersCard(state: PatientHomeUiState, onOpenReminders: () -> Unit) {
    Column {
        SectionHeader("Upcoming reminders", trailing = { LinkButton(text = "Manage", onClick = onOpenReminders) })
        Spacer(Modifier.height(8.dp))
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            if (state.upcomingReminders.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(16.dp)) {
                    Icon(Icons.Rounded.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "No reminders yet. Set one so you never miss a dose.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Column {
                    state.upcomingReminders.forEachIndexed { index, item ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Box(
                                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.tertiaryContainer, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Alarm, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.reminder.medicationName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${Format.dayLabel(item.nextAt)} at ${Format.time(item.nextAt)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        if (index < state.upcomingReminders.lastIndex) {
                            Box(Modifier.fillMaxWidth().padding(start = 64.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                }
            }
        }
    }
}

