package com.nu11b1ade.watermarkcam.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import com.nu11b1ade.watermarkcam.watermark.WatermarkStyle

val WarmOrange = Color(WatermarkStyle.ACCENT_ARGB)

internal val LightColors = lightColorScheme(
  primary = WarmOrange,
  onPrimary = Color(0xFF2D1300),
  primaryContainer = Color(0xFFFFE2CC),
  onPrimaryContainer = Color(0xFF5C2800),
  secondary = Color(0xFF80543B),
  onSecondary = Color.White,
  secondaryContainer = Color(0xFFFFE0CB),
  onSecondaryContainer = Color(0xFF321403),
  tertiary = Color(0xFF53634D),
  background = Color(0xFFFFF8F3),
  onBackground = Color(0xFF291D16),
  surface = Color(0xFFFFFBF7),
  onSurface = Color(0xFF291D16),
  surfaceVariant = Color(0xFFF2E4D9),
  onSurfaceVariant = Color(0xFF705D50),
  surfaceContainer = Color(0xFFF7EEE7),
  surfaceContainerLow = Color(0xFFFFFBF7),
  surfaceContainerHigh = Color(0xFFF0E4DA),
  outline = Color(0xFF8C7565),
  outlineVariant = Color(0xFFE0CFC0),
)

internal val DarkColors = darkColorScheme(
  primary = WarmOrange,
  onPrimary = Color(0xFF2D1300),
  primaryContainer = Color(0xFF683000),
  onPrimaryContainer = Color(0xFFFFDBC2),
  secondary = Color(0xFFF2BB95),
  onSecondary = Color(0xFF482811),
  secondaryContainer = Color(0xFF60412C),
  onSecondaryContainer = Color(0xFFFFE0CB),
  tertiary = Color(0xFFBCCCB1),
  background = Color(0xFF171310),
  onBackground = Color(0xFFF5E7DB),
  surface = Color(0xFF201B17),
  onSurface = Color(0xFFF5E7DB),
  surfaceVariant = Color(0xFF514238),
  onSurfaceVariant = Color(0xFFD6C2B4),
  surfaceContainer = Color(0xFF28211C),
  surfaceContainerLow = Color(0xFF201B17),
  surfaceContainerHigh = Color(0xFF342A23),
  outline = Color(0xFFA28C7E),
  outlineVariant = Color(0xFF514238),
)
