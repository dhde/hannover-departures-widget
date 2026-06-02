package de.dhde.hannover.departures.widget.ui

import androidx.compose.ui.graphics.Color

/**
 * Zentrale Farb-Palette für App (Compose) und Widget (Glance).
 * Glance wrappt am Use-Site in ColorProvider(...). Werte 1:1 aus dem bisherigen Code,
 * jede Konstante mit Original-Hex als Selbstdoku. Kein Farbwechsel.
 */
object UestraColors {
    // ── Hintergründe / Flächen ───────────────────────────────────────────────
    val DarkBg           = Color(0xFF0D0D1A) // App-Hintergrund
    val WidgetBackground = Color(0xFF121212) // Widget-Hintergrund
    val CardBg           = Color(0xFF1A1A2E) // Karten / Surface
    val TealSurface      = Color(0xFF0F2A2A) // aktive Karte (dunkles Teal)
    val ChipInactive     = Color(0xFF2A2A2A) // inaktiver Button/Chip (Widget)
    val ChipNeutral      = Color(0xFF333333) // neutraler Chip ("Alle Typen")
    val SegmentInactive  = Color(0xFF252525) // inaktiver Segment-Button

    // ── Akzente / Marke ──────────────────────────────────────────────────────
    val Teal             = Color(0xFF0F7173) // Primärakzent
    val AccentRed        = Color(0xFFE94560) // Akzent/Storno (vormals "Red")
    val Warning          = Color(0xFFFF9800) // Verspätung/Warnung
    val Amber            = Color(0xFFFFB300) // aktives Icon / Stern-Tint
    val OkGreen          = Color(0xFF4CAF50) // pünktlich
    val GpsBlue          = Color(0xFF4285F4) // GPS aktiv
    val FavoriteGold     = Color(0xFFFFD700) // Favoriten-Stern
    val ButtonYellow     = Color(0xFFFFDD00) // gelber Aktions-Button

    // ── Text / Linien / Border ───────────────────────────────────────────────
    val TextMain         = Color(0xFFE0E0E0) // Haupttext
    val TextSub          = Color(0xFF9090AA) // Sekundärtext
    val IconMuted        = Color(0xFF666688) // gedämpftes Icon
    val Divider          = Color(0xFF333344) // Trennlinie
    val BorderSubtle     = Color(0xFF3A3A5A) // unfokussierter Feldrand
    val DarkGreenTint    = Color(0xFF141F14) // dezenter Dunkelgrün-Tint

    // ── Alpha-Overlays ───────────────────────────────────────────────────────
    val Shadow           = Color(0x4D000000) // Schatten
    val SubtleWhite      = Color(0x14FFFFFF) // dezentes Weiß-Overlay

    // ── GVH-Linienfarben (Stadtbahn-Strecken + Bus) ──────────────────────────
    val LineRed       = Color(0xFFE3001B) // ÜSTRA-Rot: B-Strecke (1,2,8) + Standard-Bus
    val LineBlue      = Color(0xFF005A9B) // A-Strecke (3,7,9,13) + Tram-Tab
    val LineYellow    = Color(0xFFFFCC00) // C-Strecke (4,5,6,11)
    val LineGreen     = Color(0xFF009A44) // D-Strecke (10,17)
    val SprintMagenta = Color(0xFFB42082) // SprintH-Busse (300–900)
}
