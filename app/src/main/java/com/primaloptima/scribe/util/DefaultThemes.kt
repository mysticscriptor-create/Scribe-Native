package com.primaloptima.scribe.util

import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ThemeColors

object DefaultThemes {

    val all: List<AppTheme> = listOf(
        AppTheme(
            id = "obsidian", name = "Obsidian", isDark = true, builtIn = true,
            colors = ThemeColors(
                background      = "#121214",
                surfaceLowest   = "#161619",
                surface         = "#1E1E22",
                surfaceRaised   = "#26262B",
                surfaceOverlay  = "#303036",
                text            = "#F4F4F6",
                mutedText       = "#A1A1AA",
                subtleText      = "#71717A",
                accent          = "#E4E4E7",
                accentMuted     = "#27272A",
                selection       = "#3F3F46",
                border          = "#27272A",
                borderSubtle    = "#27272A",
                borderProminent = "#71717A",
                dialogueText    = "#FEF08A",
                monologueText   = "#D4D4D8",
                headingText     = "#FAFAFA",
                toolbar         = "#1E1E22",
                toolbarText     = "#F4F4F6"
            ),
            fontFamily = "sans", fontSize = 17, lineHeight = 1.68f,
            letterSpacing = 0.1f, paragraphSpacing = 14,
            paddingHorizontal = 24, paddingVertical = 20, maxWidth = 720
        ),
        AppTheme(
            id = "midnight", name = "Midnight Blue", isDark = true, builtIn = true,
            colors = ThemeColors(
                background      = "#0B111A",
                surfaceLowest   = "#0F1722",
                surface         = "#141D2B",
                surfaceRaised   = "#1C2739",
                surfaceOverlay  = "#243248",
                text            = "#EDF2F7",
                mutedText       = "#94A3B8",
                subtleText      = "#64748B",
                accent          = "#60A5FA",
                accentMuted     = "#1E314B",
                selection       = "#25436B",
                border          = "#1E2C40",
                borderSubtle    = "#1E2C40",
                borderProminent = "#38BDF8",
                dialogueText    = "#FDE68A",
                monologueText   = "#93C5FD",
                headingText     = "#60A5FA",
                toolbar         = "#141D2B",
                toolbarText     = "#EDF2F7"
            ),
            fontFamily = "sans", fontSize = 17, lineHeight = 1.70f,
            letterSpacing = 0.15f, paragraphSpacing = 14,
            paddingHorizontal = 24, paddingVertical = 20, maxWidth = 720
        ),
        AppTheme(
            id = "focus", name = "Focus", isDark = true, builtIn = true,
            colors = ThemeColors(
                background      = "#000000",
                surfaceLowest   = "#080808",
                surface         = "#121212",
                surfaceRaised   = "#1A1A1A",
                surfaceOverlay  = "#242424",
                text            = "#EDEDED",
                mutedText       = "#888888",
                subtleText      = "#555555",
                accent          = "#E2E8F0",
                accentMuted     = "#1E293B",
                selection       = "#334155",
                border          = "#262626",
                borderSubtle    = "#1F1F1F",
                borderProminent = "#525252",
                dialogueText    = "#34D399",
                monologueText   = "#94A3B8",
                headingText     = "#F8FAFC",
                toolbar         = "#121212",
                toolbarText     = "#EDEDED"
            ),
            fontFamily = "mono", fontSize = 17, lineHeight = 1.72f,
            letterSpacing = 0f, paragraphSpacing = 14,
            paddingHorizontal = 26, paddingVertical = 22, maxWidth = 700
        ),
        AppTheme(
            id = "paper", name = "Paper", isDark = false, builtIn = true,
            colors = ThemeColors(
                background      = "#FAF8F5",
                surfaceLowest   = "#F2EFE9",
                surface         = "#FFFFFF",
                surfaceRaised   = "#FFFFFF",
                surfaceOverlay  = "#FFFFFF",
                text            = "#1F2421",
                mutedText       = "#5A655F",
                subtleText      = "#8A9690",
                accent          = "#2D5A46",
                accentMuted     = "#E8EFEA",
                selection       = "#D5E5DB",
                border          = "#E5E0D8",
                borderSubtle    = "#E8E3DB",
                borderProminent = "#2D5A46",
                dialogueText    = "#9A4D1C",
                monologueText   = "#476655",
                headingText     = "#1A3D2F",
                toolbar         = "#FFFFFF",
                toolbarText     = "#1F2421"
            ),
            fontFamily = "serif", fontSize = 18, lineHeight = 1.72f,
            letterSpacing = 0.2f, paragraphSpacing = 15,
            paddingHorizontal = 26, paddingVertical = 22, maxWidth = 720
        ),
        AppTheme(
            id = "sepia", name = "Sepia", isDark = false, builtIn = true,
            colors = ThemeColors(
                background      = "#F5EBD7",
                surfaceLowest   = "#EBDDC5",
                surface         = "#EFE3CB",
                surfaceRaised   = "#FAF2E3",
                surfaceOverlay  = "#FDF8EE",
                text            = "#382717",
                mutedText       = "#785E43",
                subtleText      = "#A48B70",
                accent          = "#8C4B18",
                accentMuted     = "#E5CEB0",
                selection       = "#DEC39E",
                border          = "#DECBB0",
                borderSubtle    = "#DECBB0",
                borderProminent = "#8C4B18",
                dialogueText    = "#B45309",
                monologueText   = "#5B422B",
                headingText     = "#713F12",
                toolbar         = "#EFE3CB",
                toolbarText     = "#382717"
            ),
            fontFamily = "serif", fontSize = 19, lineHeight = 1.75f,
            letterSpacing = 0.25f, paragraphSpacing = 16,
            paddingHorizontal = 28, paddingVertical = 24, maxWidth = 680
        ),
        AppTheme(
            id = "typewriter", name = "Typewriter", isDark = false, builtIn = true,
            colors = ThemeColors(
                background      = "#F7F7F8",
                surfaceLowest   = "#EDEDF0",
                surface         = "#FFFFFF",
                surfaceRaised   = "#FFFFFF",
                surfaceOverlay  = "#FFFFFF",
                text            = "#18181B",
                mutedText       = "#52525B",
                subtleText      = "#A1A1AA",
                accent          = "#09090B",
                accentMuted     = "#E4E4E7",
                selection       = "#D4D4D8",
                border          = "#E4E4E7",
                borderSubtle    = "#E4E4E7",
                borderProminent = "#18181B",
                dialogueText    = "#0F172A",
                monologueText   = "#52525B",
                headingText     = "#000000",
                toolbar         = "#FFFFFF",
                toolbarText     = "#18181B"
            ),
            fontFamily = "mono", fontSize = 16, lineHeight = 1.80f,
            letterSpacing = 0f, paragraphSpacing = 14,
            paddingHorizontal = 24, paddingVertical = 20, maxWidth = 680
        )
    )

    fun findById(id: String): AppTheme = all.firstOrNull { it.id == id } ?: all.first()
}
