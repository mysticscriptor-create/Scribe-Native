package com.primaloptima.scribe.util.font

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class OnlineFontItem(
    val id: String,
    val name: String,
    val category: String, // "all", "serif", "sans", "mono", "display", "handwriting"
    val description: String,
    val isVariable: Boolean = false,
    val weights: List<Int> = listOf(300, 400, 500, 600, 700),
    val ttfUrl: String,
    val license: String = "OFL-1.1"
)

object OnlineFontLibrary {
    private const val TAG = "OnlineFontLibrary"

    // ─────────────────────────────────────────────────────────────────────────────
    // Curated Editorial & Literary Font Catalog (Available Offline & Online)
    // ─────────────────────────────────────────────────────────────────────────────
    val curatedFonts: List<OnlineFontItem> = listOf(
        // Serif & Literary
        OnlineFontItem(
            id = "merriweather",
            name = "Merriweather",
            category = "serif",
            description = "Designed by Sorkin Type for effortless long-form reading on digital displays.",
            weights = listOf(300, 400, 700, 900),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/merriweather@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "eb-garamond",
            name = "EB Garamond",
            category = "serif",
            description = "Faithful classical revival of Claude Garamont’s legendary 16th-century humanist designs.",
            weights = listOf(400, 500, 600, 700, 800),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/eb-garamond@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "literata",
            name = "Literata",
            category = "serif",
            description = "Commissioned by Google for Google Play Books, optimized for intense novel reading.",
            weights = listOf(300, 400, 500, 600, 700),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/literata@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "libre-baskerville",
            name = "Libre Baskerville",
            category = "serif",
            description = "Web font optimized for body text with tall x-height and generous counters.",
            weights = listOf(400, 700),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/libre-baskerville@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "spectral",
            name = "Spectral",
            category = "serif",
            description = "Commissioned by Google for seamless editorial flow in rich typographic layouts.",
            weights = listOf(300, 400, 500, 600, 700, 800),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/spectral@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "alegreya",
            name = "Alegreya",
            category = "serif",
            description = "Chosen as one of 53 'Fonts of the Decade', offering a rhythmic dynamic reading flow.",
            weights = listOf(400, 500, 600, 700, 800),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/alegreya@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "bitter",
            name = "Bitter",
            category = "serif",
            description = "Contemporary slab serif designed for comfortable reading on low-contrast screens.",
            weights = listOf(300, 400, 500, 600, 700),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/bitter@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "crimson-pro",
            name = "Crimson Pro",
            category = "serif",
            description = "Noble book typeface inspired by traditional Garamond and Renaissance press.",
            weights = listOf(300, 400, 500, 600, 700),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/crimson-pro@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "cinzel",
            name = "Cinzel",
            category = "display",
            description = "Inspired by first-century Roman inscriptions, ideal for chapter titles and headers.",
            weights = listOf(400, 500, 600, 700, 800),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/cinzel@latest/latin-400-normal.ttf"
        ),

        // Sans-Serif & Clean
        OnlineFontItem(
            id = "plus-jakarta-sans",
            name = "Plus Jakarta Sans",
            category = "sans",
            description = "Clean modern geometric sans-serif with subtle warm nuances and pristine legibility.",
            weights = listOf(300, 400, 500, 600, 700, 800),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/plus-jakarta-sans@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "outfit",
            name = "Outfit",
            category = "sans",
            description = "Contemporary geometric sans typeface with balanced curves and high clarity.",
            weights = listOf(300, 400, 500, 600, 700, 800),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/outfit@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "work-sans",
            name = "Work Sans",
            category = "sans",
            description = "Based on early grotesques, tuned for medium-size reading and clean aesthetics.",
            weights = listOf(300, 400, 500, 600, 700),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/work-sans@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "space-grotesk",
            name = "Space Grotesk",
            category = "sans",
            description = "Proportional variant of Space Mono, retaining distinctive quirky monospace details.",
            weights = listOf(300, 400, 500, 600, 700),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/space-grotesk@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "dm-sans",
            name = "DM Sans",
            category = "sans",
            description = "Low-contrast geometric sans-serif designed for clean, precise digital reading.",
            weights = listOf(400, 500, 700),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/dm-sans@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "quicksand",
            name = "Quicksand",
            category = "sans",
            description = "Display sans-serif with rounded terminals, conveying a soft and approachable tone.",
            weights = listOf(300, 400, 500, 600, 700),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/quicksand@latest/latin-400-normal.ttf"
        ),

        // Monospace & Typewriter
        OnlineFontItem(
            id = "fira-code",
            name = "Fira Code",
            category = "mono",
            description = "Monospaced font with programmed ligatures and exceptional character differentiation.",
            weights = listOf(300, 400, 500, 600, 700),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/fira-code@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "space-mono",
            name = "Space Mono",
            category = "mono",
            description = "Original geometric monospace combining 1960s sci-fi aesthetic with modern craft.",
            weights = listOf(400, 700),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/space-mono@latest/latin-400-normal.ttf"
        ),

        // Handwriting & Journal
        OnlineFontItem(
            id = "dancing-script",
            name = "Dancing Script",
            category = "handwriting",
            description = "Lively casual script where letters bounce and shift in size like natural handwriting.",
            weights = listOf(400, 500, 600, 700),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/dancing-script@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "kalam",
            name = "Kalam",
            category = "handwriting",
            description = "Handwritten handwriting font imitating ballpoint pen writing on paper.",
            weights = listOf(300, 400, 700),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/kalam@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "satisfy",
            name = "Satisfy",
            category = "handwriting",
            description = "Graceful brush script with a timeless vintage handwritten touch.",
            weights = listOf(400),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/satisfy@latest/latin-400-normal.ttf"
        ),
        OnlineFontItem(
            id = "shadows-into-light",
            name = "Shadows Into Light",
            category = "handwriting",
            description = "Clean, neat handwriting font with charming personal charisma.",
            weights = listOf(400),
            ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/shadows-into-light@latest/latin-400-normal.ttf"
        )
    )

