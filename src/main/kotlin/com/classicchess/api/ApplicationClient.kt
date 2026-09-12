package com.classicchess.api

import java.io.Closeable
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

data class ApplicationResponse(val status: Int, val data: JsonObject, val retryAfter: String?) {
    val ok: Boolean get() = status in 200..299
}

/** Reusable application API transport. Bearers are explicit per request, never stored or sent on public reads.
 * HTTP errors retain their entire JSON payload for conflicts and scanner saved-failure metadata.
 * Network/invalid-response errors throw ApiException; cancellation remains coroutine cancellation.
 */
class ApplicationClient(private val options: ClientOptions = ClientOptions()) : Closeable {
    private val transport = Transport(options)

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

    private suspend fun execute(path: String, method: String, body: RequestBody?, token: String?, timeoutMillis: Long?): ApplicationResponse {
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
            if (!PRIVATE_PATH.containsMatchIn(pathname) ||
                url.scheme != "https" && url.host !in listOf("localhost", "127.0.0.1", "::1") &&
                !(options.allowInsecureEmulator && url.host == "10.0.2.2")) {
                throw ApiException("Bearer credentials require a private API over HTTPS or loopback development.", "unsafe_credentials")
            }
        }
        if (method !in listOf("GET", "POST", "PUT", "PATCH", "DELETE")) throw ApiException("Invalid API method.", "invalid_method")
        if (method == "GET" && body != null) throw ApiException("GET cannot carry a body.", "invalid_request")
        val request = Request.Builder().url(url).header("Accept", "application/json")
        if (token != null) request.header("Authorization", "Bearer $token")
        request.method(method, body ?: if (method in listOf("POST", "PUT", "PATCH")) ByteArray(0).toRequestBody() else null)
        val response = transport.exchange(request.build(), timeoutMillis)
        val data = withContext(Dispatchers.Default) {
            try {
                Json.parseToJsonElement(response.text.ifBlank { "{}" }) as? JsonObject
                    ?: throw IllegalArgumentException("Expected a JSON object")
            } catch (error: Exception) {
                throw ApiException("Response was not a JSON object.", "invalid_response", response.status, response.retryAfter, error)
            }
        }
        return ApplicationResponse(response.status, data, response.retryAfter)
    }

    override fun close() = transport.close()

    companion object {
        private const val MAX_JSON_BYTES = 10 * 1024 * 1024
        private val PRIVATE_PATH = Regex("^/(?:api/v1/(?:account/|desktop/|mobile/(?:notebooks/|position-scan/))|cast/api/mobile/)")
    }
}
