package com.jos.myapplication.media

object MediaPolicy {
    const val DEFAULT_MAX_BYTES = 50_000_000L
    const val FREE_MAX_BYTES = 50_000_000L
    fun limit(value: Long): Long {
        require(value in 1..FREE_MAX_BYTES) { "Media limit must be between 1 and 50,000,000 bytes" }
        return value
    }
    fun validateSize(size: Long, limit: Long) {
        require(size in 1..MediaPolicy.limit(limit)) { "Media file exceeds the configured file-size limit. Choose a smaller file." }
    }
    fun prefix(input: java.io.InputStream, limit: Int): ByteArray {
        val out=java.io.ByteArrayOutputStream();val chunk=ByteArray(minOf(limit,8192))
        while(out.size()<limit){val n=input.read(chunk,0,minOf(chunk.size,limit-out.size()));if(n<0)break;out.write(chunk,0,n)}
        return out.toByteArray()
    }
    val attachmentMimes = setOf("video/mp4", "audio/mp4", "audio/mpeg", "audio/wav")
    fun type(mime: String): String = when {
        mime.startsWith("image/") -> "image"
        mime == "video/mp4" -> "video"
        mime in attachmentMimes -> "audio"
        else -> error("Unsupported media type")
    }
    fun extension(mime: String): String = when(mime) {
        "video/mp4" -> "mp4"; "audio/mp4" -> "m4a"; "audio/mpeg" -> "mp3"; "audio/wav" -> "wav"
        "image/png" -> "png"; "image/webp" -> "webp"; "image/heic", "image/heif" -> "heic"; else -> "jpg"
    }
    fun filename(value: String?, fallback: String): String = value.orEmpty()
        .replace(Regex("[\\p{Cntrl}/\\\\]"), "_").trim().take(120).ifBlank { fallback }
    fun attachmentMime(header: ByteArray, expectedType: String): String {
        fun ascii(start: Int, count: Int) = String(header, start, count, Charsets.US_ASCII)
        val result = when {
            header.size >= 12 && ascii(0,4) == "RIFF" && ascii(8,4) == "WAVE" -> "audio/wav"
            header.size >= 3 && ascii(0,3) == "ID3" -> "audio/mpeg"
            header.size >= 4 && header[0] == 0xff.toByte() && (header[1].toInt() and 0xe6) == 0xe2 -> "audio/mpeg"
            header.size >= 16 && ascii(4,4) == "ftyp" -> {
                val brand = ascii(8, minOf(64,header.size)-8)
                require(!Regex("hei[cx]|hev[cx]|mif1|msf1|avif").containsMatchIn(brand)) { "Select a playable video or audio file" }
                if (expectedType == "video") "video/mp4" else "audio/mp4"
            }
            else -> error("Choose MP4 video, or MP3, M4A or WAV audio. File extensions are not trusted.")
        }
        require(type(result) == expectedType) { "Selected file does not match the attachment type" }
        return result
    }
    fun mime(header: ByteArray): String {
        fun ascii(start: Int, length: Int) = String(header, start, length, Charsets.US_ASCII)
        if (header.size >= 3 && header[0] == 0xff.toByte() && header[1] == 0xd8.toByte() && header[2] == 0xff.toByte()) return "image/jpeg"
        if (header.size >= 8 && header.take(8).toByteArray().contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) return "image/png"
        if (header.size >= 12 && ascii(0,4) == "RIFF" && ascii(8,4) == "WEBP") return "image/webp"
        if (header.size >= 16 && ascii(4,4) == "ftyp") {
            val brands = ascii(8, minOf(header.size,64)-8)
            if (Regex("hei[cx]|hev[cx]").containsMatchIn(brands)) return "image/heic"
            if (Regex("mif1|msf1").containsMatchIn(brands)) return "image/heif"
        }
        error("Choose a real JPEG, PNG, WebP or HEIF image. Filename extensions are not trusted.")
    }
}
