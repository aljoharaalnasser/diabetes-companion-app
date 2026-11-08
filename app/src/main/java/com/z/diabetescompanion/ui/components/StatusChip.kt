package com.z.diabetescompanion.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.z.diabetescompanion.domain.GlucoseStatus
import com.z.diabetescompanion.domain.Trend
import com.z.diabetescompanion.ui.theme.AppTheme

/**
 * Status is always communicated with an icon AND a text label, never colour alone (NFR-02).
 */
@Composable
fun GlucoseStatus.icon(): ImageVector = when (this) {
    GlucoseStatus.CriticalLow, GlucoseStatus.CriticalHigh -> Icons.Rounded.Warning
    GlucoseStatus.Low -> Icons.Rounded.ArrowDownward
    GlucoseStatus.High -> Icons.Rounded.ArrowUpward
    GlucoseStatus.Ideal -> Icons.Rounded.CheckCircle
    GlucoseStatus.NoThresholds -> Icons.AutoMirrored.Rounded.HelpOutline
}

@Composable
fun GlucoseStatus.color(): Color = when (this) {
    GlucoseStatus.CriticalLow -> AppTheme.clinical.veryHigh
    GlucoseStatus.Low -> AppTheme.clinical.low
    GlucoseStatus.Ideal -> AppTheme.clinical.inRange
    GlucoseStatus.High -> AppTheme.clinical.high
    GlucoseStatus.CriticalHigh -> AppTheme.clinical.veryHigh
    GlucoseStatus.NoThresholds -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun GlucoseStatus.containerColor(): Color = when (this) {
    GlucoseStatus.CriticalLow -> AppTheme.clinical.veryHighContainer
    GlucoseStatus.Low -> AppTheme.clinical.lowContainer
    GlucoseStatus.Ideal -> AppTheme.clinical.inRangeContainer
    GlucoseStatus.High -> AppTheme.clinical.highContainer
    GlucoseStatus.CriticalHigh -> AppTheme.clinical.veryHighContainer
    GlucoseStatus.NoThresholds -> MaterialTheme.colorScheme.surfaceVariant
}

@Composable
fun StatusChip(
    status: GlucoseStatus,
    modifier: Modifier = Modifier,
    onDark: Boolean = false,
) {
    val container = if (onDark) Color.White.copy(alpha = 0.18f) else status.containerColor()
    val content = if (onDark) Color.White else status.color()
    Surface(shape = CircleShape, color = container, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        ) {
            Icon(status.icon(), contentDescription = null, tint = content, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(status.label, style = MaterialTheme.typography.labelMedium, color = content)
        }
    }
}

@Composable
fun Trend.icon(): ImageVector = when (this) {
    Trend.Rising -> Icons.AutoMirrored.Rounded.TrendingUp
    Trend.Falling -> Icons.AutoMirrored.Rounded.TrendingDown
    Trend.Stable -> Icons.AutoMirrored.Rounded.TrendingFlat
    Trend.Unavailable -> Icons.Rounded.Remove
}

@Composable
fun TrendLabel(trend: Trend, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier) {
        Icon(trend.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(trend.label, style = MaterialTheme.typography.labelMedium, color = tint)
    }
}
