package com.classicchess.api

import java.io.Closeable
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

data class ApplicationResponse(val status: Int, val data: JsonObject, val retryAfter: String?) {
    val ok: Boolean get() = status in 200..299
}

/** A file reply. [bytes] hold the GIF, PGN or .ccnb file only when [ok]; otherwise [error] holds the JSON reply. */
class ApplicationDownload(
    val status: Int,
    val bytes: ByteArray,
    val contentType: String?,
    val filename: String?,
    val retryAfter: String?,
    val error: JsonObject?,
) {
    val ok: Boolean get() = status in 200..299
}

/** Reusable application API transport. Bearers are explicit per request, never stored or sent on public reads.
 * HTTP errors retain their entire JSON payload for conflicts and scanner saved-failure metadata.
 * Network/invalid-response errors throw ApiException; cancellation remains coroutine cancellation.
 */
class ApplicationClient(private val options: ClientOptions = ClientOptions()) : Closeable {
    private val transport = Transport(options)

    suspend fun accountMe(token: String): ApplicationResponse = request("/api/v1/account/me/", token = token)
    suspend fun accountCollections(token: String): ApplicationResponse = request("/api/v1/account/collections/", token = token)
    suspend fun accountCreateCollection(name: String, token: String): ApplicationResponse =
        request("/api/v1/account/collections/", "POST", buildJsonObject { put("name", name) }.toString(), token)
    suspend fun accountImportPublicPlayerGames(archivePlayer: String, token: String, name: String? = null,
        collectionId: Long? = null, since: Int? = null, until: Int? = null): ApplicationResponse =
        request("/api/v1/account/collections/import/", "POST", buildJsonObject {
            put("source", "public_player_games"); put("archive_player", archivePlayer)
            name?.let { put("name", it) }; collectionId?.let { put("collection_id", it) }
            since?.let { put("since", it) }; until?.let { put("until", it) }
        }.toString(), token)

    /** Add one openable game. Status 201 when added; 200 with changed false when already present. */
    suspend fun accountAddCollectionGame(collectionId: Long, gameSlug: String, token: String): ApplicationResponse {
        if (collectionId < 1) throw ApiException("Use a positive collection ID.", "invalid_request")
        return request("/api/v1/account/collections/$collectionId/items/", "POST",
            buildJsonObject { put("game", segment(gameSlug)) }.toString(), token)
    }
    suspend fun accountStarredPlayers(token: String, page: Int = 1, pageSize: Int = 50): ApplicationResponse =
        request("/api/v1/account/starred/players/?${libraryPage(page, pageSize)}", token = token)
    suspend fun accountStarPlayer(playerSlug: String, token: String): ApplicationResponse =
        request("/api/v1/account/starred/players/${segment(playerSlug)}/", "PUT", token = token)
    suspend fun accountUnstarPlayer(playerSlug: String, token: String): ApplicationResponse =
        request("/api/v1/account/starred/players/${segment(playerSlug)}/", "DELETE", token = token)
    suspend fun accountStarredGames(token: String, page: Int = 1, pageSize: Int = 50): ApplicationResponse =
        request("/api/v1/account/starred/games/?${libraryPage(page, pageSize)}", token = token)
    suspend fun accountStarGame(gameSlug: String, token: String): ApplicationResponse =
        request("/api/v1/account/starred/games/${segment(gameSlug)}/", "PUT", token = token)
    suspend fun accountUnstarGame(gameSlug: String, token: String): ApplicationResponse =
        request("/api/v1/account/starred/games/${segment(gameSlug)}/", "DELETE", token = token)
    suspend fun accountSetImportedGameVisibility(gameSlug: String, visibility: String, token: String): ApplicationResponse {
        if (visibility != "private" && visibility != "public") throw ApiException("visibility must be private or public.", "invalid_request")
        return request("/api/v1/account/imported-games/${segment(gameSlug)}/", "PATCH",
            buildJsonObject { put("visibility", visibility) }.toString(), token)
    }
    /** Permanently delete an owned import, including from every collection and star list. */
    suspend fun accountDeleteImportedGame(gameSlug: String, token: String): ApplicationResponse =
        request("/api/v1/account/imported-games/${segment(gameSlug)}/", "DELETE", token = token)

