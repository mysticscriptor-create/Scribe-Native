package com.primaloptima.scribe.ui.theme

import android.content.Context
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import com.primaloptima.scribe.R
import com.primaloptima.scribe.util.font.ScribeFontManager
import java.io.File

data class FontOption(
    val key: String,
    val name: String,
    val subtitle: String
)

object FontHelper {
    private val fontProvider = GoogleFont.Provider(
        providerAuthority = "com.google.android.gms.fonts",
        providerPackage = "com.google.android.gms",
        certificates = R.array.com_google_android_gms_fonts_certs
    )

    val fontOptions = listOf(
        FontOption("default", "Default", "System"),
        FontOption("playfair", "Playfair Display", "Serif"),
        FontOption("courier", "Courier Prime", "Mono"),
        FontOption("cormorant", "Cormorant Garamond", "Elegant"),
        FontOption("inter", "Inter", "Clean"),
        FontOption("caveat", "Caveat", "Handwritten"),
        FontOption("lora", "Lora", "Literary")
    )

    /**
     * Builds a multi-weight GoogleFont FontFamily with explicit weight definitions.
     * This guarantees Jetpack Compose can resolve distinct glyph metrics for
     * Light (300), Regular (400), Medium (500), SemiBold (600), and Bold (700)
     * instead of collapsing 300 and 500 into 400.
     */
    private fun createMultiWeightGoogleFamily(
        fontName: String,
        weights: List<FontWeight> = listOf(
            FontWeight.W300,
            FontWeight.W400,
            FontWeight.W500,
            FontWeight.W600,
            FontWeight.W700,
            FontWeight.W800
        )
    ): FontFamily {
        return FontFamily(
            weights.map { w ->
                Font(
                    googleFont = GoogleFont(fontName),
                    fontProvider = fontProvider,
                    weight = w
                )
            }
        )
    }

    /**
     * Resolves a Jetpack Compose FontFamily for any built-in, downloaded, or custom font.
     */
        val googleFontProvider: GoogleFont.Provider
        get() = fontProvider

    /**
     * Resolves a preview FontFamily for online or uninstalled typefaces.
     * Uses GoogleFont with graceful fallback to category family.
     */
    fun getOnlineFontPreviewFamily(fontName: String, category: String = "sans", weight: FontWeight = FontWeight.Normal): FontFamily {
        return try {
            FontFamily(
                Font(
                    googleFont = GoogleFont(fontName),
                    fontProvider = fontProvider,
                    weight = weight
                )
            )
        } catch (_: Exception) {
            when (category.lowercase()) {
                "serif" -> FontFamily.Serif
                "mono", "monospace" -> FontFamily.Monospace
                "handwriting" -> FontFamily.Cursive
                else -> FontFamily.SansSerif
            }
        }
    }

    fun getFontFamily(fontKey: String, weight: Int = 400): FontFamily {
        try {
            val app = com.primaloptima.scribe.ScribeApp.instance
            return ScribeFontManager.resolveFontFamily(app, fontKey, weight)
        } catch (_: Exception) {}

        val norm = fontKey.lowercase().trim()

        return when (norm) {
            "playfair", "playfair display", "serif" ->
                createMultiWeightGoogleFamily("Playfair Display")

            "courier", "courier prime", "mono" ->
                createMultiWeightGoogleFamily("Courier Prime", listOf(FontWeight.W400, FontWeight.W700))

            "cormorant", "cormorant garamond" ->
                createMultiWeightGoogleFamily("Cormorant Garamond")

            "inter", "inter clean", "sans" ->
                createMultiWeightGoogleFamily("Inter")

            "caveat", "caveat handwritten" ->
                createMultiWeightGoogleFamily("Caveat")

            "lora", "lora literary" ->
                createMultiWeightGoogleFamily("Lora")

            "jetbrains_mono", "jetbrains mono" ->
                createMultiWeightGoogleFamily("JetBrains Mono")

            "default", "system", "" ->
                FontFamily.Default

            else -> {
                // If it's a custom/downloaded font, check ScribeFontManager's custom fonts
                // If a local TTF exists, construct a Compose Font from file
                val customFonts = ScribeFontManager.builtInFonts
                // Try to resolve from file if path is known or fallback to default
                FontFamily.Default
            }
        }
    }

    /**
     * Context-aware Compose FontFamily resolver that seamlessly resolves custom and downloaded fonts.
     */
    fun getFontFamilyWithContext(context: Context, fontKey: String, weight: Int = 400): FontFamily {
        return ScribeFontManager.resolveFontFamily(context, fontKey, weight)
    }
}
