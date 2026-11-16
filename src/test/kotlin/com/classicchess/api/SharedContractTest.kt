package com.classicchess.api

import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class SharedContractTest {
    private val fixtures = Json.parseToJsonElement(File(System.getProperty("classicchess.fixtures")).readText()).jsonObject
    private lateinit var server: MockWebServer
    private lateinit var api: ClassicChessClient
    @BeforeTest fun start() {
        server = MockWebServer().apply { start() }
        api = ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString()))
    }
    @AfterTest fun stop() { api.close(); server.shutdown() }
    private fun cases(key: String) = fixtures.getValue(key).jsonArray.map { it.jsonObject }

    @Test fun gymStatesAndNullableBotMetadataWithoutAutomaticPolling() = runBlocking {
        val gym = fixtures.getValue("gym").jsonObject
        val decoder = Json { ignoreUnknownKeys = true }
        ApplicationClient(ClientOptions(baseUrl = server.url("/").toString())).use { application ->
            server.enqueue(MockResponse().setBody(gym.getValue("home").toString()))
            val home = decoder.decodeFromJsonElement(GymHome.serializer(), application.accountGym("fixture").data)
            assertEquals(0.6, home.bots[0].versions!![0].moveMatch)
            assertNull(home.bots[0].ratingInterval)
            val strength = home.bots[1]
            assertEquals(1450.0, strength.ratingInterval!!.low)
            assertEquals(1550.0, strength.ratingInterval!!.high)
            val version = strength.versions!![0]
            assertEquals(listOf(null, null, null, null),
                listOf(version.moveMatch, version.masters, version.studyGames, version.ownGames))
            server.takeRequest(2, TimeUnit.SECONDS)
            for (case in gym.getValue("game_replies").jsonArray.map { it.jsonObject }) {
                val status = case.getValue("status").jsonPrimitive.int
                val method = case.getValue("method").jsonPrimitive.content
                val body = case.getValue("body").jsonObject
                val retry = case.getValue("retry_after").jsonPrimitive.contentOrNull
                val response = MockResponse().setResponseCode(status).setBody(body.toString())
                if (retry != null) response.setHeader("Retry-After", retry)
                server.enqueue(response)
                val before = server.requestCount
                val reply = if (method == "GET") application.accountGymGame(7, "fixture")
                    else application.accountGymBotMove(7, 1, "fixture")
                assertEquals(status, reply.status)
                assertEquals(status < 300, reply.ok)
                assertEquals(retry, reply.retryAfter)
                assertEquals(body, reply.data)
                if (reply.ok) {
                    val state = decoder.decodeFromJsonElement(GymGameState.serializer(), reply.data)
                    assertEquals(body.getValue("thinking").jsonPrimitive.boolean, state.thinking)
                    assertEquals(body.getValue("thinking_error").jsonPrimitive.contentOrNull, state.thinkingError)
                    assertEquals(body.getValue("moves").jsonArray.map { it.jsonPrimitive.content }, state.moves)
                }
                assertEquals(before + 1, server.requestCount)
                val sent = server.takeRequest(2, TimeUnit.SECONDS)!!
                assertEquals(method, sent.method)
                assertEquals("/api/v1/account/gym/games/7/" + (if (method == "GET") "" else "bot-move/"), sent.path)
                assertEquals("Bearer fixture", sent.getHeader("Authorization"))
                val requestBody = sent.body.readUtf8()
                if (method == "GET") assertEquals("", requestBody)
                else assertEquals(buildJsonObject { put("ply", 1) }, Json.parseToJsonElement(requestBody))
            }
        }
    }

    @Test fun growingPaginationSequencesReachEveryPage() = runBlocking {
        for (case in cases("page_sequences")) {
            val before = server.requestCount
            val pages = case.getValue("pages").jsonArray
            pages.forEach { server.enqueue(MockResponse().setBody(it.toString())) }
            val tokens = api.iterateMasterGames(MasterGameFilters("Tal", pageSize = 1)).toList().map { it.token }
            assertEquals(case.getValue("expected_tokens").jsonArray.map { it.jsonPrimitive.content }, tokens)
            assertEquals(pages.size, server.requestCount - before)
        }
    }

    @Test fun missingDataAndMalformedSuccessResponsesFailConsistently() = runBlocking {
        for (case in cases("invalid_pages")) {
            server.enqueue(MockResponse().setBody(case.getValue("body").jsonPrimitive.content))
            val error = assertFailsWith<ApiException> { api.iterateMasterGames(MasterGameFilters("Tal")).toList() }
            assertEquals(case.getValue("code").jsonPrimitive.content, error.code)
        }
    }

    @Test fun httpErrorsRetainStatusAndRetryAfter() = runBlocking {
        for (case in cases("http_errors")) {
            val status = case.getValue("status").jsonPrimitive.int
            val retry = case.getValue("retry_after").jsonPrimitive.contentOrNull
            val response = MockResponse().setResponseCode(status).setBody(case.getValue("body").jsonPrimitive.content)
            if (retry != null) response.setHeader("Retry-After", retry)
            server.enqueue(response)
            val error = assertFailsWith<ApiException> { api.masterGames(MasterGameFilters("Tal")) }
            assertEquals(case.getValue("code").jsonPrimitive.content, error.code)
            assertEquals(status, error.status)
            assertEquals(retry, error.retryAfter)
        }
    }

    @Test fun stoppingOrCancellingPreventsTheNextPage() = runBlocking {
        val stop = fixtures.getValue("stop_after_first").jsonObject
        server.enqueue(MockResponse().setBody(cases("page_sequences")[0].getValue("pages").jsonArray[0].toString()))
        val games = api.iterateMasterGames(MasterGameFilters("Tal")).take(stop.getValue("items").jsonPrimitive.int).toList()
        assertEquals(1, games.size)
        assertEquals(stop.getValue("requests").jsonPrimitive.int, server.requestCount)
        server.takeRequest(2, TimeUnit.SECONDS)
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val observed = CompletableDeferred<Throwable>()
        val pending = async(start = CoroutineStart.UNDISPATCHED) {
            try { api.masterGames(MasterGameFilters("Tal")) }
            catch (error: Throwable) { observed.complete(error); throw error }
        }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        pending.cancelAndJoin()
        assertIs<CancellationException>(observed.await())
        assertFailsWith<CancellationException> { pending.await() }
        assertEquals(2, server.requestCount)
    }

    private val usernames get() = fixtures.getValue("imported_game_usernames").jsonObject
    private fun usernameStrings(key: String) = usernames.getValue(key).jsonArray.map { it.jsonPrimitive.content }

    /** The request paths of the three public imported-game reads for one username. */
    private suspend fun importedGamePaths(username: String): List<String?> {
        server.enqueue(MockResponse().setBody(SiteGapFixtures.PUBLIC_IMPORTED_GAME))
        api.publicImportedGame(username, "g1")
        server.enqueue(MockResponse().setBody("1. e4 *"))
        api.publicImportedPgn(username, "g1")
        server.enqueue(MockResponse().setBody("GIF89a").setHeader("Content-Type", "image/gif"))
        ApplicationClient(ClientOptions(baseUrl = server.url("/").toString())).use { it.publicImportedGameGif(username, "g1", "t") }
        return List(3) { server.takeRequest(2, TimeUnit.SECONDS)?.path }
    }

    @Test fun importedGameUsernamesInAnyScriptReachTheirRoutes() = runBlocking<Unit> {
        for (case in usernames.getValue("accepted").jsonArray.map { it.jsonObject }) {
            val game = "/api/v1/public/imported-games/" + case.getValue("path_segment").jsonPrimitive.content + "/g1/"
            assertEquals(listOf(game, game + "pgn/", game + "gif/"), importedGamePaths(case.getValue("username").jsonPrimitive.content))
        }
        val longest = usernames.getValue("max_length").jsonPrimitive.int
        for (character in usernameStrings("length_characters")) {
            assertTrue(importedGamePaths(character.repeat(longest)).all { it != null }, character)
        }
    }

    @Test fun otherNamesFailBeforeAnyRequest() = runBlocking<Unit> {
        val longest = usernames.getValue("max_length").jsonPrimitive.int
        val names = usernames.getValue("rejected").jsonArray.map { it.jsonObject.getValue("username").jsonPrimitive.content } +
            usernameStrings("length_characters").map { it.repeat(longest + 1) }
        ApplicationClient(ClientOptions(baseUrl = server.url("/").toString())).use { application ->
            for (username in names) {
                for (call in listOf<suspend () -> Unit>({ api.publicImportedGame(username, "g1") },
                    { api.publicImportedPgn(username, "g1") }, { application.publicImportedGameGif(username, "g1", "t") })) {
                    assertEquals("invalid_identifier", assertFailsWith<ApiException>(username) { call() }.code)
                }
            }
        }
        assertEquals(0, server.requestCount)
    }
}