    // GIF exports need a registered account: any personal token, or a device session.
    suspend fun masterGameGif(game: String, token: String, orientation: String = "white"): ApplicationDownload =
        gif("/api/v1/games/" + segment(game) + "/gif/", token, orientation)
    suspend fun publicGameGif(gameSlug: String, token: String, orientation: String = "white"): ApplicationDownload =
        gif("/api/v1/public/games/" + segment(gameSlug) + "/gif/", token, orientation)
    suspend fun annotatedGameGif(bookSlug: String, gameSlug: String, token: String, orientation: String = "white"): ApplicationDownload =
        gif("/api/v1/annotated/books/" + segment(bookSlug) + "/games/" + segment(gameSlug) + "/gif/", token, orientation)
    suspend fun publicImportedGameGif(username: String, gameSlug: String, token: String,
        orientation: String = "white"): ApplicationDownload =
        gif("/api/v1/public/imported-games/" + usernameSegment(username) + "/" + segment(gameSlug) + "/gif/", token, orientation)
    /** An owned import of either visibility; needs library:read. */
    suspend fun accountImportedGameGif(gameSlug: String, token: String, orientation: String = "white"): ApplicationDownload =
        gif("/api/v1/account/imported-games/" + segment(gameSlug) + "/gif/", token, orientation)
    private suspend fun gif(path: String, token: String, orientation: String): ApplicationDownload =
        download(path + gifQuery(orientation), token, accept = "image/gif")

    suspend fun accountNotifications(token: String, page: Int = 1, pageSize: Int = 50): ApplicationResponse =
        request("/api/v1/account/notifications/?" + libraryPage(page, pageSize), token = token)
    suspend fun accountMarkNotificationRead(notificationId: Long, token: String): ApplicationResponse =
        request("/api/v1/account/notifications/" + positiveId(notificationId, "notification ID") + "/read/", "POST", token = token)
    suspend fun accountMarkAllNotificationsRead(token: String): ApplicationResponse =
        request("/api/v1/account/notifications/read-all/", "POST", token = token)
    suspend fun accountDismissNotification(notificationId: Long, token: String): ApplicationResponse =
        request("/api/v1/account/notifications/" + positiveId(notificationId, "notification ID") + "/", "DELETE", token = token)
    suspend fun accountNotificationPreferences(token: String): ApplicationResponse =
        request("/api/v1/account/notifications/preferences/", token = token)
    /** Change only the named topics and/or the sound setting. */
    suspend fun accountUpdateNotificationPreferences(token: String, topics: Map<String, Boolean>? = null,
        soundEnabled: Boolean? = null): ApplicationResponse {
        if (topics == null && soundEnabled == null) {
            throw ApiException("Provide topics and/or soundEnabled to update.", "invalid_request")
        }
        val body = buildJsonObject {
            topics?.let { values -> put("topics", buildJsonObject { values.forEach { (key, value) -> put(key, value) } }) }
            soundEnabled?.let { put("sound_enabled", it) }
        }
        return request("/api/v1/account/notifications/preferences/", "PATCH", body.toString(), token)
    }

