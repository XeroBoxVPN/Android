package io.nekohasekai.sfa.compose.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// XeroBox brand palette, shared with the Windows app: "Void" (dark) is a near-black
// ground with a cyan primary and violet secondary; "Paper" is the light twin. The X
// mark gradient runs mint → cyan → violet; mint also means "protected".
// ---------------------------------------------------------------------------

// Dark scheme (the primary experience)
val XeroDarkBackground = Color(0xFF07080D)
val XeroDarkSurface = Color(0xFF0A0C14)
val XeroDarkSurfaceContainerLowest = Color(0xFF05060A)
val XeroDarkSurfaceContainerLow = Color(0xFF0E111B)
val XeroDarkSurfaceContainer = Color(0xFF121624)
val XeroDarkSurfaceContainerHigh = Color(0xFF181D2E)
val XeroDarkSurfaceContainerHighest = Color(0xFF1F2539)
val XeroDarkSurfaceVariant = Color(0xFF1A2033)
val XeroDarkOnSurface = Color(0xFFE8ECF7)
val XeroDarkOnSurfaceVariant = Color(0xFFA2ABC2)
val XeroDarkOutline = Color(0xFF3A4360)
val XeroDarkOutlineVariant = Color(0xFF252C42)
val XeroDarkPrimary = Color(0xFF4FD3FF)
val XeroDarkOnPrimary = Color(0xFF00202D)
val XeroDarkPrimaryContainer = Color(0xFF004A66)
val XeroDarkOnPrimaryContainer = Color(0xFFC2ECFF)
val XeroDarkSecondary = Color(0xFFA69CFF)
val XeroDarkOnSecondary = Color(0xFF1C1466)
val XeroDarkSecondaryContainer = Color(0xFF352C8C)
val XeroDarkOnSecondaryContainer = Color(0xFFE3DFFF)
val XeroDarkTertiary = Color(0xFF5CFFC9)
val XeroDarkOnTertiary = Color(0xFF00382A)
val XeroDarkTertiaryContainer = Color(0xFF00523E)
val XeroDarkOnTertiaryContainer = Color(0xFFB8FFE6)
val XeroDarkError = Color(0xFFFF8A80)
val XeroDarkOnError = Color(0xFF5C0A0A)
val XeroDarkErrorContainer = Color(0xFF7F1D1D)
val XeroDarkOnErrorContainer = Color(0xFFFFDAD6)

// Light scheme
val XeroLightBackground = Color(0xFFEEF1F8)
val XeroLightSurface = Color(0xFFF6F8FC)
val XeroLightSurfaceContainerLowest = Color(0xFFFFFFFF)
val XeroLightSurfaceContainerLow = Color(0xFFF1F4FA)
val XeroLightSurfaceContainer = Color(0xFFEBEFF7)
val XeroLightSurfaceContainerHigh = Color(0xFFE5EAF3)
val XeroLightSurfaceContainerHighest = Color(0xFFDEE4EF)
val XeroLightSurfaceVariant = Color(0xFFDEE4EF)
val XeroLightOnSurface = Color(0xFF0B1020)
val XeroLightOnSurfaceVariant = Color(0xFF465069)
val XeroLightOutline = Color(0xFF77819A)
val XeroLightOutlineVariant = Color(0xFFC5CCDB)
val XeroLightPrimary = Color(0xFF0072A3)
val XeroLightOnPrimary = Color(0xFFFFFFFF)
val XeroLightPrimaryContainer = Color(0xFFCBEEFF)
val XeroLightOnPrimaryContainer = Color(0xFF001E2C)
val XeroLightSecondary = Color(0xFF5547D6)
val XeroLightOnSecondary = Color(0xFFFFFFFF)
val XeroLightSecondaryContainer = Color(0xFFE4E0FF)
val XeroLightOnSecondaryContainer = Color(0xFF160C5E)
val XeroLightTertiary = Color(0xFF00836A)
val XeroLightOnTertiary = Color(0xFFFFFFFF)
val XeroLightTertiaryContainer = Color(0xFFB5F7E1)
val XeroLightOnTertiaryContainer = Color(0xFF002118)
val XeroLightError = Color(0xFFBA1A1A)
val XeroLightOnError = Color(0xFFFFFFFF)
val XeroLightErrorContainer = Color(0xFFFFDAD6)
val XeroLightOnErrorContainer = Color(0xFF410002)

// Protection state (semantic, separate from the accent)
val ProtectedDark = Color(0xFF5CFFC9)
val ProtectedLight = Color(0xFF00836A)
val UnprotectedDark = Color(0xFFFFC46B)
val UnprotectedLight = Color(0xFFB26A00)

// ---------------------------------------------------------------------------
// Existing names, kept so other screens keep compiling. The brand primary now
// follows the XeroBox accent.
// ---------------------------------------------------------------------------
val SingBoxPrimary = XeroLightPrimary
val SingBoxPrimaryDark = XeroDarkPrimaryContainer
val SingBoxPrimaryLight = XeroDarkPrimary

// Service status colors
val ServiceRunning = Color(0xFF00A86B)
val ServiceStopped = Color(0xFF8A94A6)
val ServiceError = Color(0xFFE5484D)

// Log colors
val LogRed = Color(0xFFFF2158)
val LogGreen = Color(0xFF2ECC71)
val LogYellow = Color(0xFFE5E500)
val LogBlue = Color(0xFF3498DB)
val LogPurple = Color(0xFFE500E5)
val LogRedLight = Color(0xFFE91E63)
val LogBlueLight = Color(0xFF00A6B2)
val LogWhite = Color(0xFFECECEC)

// Material You seed color
val SeedColor = XeroLightPrimary

// Additional semantic colors
val SuccessGreen = Color(0xFF00A86B)
val WarningOrange = Color(0xFFE69500)
val ErrorRed = Color(0xFFE5484D)
val InfoBlue = Color(0xFF3A7BDB)

// Security-conveying design palette (used by SecurityBadge / LicenseScreen)
val SecureGreen = ProtectedDark
val SecureGreenDark = ProtectedLight
val TrustBlue = XeroLightSecondary
val TrustBlueDark = XeroDarkSecondaryContainer
val ShieldGold = XeroDarkTertiary
val ShieldGoldDark = XeroLightTertiary
val SecureGradientStart = Color(0xFF171D33)
val SecureGradientEnd = Color(0xFF06070C)

// Connected state colors
val ConnectedBackground = Color(0xFF003828)
val ConnectedBackgroundLight = Color(0xFFDDF9EF)
val DisconnectedBackground = Color(0xFF1F2539)

// The X mark gradient (also in windows/assets/mark.svg and the panel favicon).
val XeroMint = Color(0xFF5CFFC9)
val XeroCyan = Color(0xFF2FCBFF)
val XeroViolet = Color(0xFF6A5CFF)
