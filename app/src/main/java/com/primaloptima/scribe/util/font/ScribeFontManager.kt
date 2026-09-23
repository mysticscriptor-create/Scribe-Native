package com.primaloptima.scribe.util.font

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.compose.ui.text.font.FontFamily
import com.primaloptima.scribe.ui.theme.FontHelper
import com.primaloptima.scribe.util.ThemeManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.nio.charset.StandardCharsets

/**
 * Metadata model for fonts available in Scribe (built-in, downloaded, and custom imported).
 */
data class ScribeFont(
    val id: String,
    val name: String,
    val category: String, // "serif", "sans", "mono", "display", "handwriting", "custom"
    val isVariable: Boolean = false,
    val weights: List<Int> = listOf(400),
    val isCustom: Boolean = false,
    val filePath: String? = null,
    val weightFilePaths: Map<Int, String> = emptyMap(),
    val license: String? = "OFL-1.1"
)

/**
 * Centralized Font Management Engine for Scribe.
 * 
 * Coordinates:
 * 1. Storage & registration of downloaded and imported fonts.
 * 2. OpenType TTF/OTF metadata parsing (font family name, variable 'fvar' tables).
 * 3. High-fidelity Typeface resolution for Sora CodeEditor (with 'wght' variation axis).
 * 4. Compose FontFamily resolution for Manuscript Titles, theme previews, and HUD UI.
 */
object ScribeFontManager {
    private const val TAG = "ScribeFontManager"
    private const val REGISTRY_FILE = "fonts_registry.json"

    // In-memory cache for resolved Typefaces to avoid frequent disk I/O
    private val typefaceCache = mutableMapOf<String, Typeface>()

    val builtInFonts: List<ScribeFont> = listOf(
        ScribeFont(
            id = "default",
            name = "Default System",
            category = "sans",
            isVariable = false,
            weights = listOf(300, 400, 500, 700),
            isCustom = false
        ),
        ScribeFont(
            id = "inter",
            name = "Inter Clean",
            category = "sans",
            isVariable = true,
            weights = listOf(300, 400, 500, 600, 700, 800),
            isCustom = false
        ),
        ScribeFont(
            id = "lora",
            name = "Lora Literary",
            category = "serif",
            isVariable = true,
            weights = listOf(400, 500, 600, 700),
            isCustom = false
        ),
        ScribeFont(
            id = "cormorant",
            name = "Cormorant Garamond",
            category = "serif",
            isVariable = true,
            weights = listOf(300, 400, 500, 600, 700),
            isCustom = false
        ),
        ScribeFont(
            id = "playfair",
            name = "Playfair Display",
            category = "display",
            isVariable = true,
            weights = listOf(400, 500, 600, 700, 800),
            isCustom = false
        ),
        ScribeFont(
            id = "courier",
            name = "Courier Prime",
            category = "mono",
            isVariable = false,
            weights = listOf(400, 700),
            isCustom = false
        ),
        ScribeFont(
            id = "jetbrains_mono",
            name = "JetBrains Mono",
            category = "mono",
            isVariable = true,
            weights = listOf(300, 400, 500, 700),
            isCustom = false
        ),
        ScribeFont(
            id = "caveat",
            name = "Caveat Handwritten",
            category = "handwriting",
            isVariable = true,
            weights = listOf(400, 500, 600, 700),
            isCustom = false
        )
    )