    suspend fun accountNotebooks(token: String): ApplicationResponse = request("/api/v1/account/notebooks/", token = token)
    suspend fun accountNotebook(notebook: String, token: String): ApplicationResponse =
        request("/api/v1/account/notebooks/" + notebookUuid(notebook) + "/", token = token)
    suspend fun accountNotebookChapterPgn(notebook: String, chapterId: Long, token: String): ApplicationDownload =
        download("/api/v1/account/notebooks/" + notebookUuid(notebook) + "/chapters/" + positiveId(chapterId, "chapter ID") + "/pgn/",
            token, accept = "application/x-chess-pgn")
    /** The whole Notebook as .ccnb bytes; a password (members only) encrypts it. */
    suspend fun accountNotebookFile(notebook: String, token: String, password: String? = null): ApplicationDownload {
        val path = "/api/v1/account/notebooks/" + notebookUuid(notebook) + "/file/"
        val accept = "application/vnd.classicchess.notebook"
        if (password == null) return download(path, token, accept = accept)
        if (password.isEmpty()) throw ApiException("Use a non-empty password.", "invalid_request")
        return download(path, token, "POST", buildJsonObject { put("password", password) }.toString(), accept)
    }

    /** Binary notebook requests preserve their original bytes and media type. */
    suspend fun requestBytes(path: String, body: ByteArray, token: String? = null, method: String = "POST",
        contentType: String = "application/octet-stream", timeoutMillis: Long? = null): ApplicationResponse {
        if (body.size > 128 * 1024 * 1024) throw ApiException("API request exceeds 128 MiB.", "request_too_large")
        if (contentType.length > 200 || '\r' in contentType || '\n' in contentType) throw ApiException("Invalid content type.", "invalid_request")
        return execute(path, method, body.toRequestBody(contentType.toMediaType()), token, timeoutMillis)
    }

    suspend fun request(
        path: String,
        method: String = "GET",
        body: String? = null,
        token: String? = null,
        timeoutMillis: Long? = null,
    ): ApplicationResponse {
        if (body != null && (body.length > MAX_JSON_BYTES || body.toByteArray(Charsets.UTF_8).size > MAX_JSON_BYTES)) {
            throw ApiException("API request exceeds 10 MiB.", "request_too_large")
        }
        return execute(path, method, body?.toRequestBody("application/json; charset=utf-8".toMediaType()), token, timeoutMillis)
    }

    /** Upload the app's already resized JPEG. OkHttp owns multipart quoting and boundaries. */
    suspend fun scanPosition(image: ByteArray, token: String): ApplicationResponse {
        if (image.isEmpty() || image.size > 850_000) throw ApiException("Use a JPEG of at most 850000 bytes.", "invalid_upload")
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart("image", "diagram.jpg", image.toRequestBody("image/jpeg".toMediaType())).build()
        return execute("/api/v1/mobile/position-scan/", "POST", body, token, 85_000)
    }

    /** Fetch a file. The bytes are returned only for a 2xx; otherwise [ApplicationDownload.error] holds the JSON reply. */
    suspend fun download(path: String, token: String? = null, method: String = "GET", body: String? = null,
        accept: String = "*/*"): ApplicationDownload {
        val requestBody = body?.toRequestBody("application/json; charset=utf-8".toMediaType())
        val response = exchange(path, method, requestBody, token, null, accept)
        val ok = response.status in 200..299
        return ApplicationDownload(response.status, if (ok) response.bytes else ByteArray(0), response.contentType,
            if (ok) attachmentName(response.contentDisposition) else null, response.retryAfter,
            if (ok) null else decodeObject(response))
    }

    private suspend fun execute(path: String, method: String, body: RequestBody?, token: String?, timeoutMillis: Long?): ApplicationResponse {
        val response = exchange(path, method, body, token, timeoutMillis, "application/json")
        return ApplicationResponse(response.status, decodeObject(response), response.retryAfter)
    }

    private suspend fun decodeObject(response: WireResponse): JsonObject = withContext(Dispatchers.Default) {
        try {
            Json.parseToJsonElement(response.text.ifBlank { "{}" }) as? JsonObject
                ?: throw IllegalArgumentException("Expected a JSON object")
        } catch (error: Exception) {
            throw ApiException("Response was not a JSON object.", "invalid_response", response.status, response.retryAfter, error)
        }
    }

