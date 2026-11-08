package com.z.diabetescompanion.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Brand palette taken from the logo: bright blue glucose meter, deep blue shadow,
// red blood drop, white face panel. Blue is the primary, red the accent.
// ---------------------------------------------------------------------------

val Blue10 = Color(0xFF071B3F)
val Blue20 = Color(0xFF0A2E6B)
val Blue30 = Color(0xFF0050B0)
val Blue40 = Color(0xFF0A6FD1)
/** The logo's exact meter blue; used for gradients and accents, not for text on white. */
val Blue50 = Color(0xFF0090F8)
val Blue80 = Color(0xFF8FC0FF)
val Blue90 = Color(0xFFDCEBFF)
val Blue95 = Color(0xFFEAF3FF)

val Navy30 = Color(0xFF1246A8)
val Navy40 = Color(0xFF2B5BC4)
val Navy80 = Color(0xFFB4C5FF)
val Navy90 = Color(0xFFDFE6FF)

val Red10 = Color(0xFF4A0808)
val Red30 = Color(0xFF8C1D1D)
val Red40 = Color(0xFFDC3A34)
/** The logo's exact drop red. */
val Red50 = Color(0xFFF85048)
val Red80 = Color(0xFFFF8A80)
val Red90 = Color(0xFFFFE0DD)

val NeutralBackgroundLight = Color(0xFFF4F8FF)
val NeutralSurfaceLight = Color(0xFFFFFFFF)
val NeutralSurfaceVariantLight = Color(0xFFE4EDFA)
val NeutralOnSurfaceLight = Color(0xFF101C33)
val NeutralOnSurfaceVariantLight = Color(0xFF4A5773)
val NeutralOutlineLight = Color(0xFF7A88A6)
val NeutralOutlineVariantLight = Color(0xFFC7D3E8)

val NeutralBackgroundDark = Color(0xFF0B1220)
val NeutralSurfaceDark = Color(0xFF111A2C)
val NeutralSurfaceVariantDark = Color(0xFF232E45)
val NeutralOnSurfaceDark = Color(0xFFE1E8F5)
val NeutralOnSurfaceVariantDark = Color(0xFFB3BFD6)
val NeutralOutlineDark = Color(0xFF7E8BA6)
val NeutralOutlineVariantDark = Color(0xFF38445C)

val ErrorLight = Color(0xFFC62828)
val ErrorDark = Color(0xFFFF8A80)

/**
 * Clinical semantic colours used for glucose ranges and health status chips.
 * Provided through [LocalClinicalColors] so screens never hardcode hex values.
 * "Low" uses indigo rather than blue so it never reads as the brand colour.
 */
@Immutable
data class ClinicalColors(
    val inRange: Color,
    val inRangeContainer: Color,
    val low: Color,
    val lowContainer: Color,
    val high: Color,
    val highContainer: Color,
    val veryHigh: Color,
    val veryHighContainer: Color,
)

val LightClinicalColors = ClinicalColors(
    inRange = Color(0xFF15803D),
    inRangeContainer = Color(0xFFDCFCE7),
    low = Color(0xFF6D28D9),
    lowContainer = Color(0xFFEDE9FE),
    high = Color(0xFFB45309),
    highContainer = Color(0xFFFEF3C7),
    veryHigh = Color(0xFFB91C1C),
    veryHighContainer = Color(0xFFFEE2E2),
)

val DarkClinicalColors = ClinicalColors(
    inRange = Color(0xFF4ADE80),
    inRangeContainer = Color(0xFF14532D),
    low = Color(0xFFC4B5FD),
    lowContainer = Color(0xFF4C1D95),
    high = Color(0xFFFCD34D),
    highContainer = Color(0xFF78350F),
    veryHigh = Color(0xFFFCA5A5),
    veryHighContainer = Color(0xFF7F1D1D),
)

val LocalClinicalColors = staticCompositionLocalOf { LightClinicalColors }