    private fun getFontsDir(context: Context): File {
        val dir = File(context.filesDir, "fonts")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    private fun getRegistryFile(context: Context): File {
        return File(getFontsDir(context), REGISTRY_FILE)
    }

    /**
     * Retrieves all custom (downloaded + imported) fonts stored on device.
     */
    @Synchronized
    fun getCustomFonts(context: Context): List<ScribeFont> {
        val file = getRegistryFile(context)
        if (!file.exists()) return emptyList()

        return try {
            val jsonStr = file.readText()
            val array = JSONArray(jsonStr)
            val list = mutableListOf<ScribeFont>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val category = obj.optString("category", "custom")
                val isVariable = obj.optBoolean("isVariable", false)
                val filePath = obj.optString("filePath", null)
                val license = obj.optString("license", "OFL-1.1")

                val weightsList = mutableListOf<Int>()
                val weightsArray = obj.optJSONArray("weights")
                if (weightsArray != null) {
                    for (w in 0 until weightsArray.length()) {
                        weightsList.add(weightsArray.getInt(w))
                    }
                }
                if (weightsList.isEmpty()) {
                    weightsList.add(400)
                }

                val weightFilesMap = mutableMapOf<Int, String>()
                val weightFilesObj = obj.optJSONObject("weightFiles")
                if (weightFilesObj != null) {
                    val keys = weightFilesObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val wInt = k.toIntOrNull()
                        if (wInt != null) {
                            weightFilesMap[wInt] = weightFilesObj.getString(k)
                        }
                    }
                }

                // Verify primary file still exists
                if (filePath != null && File(filePath).exists()) {
                    list.add(
                        ScribeFont(
                            id = id,
                            name = name,
                            category = category,
                            isVariable = isVariable,
                            weights = weightsList,
                            isCustom = true,
                            filePath = filePath,
                            weightFilePaths = weightFilesMap,
                            license = license
                        )
                    )
                }
            }
            list
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read font registry", e)
            emptyList()
        }
    }

    /**
     * Retrieves all fonts: built-in presets followed by user custom / downloaded fonts.
     */
    fun getAllFonts(context: Context): List<ScribeFont> {
        val custom = getCustomFonts(context)
        return builtInFonts + custom
    }

    /**
     * Checks if a font (built-in or custom) is available by its key/ID.
     */
    fun isFontInstalled(context: Context, fontKey: String): Boolean {
        val norm = fontKey.lowercase().trim()
        if (builtInFonts.any { it.id.equals(norm, ignoreCase = true) || it.name.equals(norm, ignoreCase = true) }) {
            return true
        }
        return getCustomFonts(context).any { it.id.equals(norm, ignoreCase = true) }
    }

    /**
     * Imports a TTF or OTF font from a user-selected SAF Uri.
     */
    suspend fun importFontFromUri(context: Context, uri: Uri): Result<ScribeFont> = withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
                ?: return@withContext Result.failure(Exception("Cannot open file stream"))

            val bytes = inputStream.use { it.readBytes() }
            if (bytes.size < 12) {
                return@withContext Result.failure(Exception("File too small to be a valid font"))
            }

            // Parse OpenType metadata
            val fontInfo = parseFontMetadata(bytes)
                ?: return@withContext Result.failure(Exception("Invalid font format (.ttf/.otf required)"))

            val safeId = "custom_" + System.currentTimeMillis() + "_" +
                    fontInfo.familyName.lowercase().replace("[^a-z0-9]".toRegex(), "_")
            val targetFile = File(getFontsDir(context), "$safeId.ttf")

            FileOutputStream(targetFile).use { it.write(bytes) }

            val font = ScribeFont(
                id = safeId,
                name = fontInfo.familyName.ifBlank { "Custom Font" },
                category = "custom",
                isVariable = fontInfo.isVariable,
                weights = if (fontInfo.isVariable) listOf(300, 400, 500, 600, 700, 800) else listOf(400),
                isCustom = true,
                filePath = targetFile.absolutePath,
                license = "Custom Import"
            )

            saveFontToRegistry(context, font)
            clearCache()
            Result.success(font)
        } catch (e: Exception) {
            Log.e(TAG, "Error importing font from URI", e)
            Result.failure(e)
        }
    }

    /**
     * Saves a font downloaded from the online library.
     */
    suspend fun saveDownloadedFont(
        context: Context,
        id: String,
        name: String,
        category: String,
        bytes: ByteArray,
        supportedWeights: List<Int> = listOf(400),
        license: String = "OFL-1.1"
    ): Result<ScribeFont> = withContext(Dispatchers.IO) {
        try {
            if (bytes.size < 12) {
                return@withContext Result.failure(Exception("Downloaded font file is empty or corrupted"))
            }

            val fontInfo = parseFontMetadata(bytes)
            val isVariable = fontInfo?.isVariable ?: false
            val parsedName = fontInfo?.familyName?.takeIf { it.isNotBlank() } ?: name

            val targetFile = File(getFontsDir(context), "$id.ttf")
            FileOutputStream(targetFile).use { it.write(bytes) }

            val font = ScribeFont(
                id = id,
                name = parsedName,
                category = category,
                isVariable = isVariable,
                weights = if (isVariable) listOf(300, 400, 500, 600, 700, 800) else supportedWeights,
                isCustom = true,
                filePath = targetFile.absolutePath,
                license = license
            )

            saveFontToRegistry(context, font)
            clearCache()
            Result.success(font)
        } catch (e: Exception) {
            Log.e(TAG, "Error saving downloaded font", e)
            Result.failure(e)
        }
    }

    /**
     * Deletes a custom or downloaded font.
     */
    @Synchronized
    fun deleteCustomFont(context: Context, fontId: String): Boolean {
        try {
            val registry = getCustomFonts(context).toMutableList()
            val fontToRemove = registry.find { it.id == fontId } ?: return false

            fontToRemove.filePath?.let { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }
            fontToRemove.weightFilePaths.values.forEach { path ->
                val f = File(path)
                if (f.exists()) f.delete()
            }

            registry.removeAll { it.id == fontId }
            writeRegistry(context, registry)
            clearCache()
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete font $fontId", e)
            return false
        }
    }

    @Synchronized
    private fun saveFontToRegistry(context: Context, newFont: ScribeFont) {
        val current = getCustomFonts(context).filter { it.id != newFont.id }.toMutableList()
        current.add(0, newFont)
        writeRegistry(context, current)
    }

    @Synchronized
    private fun writeRegistry(context: Context, fonts: List<ScribeFont>) {
        val array = JSONArray()
        fonts.forEach { font ->
            val obj = JSONObject().apply {
                put("id", font.id)
                put("name", font.name)
                put("category", font.category)
                put("isVariable", font.isVariable)
                put("filePath", font.filePath)
                put("license", font.license ?: "OFL-1.1")
                val wArr = JSONArray()
                font.weights.forEach { wArr.put(it) }
                put("weights", wArr)
                if (font.weightFilePaths.isNotEmpty()) {
                    val wObj = JSONObject()
                    font.weightFilePaths.forEach { (w, p) -> wObj.put(w.toString(), p) }
                    put("weightFiles", wObj)
                }
            }
            array.put(obj)
        }
        val file = getRegistryFile(context)
        file.writeText(array.toString(2))
    }

    /**
     * Resolves an [android.graphics.Typeface] with full weight control (300, 400, 500, etc.)
     * for Sora CodeEditor and native Android Views.
     */
    fun resolveTypeface(context: Context, fontKey: String?, weight: Int = 400): Typeface {
        val rawKey = fontKey?.trim() ?: "default"
        val cacheKey = "${rawKey.lowercase()}_$weight"

        synchronized(typefaceCache) {
            typefaceCache[cacheKey]?.let { return it }
        }

        val resolvedTf = runCatching {
            // 1. Check custom fonts
            val custom = getCustomFonts(context).find {
                it.id.equals(rawKey, ignoreCase = true) || it.name.equals(rawKey, ignoreCase = true)
            }

            if (custom != null && custom.filePath != null) {
                val fontFile = File(custom.filePath)
                if (fontFile.exists()) {
                    if (custom.isVariable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        Typeface.Builder(fontFile)
                            .setFontVariationSettings("'wght' $weight")
                            .setWeight(weight)
                            .build()
                    } else {
                        // Check if static weight file exists
                        val specificPath = custom.weightFilePaths[weight]
                        val targetFile = if (specificPath != null && File(specificPath).exists()) {
                            File(specificPath)
                        } else fontFile

                        val base = Typeface.createFromFile(targetFile)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            Typeface.create(base, weight, false)
                        } else {
                            if (weight >= 600) Typeface.create(base, Typeface.BOLD) else base
                        }
                    }
                } else null
            } else {
                null
            }
        }.getOrNull()

        val finalTf = resolvedTf ?: run {
            // Built-in font resolution via ThemeManager
            ThemeManager.resolveTypeface(context, rawKey, weight)
        }

        synchronized(typefaceCache) {
            typefaceCache[cacheKey] = finalTf
        }

        return finalTf
    }

    /**
     * Resolves a Jetpack Compose [FontFamily] using the exact same underlying Typeface engine.
     * Guarantees 100% visual consistency between Titles in Compose and Document Prose in Sora.
     */
    fun resolveFontFamily(context: Context, fontKey: String?, weight: Int = 400): FontFamily {
        val tf = resolveTypeface(context, fontKey, weight)
        return FontFamily(androidx.compose.ui.text.font.Typeface(tf))
    }

    private fun clearCache() {
        synchronized(typefaceCache) {
            typefaceCache.clear()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // OpenType Header & Metadata Parser
    // ─────────────────────────────────────────────────────────────────────────────

    data class ParsedFontInfo(
        val familyName: String,
        val isVariable: Boolean
    )

    /**
     * Reads OpenType / TrueType sfnt table directory:
     * - Detects 'fvar' table for variable fonts.
     * - Parses 'name' table (ID 1 = Family Name, ID 16 = Typographic Family).
     */
    private fun parseFontMetadata(bytes: ByteArray): ParsedFontInfo? {
        return try {
            val dis = DataInputStream(ByteArrayInputStream(bytes))
            val sfntVersion = dis.readInt()

            // Verify magic sfnt version: 0x00010000 (TrueType), 0x4F54544F ("OTTO"), 0x74746366 ("ttcf")
            val isValidMagic = sfntVersion == 0x00010000 ||
                    sfntVersion == 0x4F54544F ||
                    sfntVersion == 0x74746366
            if (!isValidMagic) return null

            val numTables = dis.readUnsignedShort()
            dis.skipBytes(6) // searchRange, entrySelector, rangeShift

            var hasFvar = false
            var nameOffset = 0
            var nameLength = 0

            for (i in 0 until numTables) {
                val tag = dis.readInt()
                val checksum = dis.readInt()
                val offset = dis.readInt()
                val length = dis.readInt()

                // 0x66766172 = 'fvar'
                if (tag == 0x66766172) {
                    hasFvar = true
                }
                // 0x6E616D65 = 'name'
                if (tag == 0x6E616D65) {
                    nameOffset = offset
                    nameLength = length
                }
            }

            var familyName = ""
            if (nameOffset > 0 && nameOffset + nameLength <= bytes.size) {
                familyName = parseNameTable(bytes, nameOffset)
            }

            ParsedFontInfo(
                familyName = familyName,
                isVariable = hasFvar
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse OpenType metadata", e)
            null
        }
    }

    private fun parseNameTable(bytes: ByteArray, offset: Int): String {
        return try {
            val dis = DataInputStream(ByteArrayInputStream(bytes, offset, bytes.size - offset))
            val format = dis.readUnsignedShort()
            val count = dis.readUnsignedShort()
            val stringOffset = dis.readUnsignedShort()

            val names = mutableMapOf<Int, String>()

            for (i in 0 until count) {
                val platformId = dis.readUnsignedShort()
                val encodingId = dis.readUnsignedShort()
                val languageId = dis.readUnsignedShort()
                val nameId = dis.readUnsignedShort()
                val length = dis.readUnsignedShort()
                val strOffset = dis.readUnsignedShort()

                val actualOffset = offset + stringOffset + strOffset
                if (actualOffset + length <= bytes.size) {
                    val strBytes = bytes.copyOfRange(actualOffset, actualOffset + length)
                    val strVal = if (platformId == 0 || platformId == 3) {
                        // Unicode / Microsoft UTF-16BE
                        String(strBytes, StandardCharsets.UTF_16BE).replace("\u0000", "").trim()
                    } else {
                        // Mac / ISO Latin1
                        String(strBytes, StandardCharsets.ISO_8859_1).replace("\u0000", "").trim()
                    }
                    if (strVal.isNotBlank() && (nameId == 1 || nameId == 4 || nameId == 16)) {
                        names[nameId] = strVal
                    }
                }
            }

            // Prefer Typographic Family (16) -> Font Family (1) -> Full Name (4)
            names[16] ?: names[1] ?: names[4] ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
