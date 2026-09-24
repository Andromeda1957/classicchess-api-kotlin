package com.classicchess.api

import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer

class SiteGapsTest {
    private lateinit var server: MockWebServer
    private lateinit var client: ApplicationClient
    private val notebook = "0f8fad5b-d9cb-469f-a165-70867728950e"
    private val gif = byteArrayOf(71, 73, 70, 56, 57, 97, 0, -1)

    @BeforeTest fun start() {
        server = MockWebServer(); server.start()
        client = ApplicationClient(ClientOptions(baseUrl = server.url("/").toString()))
    }
    @AfterTest fun stop() { client.close(); server.shutdown() }

    private fun file(bytes: ByteArray, type: String, name: String) = MockResponse().setBody(Buffer().write(bytes))
        .setHeader("Content-Type", type).setHeader("Content-Disposition", "attachment; filename=" + '"' + name + '"')

    private fun sent() = server.takeRequest(2, TimeUnit.SECONDS)!!

    @Test fun gifDownloadsSendTheBearerAndReturnExactBytes() = runBlocking {
        val cases = listOf<Pair<suspend () -> ApplicationDownload, String>>(
            suspend { client.masterGameGif("g1a2-0123456789ab", "t") } to "/api/v1/games/g1a2-0123456789ab/gif/",
            suspend { client.publicGameGif("tal-vs-larsen", "t", "black") } to "/api/v1/public/games/tal-vs-larsen/gif/?orientation=black",
            suspend { client.annotatedGameGif("my-system", "game-1", "t") } to "/api/v1/annotated/books/my-system/games/game-1/gif/",
            suspend { client.publicImportedGameGif("ann.lee+1", "g1", "t") } to "/api/v1/public/imported-games/ann.lee+1/g1/gif/",
            suspend { client.accountImportedGameGif("mine", "t") } to "/api/v1/account/imported-games/mine/gif/",
        )
        for ((call, path) in cases) {
            server.enqueue(file(gif, "image/gif", "game.gif"))
            val result = call()
            val request = sent()
            assertEquals(path, request.path)
            assertEquals("Bearer t", request.getHeader("Authorization"))
            assertEquals("image/gif", request.getHeader("Accept"))
            assertTrue(result.ok)
            assertContentEquals(gif, result.bytes)
            assertEquals("image/gif", result.contentType)
            assertEquals("game.gif", result.filename)
        }
    }