    /**
     * Searches curated fonts + queries Fontsource API dynamically if online.
     */
    suspend fun searchOnlineFonts(
        query: String,
        categoryFilter: String = "all"
    ): List<OnlineFontItem> = withContext(Dispatchers.IO) {
        val q = query.trim().lowercase()

        // 1. Filter curated fonts first
        val curatedFiltered = curatedFonts.filter { item ->
            val matchesCategory = categoryFilter == "all" || item.category == categoryFilter
            val matchesQuery = q.isEmpty() ||
                    item.name.lowercase().contains(q) ||
                    item.id.lowercase().contains(q) ||
                    item.description.lowercase().contains(q)
            matchesCategory && matchesQuery
        }.toMutableList()

        if (q.length < 2) {
            return@withContext curatedFiltered
        }

        // 2. Query Fontsource API dynamically to find more fonts matching search
        try {
            val encodedQuery = URLEncoder.encode(q, "UTF-8")
            val apiUrl = "https://api.fontsource.org/v1/fonts"
            val url = URL(apiUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Scribe-Android/1.0")
            }

            if (conn.responseCode == 200) {
                val json = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(json)

                val existingIds = curatedFiltered.map { it.id }.toSet()
                var addedCount = 0

                for (i in 0 until array.length()) {
                    if (addedCount >= 20) break // limit extra results for fast UI
                    val obj = array.getJSONObject(i)
                    val id = obj.optString("id", "")
                    val family = obj.optString("family", "")
                    val cat = obj.optString("category", "sans-serif")
                        .replace("-serif", "")
                        .replace("sans-serif", "sans")
                        .replace("monospace", "mono")
                    val isVar = obj.optBoolean("variable", false)

                    if (id.isNotBlank() && !existingIds.contains(id)) {
                        if (family.lowercase().contains(q) || id.contains(q)) {
                            val mappedCat = when {
                                cat.contains("serif") -> "serif"
                                cat.contains("mono") -> "mono"
                                cat.contains("display") -> "display"
                                cat.contains("handwriting") -> "handwriting"
                                else -> "sans"
                            }

                            if (categoryFilter == "all" || mappedCat == categoryFilter) {
                                val ttfUrl = "https://cdn.jsdelivr.net/fontsource/fonts/$id@latest/latin-400-normal.ttf"
                                val dynamicWeights = mutableListOf<Int>()
                                val wArr = obj.optJSONArray("weights")
                                if (wArr != null) {
                                    for (wIdx in 0 until wArr.length()) {
                                        dynamicWeights.add(wArr.getInt(wIdx))
                                    }
                                }
                                if (dynamicWeights.isEmpty()) {
                                    dynamicWeights.add(400)
                                }
                                curatedFiltered.add(
                                    OnlineFontItem(
                                        id = id,
                                        name = family,
                                        category = mappedCat,
                                        description = "Open source font from Fontsource library.",
                                        isVariable = isVar,
                                        weights = dynamicWeights,
                                        ttfUrl = ttfUrl
                                    )
                                )
                                addedCount++
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Dynamic API search skipped (offline or timeout): ${e.message}")
        }

        curatedFiltered
    }

    /**
     * Downloads font TTF directly into Scribe storage.
     */
    suspend fun downloadFont(
        context: Context,
        item: OnlineFontItem,
        onProgress: (Float) -> Unit = {}
    ): Result<ScribeFont> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            onProgress(0.1f)
            val url = URL(item.ttfUrl)
            connection = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 8000
                readTimeout = 12000
                setRequestProperty("User-Agent", "Scribe-Android/1.0")
            }

            val responseCode = connection.responseCode
            if (responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP Error $responseCode while downloading font"))
            }

            val totalBytes = connection.contentLength.coerceAtLeast(1)
            val inputStream = connection.inputStream
            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var bytesReadSoFar = 0

            var n: Int
            while (inputStream.read(buffer).also { n = it } != -1) {
                outputStream.write(buffer, 0, n)
                bytesReadSoFar += n
                if (totalBytes > 0) {
                    val progress = (bytesReadSoFar.toFloat() / totalBytes.toFloat()).coerceIn(0.1f, 0.95f)
                    onProgress(progress)
                }
            }

            val fontBytes = outputStream.toByteArray()

            // Check if font has variable axes (fvar table)
            val fontInfo = ScribeFontManager.parseFontMetadata(fontBytes)
            val isVariable = fontInfo?.isVariable ?: false
            val weightFiles = mutableMapOf<Int, String>()

            // If not a variable font, fetch additional static weight files from Fontsource
            if (!isVariable && item.weights.size > 1) {
                val otherWeights = item.weights.filter { it != 400 }
                val weightCount = otherWeights.size
                otherWeights.forEachIndexed { index, w ->
                    try {
                        val weightUrlStr = if (item.ttfUrl.contains("latin-400-normal.ttf")) {
                            item.ttfUrl.replace("latin-400-normal.ttf", "latin-$w-normal.ttf")
                        } else {
                            "https://cdn.jsdelivr.net/fontsource/fonts/${item.id}@latest/latin-$w-normal.ttf"
                        }
                        val wConn = (URL(weightUrlStr).openConnection() as HttpURLConnection).apply {
                            instanceFollowRedirects = true
                            connectTimeout = 6000
                            readTimeout = 8000
                            setRequestProperty("User-Agent", "Scribe-Android/1.0")
                        }
                        if (wConn.responseCode in 200..299) {
                            val wBytes = wConn.inputStream.use { it.readBytes() }
                            if (wBytes.size >= 12) {
                                val wFile = File(ScribeFontManager.getFontsDir(context), "${item.id}_$w.ttf")
                                FileOutputStream(wFile).use { it.write(wBytes) }
                                weightFiles[w] = wFile.absolutePath
                            }
                        }
                        wConn.disconnect()
                    } catch (e: Exception) {
                        Log.d(TAG, "Optional weight $w download skipped for ${item.id}: ${e.message}")
                    }
                    val weightProgress = 0.5f + 0.45f * ((index + 1).toFloat() / weightCount.toFloat())
                    onProgress(weightProgress)
                }
            }

            onProgress(1.0f)
            ScribeFontManager.saveDownloadedFont(
                context = context,
                id = item.id,
                name = item.name,
                category = item.category,
                bytes = fontBytes,
                supportedWeights = item.weights,
                weightFiles = weightFiles,
                license = item.license
            )
        } catch (e: Exception) {
            Log.e(TAG, "Download failed for font ${item.name}", e)
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
