package com.classicchess.api

import java.io.Closeable
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer

/** HTTP failures retain the API code and Retry-After header. Coroutine cancellation stays cancellation. */
class ApiException(
    message: String,
    val code: String,
    val status: Int? = null,
    val retryAfter: String? = null,
    cause: Throwable? = null,
) : IOException(message, cause)

data class ClientOptions(
    val baseUrl: String = "https://classicchess.com",
    val userAgent: String = "classicchess-kotlin-client/0.1.0",
    val timeoutMillis: Long = 30_000,
    val maxResponseBytes: Long = 32L * 1024 * 1024,
    /** Android debug previews only: allow bearer requests to the emulator's host-loopback alias 10.0.2.2. */
    val allowInsecureEmulator: Boolean = false,
)

internal fun segment(value: String): String {
    if (!Regex("[A-Za-z0-9_-]{1,255}").matches(value)) {
        throw ApiException("Use an exact slug or token returned by the catalog.", "invalid_identifier")
    }
    return value
}

internal fun gameToken(value: String): String {
    val parts = value.split('/')
    if (parts.size !in 1..2) throw ApiException("Use a game token, not a URL.", "invalid_identifier")
    return parts.joinToString("/") { segment(it) }
}

internal class Transport(private val options: ClientOptions) : Closeable {
    private val masterReadPath = Regex("/api/v1/games/(?:g[0-9a-z]{1,66}-[0-9a-f]{12}/(?:pgn/)?)?")
    val base: HttpUrl = options.baseUrl.toHttpUrlOrNull()
        ?: throw ApiException("Invalid API origin.", "invalid_options")
    private val client: OkHttpClient
    @Volatile private var closed = false

    init {
        if (base.encodedPath != "/" || base.username.isNotEmpty() || base.password.isNotEmpty()
            || base.query != null || base.fragment != null || options.timeoutMillis <= 0
            || options.timeoutMillis > Int.MAX_VALUE || options.maxResponseBytes !in 1..Int.MAX_VALUE.toLong()) {
            throw ApiException("Use an HTTP(S) origin and positive bounded timeout/response limits.", "invalid_options")
        }
        client = OkHttpClient.Builder()
            .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .callTimeout(options.timeoutMillis, TimeUnit.MILLISECONDS)
            .connectTimeout(options.timeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(options.timeoutMillis, TimeUnit.MILLISECONDS)
            .build()
    }

    fun url(path: String, query: Map<String, Any?> = emptyMap()): HttpUrl {
        val builder = safeUrl(path).newBuilder()
        query.forEach { (key, value) ->
            if (value != null && value != "") builder.setQueryParameter(key, value.toString())
        }
        return builder.build()
    }

    fun safeUrl(path: String, relativeTo: HttpUrl = base): HttpUrl {
        val url = relativeTo.resolve(path) ?: throw ApiException("Invalid API link.", "unsafe_url")
        if (url.scheme != base.scheme || url.host != base.host || url.port != base.port
            || url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null
            || !(url.encodedPath == "/api/v1/" || url.encodedPath.startsWith("/api/v1/public/")
                || url.encodedPath.startsWith("/api/v1/annotated/")
                || url.encodedPath in listOf("/api/v1/players/", "/api/v1/stats/", "/api/v1/games/export/",
                    "/api/v1/opening-explorer/", "/api/v1/opening-explorer/sources/")
                || masterReadPath.matches(url.encodedPath))) {
            throw ApiException("Refused a link outside the configured public API.", "unsafe_url")
        }
        return url
    }

    suspend fun read(url: HttpUrl, accept: String): String {
        safeUrl(url.toString())
        val response = exchange(Request.Builder().url(url).header("Accept", accept).get().build())
        if (response.status !in 200..299) throw httpError(response)
        return response.text
    }

    suspend fun exchange(request: Request, timeoutMillis: Long? = null): WireResponse = suspendCancellableCoroutine { continuation ->
        if (closed) {
            continuation.resumeWithException(ApiException("The API client is closed.", "client_closed"))
            return@suspendCancellableCoroutine
        }
        val url = request.url
        if (url.scheme != base.scheme || url.host != base.host || url.port != base.port ||
            url.username.isNotEmpty() || url.password.isNotEmpty() || url.fragment != null) {
            throw ApiException("Refused a link outside the configured API origin.", "unsafe_url")
        }
        if (timeoutMillis != null && timeoutMillis !in 1..600_000) {
            throw ApiException("Use a timeout between 1 ms and ten minutes.", "invalid_options")
        }
        // Derived clients share the dispatcher and connection pool. Long scanner calls also need a longer read deadline.
        val requestClient = if (timeoutMillis == null) client else client.newBuilder()
            .readTimeout(timeoutMillis, TimeUnit.MILLISECONDS).writeTimeout(timeoutMillis, TimeUnit.MILLISECONDS).build()
        val call = requestClient.newCall(request.newBuilder().header("User-Agent", options.userAgent).build())
        if (timeoutMillis != null) call.timeout().timeout(timeoutMillis, TimeUnit.MILLISECONDS)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(networkError(e))
            }
            override fun onResponse(call: Call, response: Response) {
                try {
                    val text = response.use {
                        val body = response.body
                        val buffer = Buffer()
                        var bytes = 0L
                        while (true) {
                            val count = body.source().read(buffer, minOf(8192, options.maxResponseBytes + 1 - bytes))
                            if (count == -1L) break
                            bytes += count
                            if (bytes > options.maxResponseBytes) throw ApiException("Response exceeds maxResponseBytes.", "response_too_large", response.code)
                        }
                        buffer.readUtf8()
                    }
                    if (continuation.isActive) continuation.resume(WireResponse(response.code, text, response.header("Retry-After")))
                } catch (error: Exception) {
                    if (continuation.isActive) continuation.resumeWithException(
                        if (error is ApiException) error else networkError(error)
                    )
                }
            }
        })
    }

    private fun networkError(error: Exception) = ApiException(
        if (error is InterruptedIOException) "API request timed out." else "API request failed.",
        if (error is InterruptedIOException) "timeout" else "network_error", cause = error,
    )

    private fun httpError(response: WireResponse): ApiException {
        val payload = runCatching { Json.parseToJsonElement(response.text) as? JsonObject }.getOrNull()
        val detail = payload?.get("error") as? JsonObject
        fun field(name: String) = (detail?.get(name) as? JsonPrimitive)?.takeIf { it.isString }?.content
        return ApiException(field("message") ?: "HTTP ${response.status}",
            field("code") ?: if (response.status in 300..399) "unsafe_redirect" else "http_error",
            response.status, response.retryAfter)
    }

    override fun close() {
        closed = true
        client.dispatcher.cancelAll()
        client.dispatcher.executorService.shutdown()
        client.connectionPool.evictAll()
    }
}

internal data class WireResponse(val status: Int, val text: String, val retryAfter: String?)