    @Test fun failedDownloadReturnsTheJsonErrorWithoutBytes() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "60")
            .setBody("{ \"error\": { \"code\": \"rate_limited\" } }"))
        val result = client.publicGameGif("g1", "t")
        assertFalse(result.ok)
        assertEquals(429, result.status)
        assertEquals("60", result.retryAfter)
        assertEquals(0, result.bytes.size)
        assertEquals(JsonPrimitive("rate_limited"), result.error!!["error"]!!.jsonObject["code"])
    }

    @Test fun notificationAndNotebookCallsUseExactMethodsPathsAndBodies() = runBlocking {
        val change = "{ \"topics\": { \"new_games\": false }, \"sound_enabled\": true }"
        val cases = listOf<Triple<suspend () -> ApplicationResponse, String, String>>(
            Triple(suspend { client.accountNotifications("t", 2, 20) }, "GET", "/api/v1/account/notifications/?page=2&page_size=20"),
            Triple(suspend { client.accountMarkNotificationRead(5, "t") }, "POST", "/api/v1/account/notifications/5/read/"),
            Triple(suspend { client.accountMarkAllNotificationsRead("t") }, "POST", "/api/v1/account/notifications/read-all/"),
            Triple(suspend { client.accountDismissNotification(5, "t") }, "DELETE", "/api/v1/account/notifications/5/"),
            Triple(suspend { client.accountNotificationPreferences("t") }, "GET", "/api/v1/account/notifications/preferences/"),
            Triple(suspend { client.accountUpdateNotificationPreferences("t", mapOf("new_games" to false), true) },
                "PATCH", "/api/v1/account/notifications/preferences/"),
            Triple(suspend { client.accountNotebooks("t") }, "GET", "/api/v1/account/notebooks/"),
            Triple(suspend { client.accountNotebook(notebook.uppercase(), "t") }, "GET", "/api/v1/account/notebooks/$notebook/"),
        )
        for ((call, method, path) in cases) {
            server.enqueue(MockResponse().setBody("{ \"ok\": true }"))
            assertTrue(call().ok)
            val request = sent()
            assertEquals(method, request.method)
            assertEquals(path, request.path)
            assertEquals("Bearer t", request.getHeader("Authorization"))
            if (method == "PATCH") assertEquals(Json.parseToJsonElement(change), Json.parseToJsonElement(request.body.readUtf8()))
        }
    }

    @Test fun notebookExportsDownloadPgnAndOptionallyEncryptedFiles() = runBlocking {
        server.enqueue(file("1. e4 *".toByteArray(), "application/x-chess-pgn", "line.pgn"))
        val chapter = client.accountNotebookChapterPgn(notebook, 9, "t")
        assertEquals("/api/v1/account/notebooks/$notebook/chapters/9/pgn/", sent().path)
        assertEquals("1. e4 *", chapter.bytes.toString(Charsets.UTF_8))
        assertEquals("line.pgn", chapter.filename)
        server.enqueue(file("CCNB".toByteArray(), "application/vnd.classicchess.notebook", "n.ccnb"))
        client.accountNotebookFile(notebook, "t")
        assertEquals("GET", sent().method)
        server.enqueue(file("CCNB".toByteArray(), "application/vnd.classicchess.notebook", "n.ccnb"))
        client.accountNotebookFile(notebook, "t", "secret")
        val request = sent()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/account/notebooks/$notebook/file/", request.path)
        assertEquals(Json.parseToJsonElement("{ \"password\": \"secret\" }"), Json.parseToJsonElement(request.body.readUtf8()))
    }

    @Test fun bearersReachOnlyGifRoutesAndUnsafeArgumentsNeverSend() = runBlocking {
        for (path in listOf("/api/v1/public/games/g1/", "/api/v1/public/games/g1/pgn/", "/api/v1/public/games/g1/gif/extra/",
            "/api/v1/public/imported-games/ann/g1/")) {
            assertEquals("unsafe_credentials", assertFailsWith<ApiException> { client.download(path, "t") }.code)
        }
        val unsafe = listOf<suspend () -> Unit>(
            { client.publicGameGif("../me", "t") }, { client.publicGameGif("g1", "t", "left") },
            { client.publicImportedGameGif("..", "g1", "t") }, { client.publicImportedGameGif("a/b", "g1", "t") },
            { client.accountMarkNotificationRead(0, "t") }, { client.accountUpdateNotificationPreferences("t") },
            { client.accountNotebook("not-a-uuid", "t") }, { client.accountNotebookChapterPgn(notebook, 0, "t") },
            { client.accountNotebookFile(notebook, "t", "") },
        )
        for (call in unsafe) assertFailsWith<ApiException> { call() }
        assertEquals(0, server.requestCount)
    }

    @Test fun publicImportedGamesArePublicReads() = runBlocking<Unit> {
        ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString())).use { archive ->
            server.enqueue(MockResponse().setBody(SiteGapFixtures.PUBLIC_IMPORTED_GAME))
            assertEquals("ann.lee+1", archive.publicImportedGame("ann.lee+1", "g1").username)
            val request = sent()
            assertEquals("/api/v1/public/imported-games/ann.lee+1/g1/", request.path)
            assertNull(request.getHeader("Authorization"))
            server.enqueue(MockResponse().setBody("1. e4 *"))
            assertEquals("1. e4 *", archive.publicImportedPgn("ann", "g1"))
            assertEquals("/api/v1/public/imported-games/ann/g1/pgn/", sent().path)
            assertFailsWith<ApiException> { archive.publicImportedGame("..", "g1") }
            assertFailsWith<ApiException> { archive.publicImportedPgn("ann", "../me") }
        }
    }
}
