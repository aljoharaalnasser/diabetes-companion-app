package com.z.diabetescompanion.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.z.diabetescompanion.R

/** The app logo: a smiling glucose meter and blood drop. Transparent PNG, so it sits on any surface. */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
) {
    Image(
        painter = painterResource(R.drawable.brand_logo),
        contentDescription = "Diabetes Companion logo",
        modifier = modifier.size(size),
    )
}

/** Mark + wordmark, used in the More tab and the auth pages. */
@Composable
fun BrandLockup(
    modifier: Modifier = Modifier,
    markSize: Dp = 48.dp,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(size = markSize)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = "Diabetes Companion",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "Track. Understand. Thrive.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
