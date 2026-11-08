package com.z.diabetescompanion.ui.admin

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Colour role for a figure. Every tone also carries its own label, never colour alone (NFR-02). */
enum class StatTone { Neutral, Brand, Accent }

@Composable
private fun StatTone.content(): Color = when (this) {
    StatTone.Neutral -> MaterialTheme.colorScheme.onSurface
    StatTone.Brand -> MaterialTheme.colorScheme.primary
    StatTone.Accent -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun StatTone.container(): Color = when (this) {
    StatTone.Neutral -> MaterialTheme.colorScheme.surfaceVariant
    StatTone.Brand -> MaterialTheme.colorScheme.primaryContainer
    StatTone.Accent -> MaterialTheme.colorScheme.tertiaryContainer
}

/**
 * One headline figure. [value] is a string so a screen can print "—" when a metric is
 * genuinely unavailable rather than implying a real zero (NFR-15).
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tone: StatTone = StatTone.Neutral,
    caption: String? = null,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Box(
                modifier = Modifier.size(34.dp).background(tone.container(), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tone.content(), modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = tone.content(),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (caption != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = caption,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Two tiles side by side, each taking half the width and both as tall as the taller one.
 * Without the intrinsic height they go ragged as soon as one caption wraps, which happens
 * as soon as the reader turns the text size up (NFR-02).
 */
@Composable
fun StatRow(modifier: Modifier = Modifier, content: @Composable (Modifier) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().height(IntrinsicSize.Min),
    ) {
        content(Modifier.weight(1f).fillMaxHeight())
    }
}

data class BreakdownRow(val label: String, val count: Int, val tone: StatTone = StatTone.Brand)

/**
 * Horizontal proportional bars for a count breakdown. Each row prints its own number, so
 * the bar is decoration and the figure is always readable at any text size.
 */
@Composable
fun BreakdownBars(
    rows: List<BreakdownRow>,
    modifier: Modifier = Modifier,
    emptyLabel: String = "Nothing recorded yet.",
) {
    val total = rows.sumOf { it.count }
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            if (total == 0) {
                Text(
                    emptyLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                return@Column
            }
            rows.forEachIndexed { index, row ->
                if (index > 0) Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        row.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        row.count.toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = row.tone.content(),
                    )
                }
                Spacer(Modifier.height(6.dp))
                val fraction by animateFloatAsState(
                    targetValue = row.count.toFloat() / total,
                    label = "breakdownBar",
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (row.count > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction.coerceAtLeast(0.02f))
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(row.tone.content()),
                        )
                    }
                }
            }
        }
    }
}

/** Label on the left, value on the right. Used for the reporting detail lists. */
@Composable
fun DetailRow(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(12.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun DetailCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), content = content)
    }
}
