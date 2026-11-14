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

    @Test fun deviceSessionCallsSendTheDeviceTokenOnlyWhereTheyNeedOne() = runBlocking {
        val sessions = "/api/v1/account/mobile/sessions/"
        val settings = "/api/v1/account/mobile/settings/"
        val cases = listOf<Pair<suspend () -> ApplicationResponse, List<String?>>>(
            suspend { client.mobileHome() } to listOf("GET", "/api/v1/mobile/home/", null, null),
            suspend { client.mobileAndroidRelease() } to listOf("GET", "/api/v1/mobile/android/release/", null, null),
            suspend { client.mobileSignIn("ann", "test-only-password", "123456", "Pixel 9") } to listOf("POST",
                "/api/v1/account/mobile/login/", """{"username": "ann", "password": "test-only-password",
                    "two_factor_code": "123456", "device_name": "Pixel 9"}""", null),
            suspend { client.mobileSignOut("test-device") } to listOf("POST", "/api/v1/account/mobile/logout/", "", "test-device"),
            suspend { client.mobileSessions("test-device") } to listOf("GET", sessions, null, "test-device"),
            suspend { client.mobileRevokeSession(3, "test-device") } to listOf("POST", "${sessions}3/revoke/", "", "test-device"),
            suspend { client.mobileRevokeAllSessions("test-device") } to listOf("POST", "${sessions}revoke-all/", "", "test-device"),
            suspend { client.mobileSettings("test-device") } to listOf("GET", settings, null, "test-device"),
            suspend { client.mobileUpdateSettings(MobileSettingsChanges(soundSet = "classic", speechLevel = 1.5,
                castCcEmails = listOf("coach@example.com")), "test-device") } to listOf("POST", settings,
                """{"sound_set": "classic", "speech_level": 1.5, "cast_cc_emails": ["coach@example.com"]}""", "test-device"),
        )
        for ((call, expected) in cases) {
            val (method, path, body, token) = expected
            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(call().ok)
            val request = server.takeRequest()
            assertEquals(method, request.method)
            assertEquals(path, request.path)
            assertEquals(token?.let { "Bearer $it" }, request.getHeader("Authorization"))
            val sentBody = request.body.readUtf8()
            if (body.isNullOrEmpty()) assertEquals("", sentBody)
            else assertEquals(Json.parseToJsonElement(body), Json.parseToJsonElement(sentBody))
        }
        val invalid = listOf<suspend () -> Unit>(
            { client.mobileSignIn(" ", "test-only-password") }, { client.mobileSignIn("ann", "") },
            { client.mobileSignIn("ann", "test-only-password", deviceName = "x".repeat(81)) },
            { client.mobileRevokeSession(0, "test-device") }, { client.mobileUpdateSettings(MobileSettingsChanges(), "test-device") },
        )
        for (call in invalid) assertFailsWith<ApiException> { call() }
        assertEquals(cases.size, server.requestCount)
    }

    @Test fun passwordsAreNeverSentOverPlainHttp() = runBlocking {
        ApplicationClient(ClientOptions(baseUrl = "http://plain-http.example.invalid", timeoutMillis = 100)).use { insecure ->
            assertEquals("unsafe_credentials", assertFailsWith<ApiException> {
                insecure.mobileSignIn("ann", "test-only-password")
            }.code)
        }
        assertEquals(0, server.requestCount)
    }

    @Test fun preferenceChangesPatchOnlyTheKeysTheyName() = runBlocking {
        repeat(2) { server.enqueue(MockResponse().setBody("{}")) }
        assertTrue(client.accountPreferences("test-device").ok)
        client.accountUpdatePreferences(PreferenceChanges(boardTheme = "green", engineDepth = 22, speechLevel = 1.5,
            viewerKeybindings = mapOf("flip" to listOf("f", "Shift+F"))), "test-device")
        val read = server.takeRequest()
        val patch = server.takeRequest()
        assertEquals("GET" to "/api/v1/account/preferences/", read.method to read.path)
        assertEquals("PATCH" to "/api/v1/account/preferences/", patch.method to patch.path)
        assertEquals(Json.parseToJsonElement("""{"board_theme": "green", "engine_depth": 22, "speech_level": 1.5,
            "viewer_keybindings": {"flip": ["f", "Shift+F"]}}"""), Json.parseToJsonElement(patch.body.readUtf8()))
        listOf(read, patch).forEach { assertEquals("Bearer test-device", it.getHeader("Authorization")) }
        assertEquals("invalid_request", assertFailsWith<ApiException> {
            client.accountUpdatePreferences(PreferenceChanges(), "test-device")
        }.code)
        assertEquals(2, server.requestCount)
    }

    @Test fun failedRepliesExposeTheirApiErrorCode() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(401)
            .setBody("""{"error": {"code": "two_factor_required", "message": "Enter a code."}}"""))
        assertEquals("two_factor_required", client.mobileSignIn("ann", "test-only-password").errorCode)
        server.enqueue(MockResponse().setResponseCode(503).setBody("""{"ok": false, "error": "Unavailable."}"""))
        assertNull(client.mobileHome().errorCode)
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
