package com.z.diabetescompanion.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.z.diabetescompanion.ui.components.EmptyState
import com.z.diabetescompanion.ui.components.ScreenScaffold

/** Empty-state page for features scheduled in a later sprint. */
@Composable
fun PlaceholderScreen(
    title: String,
    description: String,
    sprint: Int,
    icon: ImageVector = Icons.Rounded.Construction,
    onBack: (() -> Unit)? = null,
) {
    ScreenScaffold(title = title, onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = icon,
                title = title,
                description = description,
                action = {
                    SuggestionChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Planned for Sprint $sprint") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            disabledContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            disabledLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        border = null,
                    )
                },
            )
        }
    }
}
