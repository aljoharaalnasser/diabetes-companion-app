package com.z.diabetescompanion.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.MedicalServices
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.z.diabetescompanion.data.model.Alert
import com.z.diabetescompanion.data.model.AlertEvent
import com.z.diabetescompanion.data.model.AlertSeverity
import com.z.diabetescompanion.data.model.AlertStage
import com.z.diabetescompanion.domain.AlertRules
import com.z.diabetescompanion.ui.common.Format
import com.z.diabetescompanion.ui.theme.AppTheme
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

@Composable
fun AlertSeverity.color(): Color = when (this) {
    AlertSeverity.Warning -> AppTheme.clinical.high
    AlertSeverity.Critical -> AppTheme.clinical.veryHigh
}

@Composable
fun AlertSeverity.containerColor(): Color = when (this) {
    AlertSeverity.Warning -> AppTheme.clinical.highContainer
    AlertSeverity.Critical -> AppTheme.clinical.veryHighContainer
}

/** Severity always pairs an icon with its word, never colour alone (NFR-02). */
@Composable
fun AlertSeverityChip(severity: AlertSeverity, resolved: Boolean, modifier: Modifier = Modifier) {
    val container = if (resolved) AppTheme.clinical.inRangeContainer else severity.containerColor()
    val content = if (resolved) AppTheme.clinical.inRange else severity.color()
    val icon = if (resolved) Icons.Rounded.CheckCircle else Icons.Rounded.Warning
    val label = if (resolved) "Resolved" else severity.label

    Surface(shape = CircleShape, color = container, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
}

@Composable
fun AlertStage.icon(): ImageVector = when (this) {
    AlertStage.Created -> Icons.Rounded.NotificationsActive
    AlertStage.CaregiverNotified -> Icons.Rounded.Groups
    AlertStage.DoctorNotified -> Icons.Rounded.MedicalServices
    AlertStage.Resolved -> Icons.Rounded.CheckCircle
}

/**
 * Live countdown to the next escalation step. Ticks once a second so the demo audience can
 * watch the five minutes run down; shows nothing once the alert is answered or resolved.
 */
@Composable
fun EscalationCountdown(alert: Alert, modifier: Modifier = Modifier) {
    var secondsLeft by remember(alert.id, alert.stage, alert.patientResponse) {
        mutableLongStateOf(AlertRules.secondsUntilEscalation(alert) ?: -1L)
    }
    LaunchedEffect(alert.id, alert.stage, alert.patientResponse) {
        while (true) {
            val remaining = AlertRules.secondsUntilEscalation(alert, Date()) ?: -1L
            secondsLeft = remaining
            if (remaining <= 0L) break
            delay(1000)
        }
    }
    if (secondsLeft < 0L) return

    val target = if (alert.stage == AlertStage.Created) "Your caregiver" else "Your doctor"
    val progress by animateFloatAsState(
        targetValue = (secondsLeft.toFloat() / (AlertRules.ESCALATION_STEP_MS / 1000f)).coerceIn(0f, 1f),
        label = "escalationProgress",
    )

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (secondsLeft > 0)
                    "$target will be notified in ${formatCountdown(secondsLeft)} if you do not respond."
                else "$target is being notified now.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { progress },
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
        )
    }
}

private fun formatCountdown(seconds: Long): String =
    String.format(Locale.US, "%02d:%02d", seconds / 60, seconds % 60)

/** Created -> Acknowledged -> Caregiver notified -> Doctor notified -> Resolved (FR-10). */
@Composable
fun AlertTimeline(events: List<AlertEvent>, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        events.forEachIndexed { index, event ->
            Row(modifier = Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(24.dp)) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                if (index == events.lastIndex) MaterialTheme.colorScheme.tertiary
                                else MaterialTheme.colorScheme.primary,
                                CircleShape,
                            )
                    )
                    if (index < events.lastIndex) {
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(34.dp)
                                .background(MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.padding(bottom = if (index < events.lastIndex) 8.dp else 0.dp)) {
                    Text(event.label, style = MaterialTheme.typography.titleSmall)
                    Text(
                        buildString {
                            append(Format.dateTime(event.at))
                            if (event.actorName.isNotBlank()) append(" · ").append(event.actorName)
                            if (event.note.isNotBlank()) append(" · ").append(event.note)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Row used in the patient's home card and in the caregiver and doctor alert lists. */
@Composable
fun AlertListItem(
    alert: Alert,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showPatientName: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(
                    if (alert.isResolved) AppTheme.clinical.inRangeContainer else alert.severity.containerColor(),
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (alert.isResolved) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (alert.isResolved) AppTheme.clinical.inRange else alert.severity.color(),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (showPatientName) alert.patientName else "${alert.valueMgdl.toInt()} mg/dL",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = buildString {
                    if (showPatientName) append("${alert.valueMgdl.toInt()} mg/dL · ")
                    append(alert.statusLabel)
                    append(" · ")
                    append(Format.relative(alert.recordedAt))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    alert.stage.icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (alert.acknowledgedButOpen)
                        "${alert.stage.label} · ${alert.patientResponse?.label}"
                    else alert.stage.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        AlertSeverityChip(alert.severity, alert.isResolved)
        Icon(
            Icons.AutoMirrored.Rounded.ArrowForward,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 6.dp).size(18.dp),
        )
    }
}

/** The charter requires this wherever escalation is shown (FR-09, NFR-15). */
@Composable
fun EmergencyNotice(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            Icons.Rounded.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "This app does not contact emergency services. In a real emergency call local " +
                "emergency services yourself and do not wait for a response here.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
        )
    }
}
