package com.primaloptima.scribe.util.image

/**
 * Robust image format and MIME detector using magic byte inspection with filename fallback.
 * Accurately identifies PNG, JPEG, WebP, GIF, and SVG binaries even when vault URIs lack file extensions.
 */
object ImageMimeUtil {

    data class ImageFormatInfo(
        val mimeType: String,
        val extension: String
    )

    /**
     * Inspects binary header (magic bytes) to determine image format.
     * Falls back to inspecting filename/URI suffix if byte array is unavailable.
     */
    fun detect(bytes: ByteArray?, filenameOrUri: String? = null): ImageFormatInfo {
        if (bytes != null && bytes.size >= 3) {
            // 1. PNG: 89 50 4E 47
            if (bytes.size >= 4 &&
                bytes[0] == 0x89.toByte() &&
                bytes[1] == 0x50.toByte() &&
                bytes[2] == 0x4E.toByte() &&
                bytes[3] == 0x47.toByte()
            ) {
                return ImageFormatInfo("image/png", "png")
            }

            // 2. JPEG: FF D8 FF
            if (bytes[0] == 0xFF.toByte() &&
                bytes[1] == 0xD8.toByte() &&
                bytes[2] == 0xFF.toByte()
            ) {
                return ImageFormatInfo("image/jpeg", "jpg")
            }

            // 3. GIF: 47 49 46 38 ("GIF8")
            if (bytes.size >= 4 &&
                bytes[0] == 0x47.toByte() &&
                bytes[1] == 0x49.toByte() &&
                bytes[2] == 0x46.toByte() &&
                bytes[3] == 0x38.toByte()
            ) {
                return ImageFormatInfo("image/gif", "gif")
            }

            // 4. WebP: bytes 0..3 == "RIFF" && bytes 8..11 == "WEBP"
            if (bytes.size >= 12 &&
                bytes[0] == 0x52.toByte() && bytes[1] == 0x49.toByte() && bytes[2] == 0x46.toByte() && bytes[3] == 0x46.toByte() &&
                bytes[8] == 0x57.toByte() && bytes[9] == 0x45.toByte() && bytes[10] == 0x42.toByte() && bytes[11] == 0x50.toByte()
            ) {
                return ImageFormatInfo("image/webp", "webp")
            }

            // 5. SVG: Text check for <svg or <?xml followed by <svg
            val sampleLength = bytes.size.coerceAtMost(256)
            val head = String(bytes, 0, sampleLength, Charsets.UTF_8).trimStart()
            if (head.startsWith("<svg", ignoreCase = true) ||
                (head.startsWith("<?xml", ignoreCase = true) && head.contains("<svg", ignoreCase = true))
            ) {
                return ImageFormatInfo("image/svg+xml", "svg")
            }
        }

        // Fallback to filename/URI extension check
        val lower = filenameOrUri?.lowercase() ?: ""
        return when {
            lower.endsWith(".png") -> ImageFormatInfo("image/png", "png")
            lower.endsWith(".webp") -> ImageFormatInfo("image/webp", "webp")
            lower.endsWith(".gif") -> ImageFormatInfo("image/gif", "gif")
            lower.endsWith(".svg") -> ImageFormatInfo("image/svg+xml", "svg")
            else -> ImageFormatInfo("image/jpeg", "jpg")
        }
    }
}
