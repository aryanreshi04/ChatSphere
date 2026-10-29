package com.jos.myapplication.data

import org.json.JSONObject

enum class SyncState { QUEUED, SENDING, SENT, FAILED, CACHED }

data class Message(
    val id: String, val senderId: String, val text: String, val timestamp: Long,
    val type: String = "text", val latitude: Double? = null, val longitude: Double? = null,
    val imageBase64: String? = null, val state: SyncState = SyncState.QUEUED,
    val mediaPath: String? = null, val mimeType: String? = null, val mediaSize: Long? = null,
    val mediaSha256: String? = null, val localMediaPath: String? = null, val errorMessage: String? = null,
    val mediaName: String? = null, val durationMs: Long? = null
) {
    fun json(): String = JSONObject().apply {
        put("id", id); put("senderId", senderId); put("text", text); put("timestamp", timestamp); put("type", type)
        latitude?.let { put("latitude", it) }; longitude?.let { put("longitude", it) }
        imageBase64?.let { put("imageBase64", it) }; put("state", state.name)
        mediaPath?.let { put("mediaPath",it) }; mimeType?.let { put("mimeType",it) };mediaSize?.let { put("mediaSize",it) }
        mediaSha256?.let { put("mediaSha256",it) }; localMediaPath?.let { put("localMediaPath",it) };errorMessage?.let { put("errorMessage",it) }
        mediaName?.let { put("mediaName",it) }; durationMs?.let { put("durationMs",it) }
    }.toString()
    companion object {
        fun fromJson(json: String): Message = JSONObject(json).let {
            Message(it.getString("id"), it.getString("senderId"), it.optString("text"), it.getLong("timestamp"),
                it.optString("type", "text"), if (it.has("latitude")) it.getDouble("latitude") else null,
                if (it.has("longitude")) it.getDouble("longitude") else null,
                if (it.has("imageBase64")) it.getString("imageBase64") else null,
                SyncState.valueOf(it.optString("state", "CACHED")),
                if(it.has("mediaPath"))it.getString("mediaPath")else null,if(it.has("mimeType"))it.getString("mimeType")else null,
                if(it.has("mediaSize"))it.getLong("mediaSize")else null,if(it.has("mediaSha256"))it.getString("mediaSha256")else null,
                if(it.has("localMediaPath"))it.getString("localMediaPath")else null,if(it.has("errorMessage"))it.getString("errorMessage")else null,
                if(it.has("mediaName"))it.getString("mediaName")else null,if(it.has("durationMs"))it.getLong("durationMs")else null)
        }
    }
}

object MessagePolicy {
    const val MAX_TEXT = 4000
    // Compatibility for existing Firestore base64 photos only. New uploads use MediaPolicy.
    const val MAX_IMAGE_BYTES = 120 * 1024
    fun validLocation(latitude: Double, longitude: Double) =
        latitude.isFinite() && longitude.isFinite() && latitude in -90.0..90.0 && longitude in -180.0..180.0
    fun conversationId(first: String, second: String): String {
        require(first.isNotBlank() && second.isNotBlank() && first != second && '/' !in first && '/' !in second)
        return listOf(first, second).sorted().joinToString("_")
    }
}
