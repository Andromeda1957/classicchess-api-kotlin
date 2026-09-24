package com.classicchess.api

import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class ApplicationClientTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApplicationClient
    @BeforeTest fun start() {
        server = MockWebServer(); server.start()
        client = ApplicationClient(ClientOptions(baseUrl = server.url("/").toString()))
    }
    @AfterTest fun stop() { client.close(); server.shutdown() }

    @Test fun typedEventCatalogAndCuratedGameOrderPreserveTheContract() = runBlocking {
        ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString())).use { archive ->
            for (series in listOf("candidates", "matches", "misc")) {
                server.enqueue(MockResponse().setBody("""{"query":"","series_slug":"$series","events":[]}"""))
                assertEquals(series, archive.eventSeries(series = series).seriesSlug)
                val request = server.takeRequest()!!
                assertEquals("/api/v1/public/event-series/", request.requestUrl!!.encodedPath)
                assertEquals(series, request.requestUrl!!.queryParameter("series"))
                assertNull(request.getHeader("Authorization"))
            }
            assertEquals("invalid_query", assertFailsWith<ApiException> { archive.eventSeries("London", "misc") }.code)
            assertEquals("invalid_query", assertFailsWith<ApiException> { archive.publicGames(PublicGameFilters(sort = "event")) }.code)
        }
    }

    @Test fun jsonReadsAndMutationsRetainPayloadsAndExplicitCredentials() = runBlocking {
        val payload = """{"error":{"code":"conflict","message":"Changed elsewhere."},"version":9}"""
        for (path in listOf("/api/v1/account/me/", "/api/v1/mobile/notebooks/", "/cast/api/mobile/sessions/session/command/")) {
            server.enqueue(MockResponse().setResponseCode(409).setBody(payload).setHeader("Retry-After", "30"))
            val response = client.request(path, "POST", "{}", "test-device")
            assertEquals(409, response.status)
            assertEquals(Json.parseToJsonElement(payload), response.data)
            assertEquals("30", response.retryAfter)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("Bearer test-device", request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
            assertEquals("{}", request.body.readUtf8())
        }
        server.enqueue(MockResponse().setBody("{}"))
        client.request("/api/v1/account/mobile/login/", "POST", "{}")
        assertNull(server.takeRequest().getHeader("Authorization"))
    }

    @Test fun unsafePathsAndPublicCredentialsFailBeforeNetworking() = runBlocking {
        for (path in listOf("https://other.invalid/api/v1/account/me/", "//other.invalid/api/v1/account/me/",
            "/api/v1/../account/", "/api/v1/%2e%2e/account/", "/api/v1/account/me/#fragment", "/static/portrait.webp",
            "/api/v1/account\\me/", "/api/v1/account/me/\n")) {
            assertEquals("unsafe_url", assertFailsWith<ApiException> { client.request(path, token = "test-device") }.code)
        }
        for (path in listOf("/api/v1/public/players/", "/api/v1/games/?q=Tal", "/api/v1/opening-explorer/")) {
            assertEquals("unsafe_credentials", assertFailsWith<ApiException> { client.request(path, token = "test-device") }.code)
        }
        assertEquals("invalid_token", assertFailsWith<ApiException> { client.request("/api/v1/account/me/", token = "bad\r\nheader") }.code)
        for (emulatorPreview in listOf(false, true)) {
            ApplicationClient(ClientOptions(baseUrl = "http://localhost.example.invalid", timeoutMillis = 100,
                allowInsecureEmulator = emulatorPreview)).use { insecure ->
                assertEquals("unsafe_credentials", assertFailsWith<ApiException> {
                    insecure.request("/api/v1/account/me/", token = "test-device")
                }.code)
            }
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun scannerUploadUsesBoundedMultipartAndPreservesSavedFailure() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(422).setBody("""{"error":{"code":"no_board"},"saved_failure":{"id":"failure"}}"""))
        val response = client.scanPosition(byteArrayOf(1, 2, 3), "test-device")
        assertEquals(422, response.status)
        assertNotNull(response.data["saved_failure"])
        val request = server.takeRequest()!!
        assertEquals("/api/v1/mobile/position-scan/", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("multipart/form-data; boundary="))
        assertTrue(request.body.readUtf8().contains("filename=\"diagram.jpg\""))
        assertEquals("invalid_upload", assertFailsWith<ApiException> { client.scanPosition(ByteArray(850_001), "test-device") }.code)
        assertEquals(1, server.requestCount)
    }

    @Test fun staleKeepAliveConnectionsRetryReadsButNeverWrites() = runBlocking {
        // A server that has dropped an idle keep-alive connection closes it as
        // soon as the next request arrives. Reads recover on a fresh connection.
        server.enqueue(MockResponse().setBody("""{"first":true}"""))
        assertEquals(200, client.request("/api/v1/mobile/home/").status)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody("""{"second":true}"""))
        val read = client.request("/api/v1/mobile/home/")
        assertEquals(200, read.status)
        assertEquals("true", read.data["second"].toString())
        assertEquals(3, server.requestCount)
        repeat(3) { assertEquals("GET", server.takeRequest().method) }

        // A write is never replayed: the caller decides, with its own idempotency key.
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AFTER_REQUEST))
        server.enqueue(MockResponse().setBody("""{"replayed":true}"""))
        val failure = assertFailsWith<ApiException> {
            client.request("/api/v1/account/collections/", "POST", "{}", "test-device")
        }
        assertEquals("network_error", failure.code)
        assertEquals(4, server.requestCount)
        assertEquals("POST", server.takeRequest().method)
    }

    @Test fun redirectsBoundsAndCancellationRemainEnforced() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://other.invalid/"))
        assertEquals(302, client.request("/api/v1/account/me/", token = "test-device").status)
        server.takeRequest()
        ApplicationClient(ClientOptions(baseUrl = server.url("/").toString(), maxResponseBytes = 8)).use { bounded ->
            server.enqueue(MockResponse().setBody("123456789"))
            assertEquals("response_too_large", assertFailsWith<ApiException> { bounded.request("/api/v1/mobile/home/") }.code)
        }
        server.takeRequest()
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = launch { client.request("/api/v1/account/me/", token = "test-device") }
        withContext(Dispatchers.IO) { assertNotNull(server.takeRequest(2, TimeUnit.SECONDS)) }
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
    }
}
