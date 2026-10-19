package com.classicchess.api

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/**
 * Clients in one process share a connection pool, so an app with a public and
 * an application client pays one TLS handshake, and an optional disk cache
 * lets public reads revalidate with `If-None-Match` instead of downloading
 * the same body again. Credentialed requests never touch the cache.
 */
class TransportSharingTest {
    private lateinit var server: MockWebServer
    private lateinit var cacheRoot: File
    private val opened = mutableListOf<AutoCloseable>()

    @BeforeTest fun start() {
        server = MockWebServer()
        server.start()
        cacheRoot = Files.createTempDirectory("classicchess-http-cache").toFile()
    }

    @AfterTest fun stop() {
        opened.asReversed().forEach { it.close() }
        server.shutdown()
        cacheRoot.deleteRecursively()
    }

    private fun options(cache: String? = null) = ClientOptions(
        baseUrl = server.url("/").toString(),
        httpCacheDirectory = cache?.let { File(cacheRoot, it) },
    )

    private fun <T : AutoCloseable> open(client: T): T = client.also { opened.add(it) }

    private fun discovery(etag: String? = null, cacheControl: String? = null) = MockResponse()
        .setBody("""{"name":"Classic Chess","version":"v1","authentication":{"public_reads":"None","account_api":"Bearer"},"documentation":{},"clients":{"comparison":"","maintained":[]},"collections":{},"player_resources":{"description":"","detail":"/api/v1/public/players/{player_slug}/","bio":"","notable_games":""},"event_resources":{"description":"","detail":"/api/v1/public/events/{event_slug}/","about":""},"tools":{},"pagination":{"catalogs":"","games":"","default_page_size":50,"max_page_size":100}}""")
        .setHeader("Content-Type", "application/json")
        .apply {
            etag?.let { setHeader("ETag", it) }
            cacheControl?.let { setHeader("Cache-Control", it) }
        }

    @Test fun separateClientsReuseOneConnection() = runBlocking {
        val public = open(ClassicChessClient(options()))
        val application = open(ApplicationClient(options()))
        server.enqueue(discovery())
        server.enqueue(MockResponse().setBody("""{"ok":true}""").setHeader("Content-Type", "application/json"))

        public.discovery()
        assertEquals(200, application.request("/api/v1/mobile/home/").status)

        assertEquals(0, server.takeRequest(2, TimeUnit.SECONDS)!!.sequenceNumber)
        // The second client's request rides the first client's pooled connection.
        assertEquals(1, server.takeRequest(2, TimeUnit.SECONDS)!!.sequenceNumber)
    }

    @Test fun closingOneClientLeavesTheOthersWorking() = runBlocking {
        val first = open(ClassicChessClient(options()))
        val second = open(ClassicChessClient(options()))
        server.enqueue(discovery())
        first.discovery()
        first.close()

        assertEquals("client_closed", assertFailsWith<ApiException> { first.discovery() }.code)
        server.enqueue(discovery())
        assertEquals("v1", second.discovery().version)
        assertEquals(2, server.requestCount)
    }

    @Test fun publicReadsAreServedFreshFromCacheThenRevalidated() = runBlocking {
        val api = open(ClassicChessClient(options(cache = "fresh")))
        server.enqueue(discovery(etag = "\"v1\"", cacheControl = "public, max-age=60"))

        assertEquals("v1", api.discovery().version)
        assertEquals("v1", api.discovery().version)
        assertEquals(1, server.requestCount, "a fresh public response is reused without a request")

        val stale = open(ClassicChessClient(options(cache = "stale")))
        server.enqueue(discovery(etag = "\"v2\"", cacheControl = "public, no-cache"))
        stale.discovery()
        server.enqueue(MockResponse().setResponseCode(304).setHeader("ETag", "\"v2\""))
        assertEquals("v1", stale.discovery().version, "a 304 answers from the stored body")
        server.takeRequest(2, TimeUnit.SECONDS)
        server.takeRequest(2, TimeUnit.SECONDS)
        assertEquals("\"v2\"", server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
    }

    @Test fun credentialedReadsAreNeitherStoredNorServedFromCache() = runBlocking {
        val application = open(ApplicationClient(options(cache = "http")))
        val secret = "device-session-secret-value"
        repeat(2) {
            server.enqueue(MockResponse().setBody("""{"results":[]}""")
                .setHeader("Content-Type", "application/json")
                .setHeader("Cache-Control", "private, max-age=600")
                .setHeader("ETag", "\"inbox\""))
        }

        repeat(2) { assertEquals(200, application.request("/api/v1/account/notifications/", token = secret).status) }

        assertEquals(2, server.requestCount)
        server.takeRequest(2, TimeUnit.SECONDS)
        assertNull(server.takeRequest(2, TimeUnit.SECONDS)!!.getHeader("If-None-Match"))
        val stored = File(cacheRoot, "http").walkTopDown().filter { it.isFile }.map { it.readText() }.joinToString("\n")
        assertFalse(secret in stored, "a bearer token must never be written to the HTTP cache")
        assertFalse("notifications" in stored, "a private response must not be cached")
    }
}