    private suspend fun exchange(path: String, method: String, body: RequestBody?, token: String?,
        timeoutMillis: Long?, accept: String): WireResponse {
        val uri = try { URI(path) } catch (error: Exception) {
            throw ApiException("Use a relative application API path.", "unsafe_url", cause = error)
        }
        val pathname = uri.rawPath.orEmpty()
        if (path.length > 8192 || path.any { it <= ' ' || it == '\u007f' || it == '\\' } ||
            uri.isAbsolute || uri.rawAuthority != null || uri.rawFragment != null || '%' in pathname ||
            uri.normalize().rawPath != pathname ||
            !(pathname.startsWith("/api/v1/") || pathname.startsWith("/cast/api/mobile/"))) {
            throw ApiException("Use a relative application API path.", "unsafe_url")
        }
        val url = transport.base.resolve(path) ?: throw ApiException("Invalid API path.", "unsafe_url")
        if (token != null) {
            if (!Regex("[\\x21-\\x7e]{1,4096}").matches(token)) throw ApiException("Invalid bearer token.", "invalid_token")
            if (!isPrivateApiPath(pathname) ||
                url.scheme != "https" && url.host !in listOf("localhost", "127.0.0.1", "::1") &&
                !(options.allowInsecureEmulator && url.host == "10.0.2.2")) {
                throw ApiException("Bearer credentials require a private API over HTTPS or loopback development.", "unsafe_credentials")
            }
        }
        if (method !in listOf("GET", "POST", "PUT", "PATCH", "DELETE")) throw ApiException("Invalid API method.", "invalid_method")
        if (method == "GET" && body != null) throw ApiException("GET cannot carry a body.", "invalid_request")
        val request = Request.Builder().url(url).header("Accept", accept)
        if (token != null) request.header("Authorization", "Bearer $token")
        request.method(method, body ?: if (method in listOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody() else null)
        return transport.exchange(request.build(), timeoutMillis)
    }

    override fun close() = transport.close()

    private fun libraryPage(page: Int, pageSize: Int): String {
        if (page < 1 || pageSize !in 1..100) throw ApiException("Use a positive page and a page size from 1 to 100.", "invalid_pagination")
        return "page=$page&page_size=$pageSize"
    }

    companion object {
        private const val MAX_JSON_BYTES = 10 * 1024 * 1024
        private val PRIVATE_PATH = Regex("^/(?:api/v1/(?:account/|desktop/|mobile/(?:notebooks/|position-scan/))|cast/api/mobile/)")
        /** GIF exports need a registered account, so bearers may also reach the public GIF routes. */
        private val GIF_PATH = Regex("^/api/v1/(?:games/[^/]+|public/games/[^/]+|public/imported-games/[^/]+/[^/]+" +
            "|annotated/books/[^/]+/games/[^/]+)/gif/" + "$")
        private val NOTEBOOK_UUID = Regex("[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-){3}[0-9a-fA-F]{12}")
        private val ATTACHMENT = Regex("filename=\"([^\"\\\\/\\r\\n]{1,255})\"")

        /** Credential audience for application routers; request() still validates URLs and HTTPS. */
        fun isPrivateApiPath(path: String): Boolean = try {
            val pathname = URI(path).rawPath.orEmpty()
            PRIVATE_PATH.containsMatchIn(pathname) || GIF_PATH.matches(pathname)
        } catch (_: Exception) {
            false
        }

        private fun positiveId(value: Long, label: String): Long {
            if (value < 1) throw ApiException("Use a positive " + label + ".", "invalid_request")
            return value
        }

        private fun notebookUuid(value: String): String {
            if (!NOTEBOOK_UUID.matches(value)) throw ApiException("Use a Notebook UUID from the API.", "invalid_query")
            return value.lowercase()
        }

        private fun gifQuery(orientation: String): String = when (orientation) {
            "white" -> ""
            "black" -> "?orientation=black"
            else -> throw ApiException("orientation must be white or black.", "invalid_query")
        }

        private fun attachmentName(disposition: String?): String? =
            disposition?.let { ATTACHMENT.find(it)?.groupValues?.get(1) }
    }
}
