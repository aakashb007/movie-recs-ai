package com.reelwise.app.data.sync

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/** Google rejected the access token (expired or revoked). */
class DriveAuthException : IOException("Google Drive access was revoked or expired")

/**
 * The one backup file in the person's Drive "appDataFolder": a hidden, per-app folder that only
 * Reelwise can see (drive.appdata scope). Plain REST over OkHttp; no Drive SDK.
 */
class DriveClient(
    private val http: OkHttpClient,
    private val json: Json,
    private val baseUrl: String = "https://www.googleapis.com",
) {
    suspend fun find(token: String): String? {
        val url = "$baseUrl/drive/v3/files?spaces=appDataFolder&fields=files(id)&orderBy=modifiedTime%20desc" +
            "&q=" + java.net.URLEncoder.encode("name='$FILE_NAME' and trashed=false", "UTF-8")
        val body = call(Request.Builder().url(url).auth(token).build())
        val files = json.parseToJsonElement(body).jsonObject["files"]?.jsonArray.orEmpty()
        return files.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.content
    }

    suspend fun download(token: String, id: String): String =
        call(Request.Builder().url("$baseUrl/drive/v3/files/$id?alt=media").auth(token).build())

    /** Creates the file when id is null, else replaces its contents; returns the file id. */
    suspend fun upload(token: String, id: String?, content: String): String {
        val jsonType = "application/json; charset=UTF-8".toMediaType()
        val request = if (id == null) {
            val meta = """{"name":"$FILE_NAME","parents":["appDataFolder"],"mimeType":"application/json"}"""
            val multipart = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(meta.toRequestBody(jsonType))
                .addPart(content.toRequestBody(jsonType))
                .build()
            Request.Builder().url("$baseUrl/upload/drive/v3/files?uploadType=multipart&fields=id").auth(token).post(multipart).build()
        } else {
            Request.Builder().url("$baseUrl/upload/drive/v3/files/$id?uploadType=media&fields=id").auth(token)
                .patch(content.toRequestBody(jsonType)).build()
        }
        val body = call(request)
        return json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.content ?: id ?: throw IOException("Drive gave no file id")
    }

    private fun Request.Builder.auth(token: String) = header("Authorization", "Bearer $token")

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { resp: Response ->
            when {
                resp.code == 401 || resp.code == 403 -> throw DriveAuthException()
                !resp.isSuccessful -> throw IOException("Google Drive ${resp.code}")
            }
            resp.body?.string().orEmpty()
        }
    }

    companion object {
        const val FILE_NAME = "reelwise-backup.json"
    }
}
