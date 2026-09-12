package com.classicchess.api

import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class ClientTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ClassicChessClient
    @BeforeTest fun start() {
        server = MockWebServer()
        server.start()
        api = ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString()))
    }
    @AfterTest fun stop() { api.close(); server.shutdown() }
    private fun reply(json: String) { server.enqueue(MockResponse().setBody(json).setHeader("Content-Type", "application/json")) }
    private fun path() = server.takeRequest(2, TimeUnit.SECONDS)!!.path

    @Test fun completeCatalogsAndNames() = runBlocking {
        repeat(2) { reply(players()) }
        assertEquals(3, api.publicPlayers().results.size)
        assertEquals(listOf("Tal", "José Capablanca", "Petrosian"), api.playerNames())
        assertEquals("/api/v1/public/players/", path())
        path()
        val events = """{"source":"public","query":"","count":3,"results":[${(1..3).joinToString { """{"slug":"event-$it","name":"Event $it","year":1900,"game_count":3,"expected_game_count":3,"event_type":{},"series":null,"source":{},"urls":{}}""" }}]}"""
        repeat(2) { reply(events) }
        assertEquals(3, api.publicEvents().results.size)
        assertEquals(listOf("Event 1", "Event 2", "Event 3"), api.eventNames())
        assertEquals("/api/v1/public/events/", path())
        path()
        repeat(2) { reply(books()) }
        assertEquals(3, api.annotatedBooks().results.size)
        assertEquals(listOf("Book 1", "Book 2", "Book 3"), api.bookTitles())
        assertEquals("/api/v1/annotated/books/", path())
        path()
        assertEquals(6, server.requestCount)
    }

    @Test fun profilesNullableBiosAndOrderedNotables() = runBlocking {
        reply("""{"source":"public","player":${player("Mikhail_Tal", "Tal")}}""")
        assertEquals("Mikhail_Tal", api.publicPlayer("Mikhail_Tal").player.slug)
        assertEquals("/api/v1/public/players/Mikhail_Tal/", path())
        reply("""{"source":"public","player":{"slug":"Tal","name":"Tal"},"bio":null,"new_field":1}""")
        assertNull(api.publicPlayerBio("Tal").bio)
        assertEquals("/api/v1/public/players/Tal/bio/", path())
        reply("""{"source":"public","player":{"slug":"Tal","name":"Tal"},"bio":$biography}""")
        assertEquals("A <b>text</b> biography", api.publicPlayerBio("Tal").bio!!.lede)
        path()
        reply(notables())
        val entries = api.publicNotableGames("Tal").results
        assertEquals(listOf("Second game", "First game"), entries.map { it.title })
        assertEquals(listOf("two", "one"), entries.map { it.game.token })
        assertEquals("/api/v1/public/players/Tal/notable-games/", path())
    }

    @Test fun queriesAndPublicPgnPreserveData() = runBlocking {
        reply(players())
        api.publicPlayers("Tal & Keres?")
        assertEquals("Tal & Keres?", server.takeRequest()!!.requestUrl!!.queryParameter("q"))
        reply(page("/api/v1/public/games/", 1, null))
        api.publicGames(PublicGameFilters(query = "B33 & wins", archivePlayer = "Mikhail_Tal", archiveEvent = "event", since = 1950, until = 1960, sort = "desc", page = 2, pageSize = 3))
        val url = server.takeRequest()!!.requestUrl!!
        assertEquals(mapOf("q" to "B33 & wins", "archive_player" to "Mikhail_Tal", "archive_event" to "event", "since" to "1950", "until" to "1960", "sort" to "desc", "page" to "2", "page_size" to "3"), url.queryParameterNames.associateWith { url.queryParameter(it) })
        for (token in listOf("game", "Mikhail_Tal/game")) {
            reply(game(token)); assertEquals(token, api.publicGame(token).token)
            assertEquals("/api/v1/public/games/$token/", path())
            reply(pgn); assertEquals(pgn, api.publicPgn(token))
            assertEquals("/api/v1/public/games/$token/pgn/", path())
        }
        reply(pgn); assertEquals(pgn, api.exportPublicGames(tokens = listOf("one", "Tal/two")))
        assertEquals("one,Tal/two", server.takeRequest()!!.requestUrl!!.queryParameter("tokens"))
    }

    @Test fun annotatedPageDetailAndPgn() = runBlocking {
        reply(page("/api/v1/annotated/books/book/games/", 1, null))
        assertEquals(1, api.annotatedGames("book", PageOptions(pageSize = 3)).results.size)
        assertEquals("/api/v1/annotated/books/book/games/?page_size=3", path())
        reply(annotatedGame("book/game"))
        assertEquals("Public domain", api.annotatedGame("book", "game").annotation.licenseName)
        assertEquals("/api/v1/annotated/books/book/games/game/", path())
        reply(pgn); assertEquals(pgn, api.annotatedPgn("book", "game"))
        assertEquals("/api/v1/annotated/books/book/games/game/pgn/", path())
        reply(pgn); assertEquals(pgn, api.annotatedPgn("book"))
        assertEquals("/api/v1/annotated/books/book/pgn/", path())
    }

    @Test fun bothFlowsFetchAllPagesLazily() = runBlocking {
        for (annotated in listOf(false, true)) {
            val path = if (annotated) "/api/v1/annotated/books/book/games/" else "/api/v1/public/games/"
            for (index in 1..3) reply(page(path, index, if (index < 3) "?page=${index+1}" else null))
            val requests = server.requestCount
            val rows = if (annotated) api.iterateAnnotatedGames("book") else api.iteratePublicGames()
            assertEquals(requests, server.requestCount)
            assertEquals(3, rows.toList().size)
            assertEquals(requests + 3, server.requestCount)
            repeat(3) { assertTrue(path()!!.startsWith(path)) }
        }
    }

    @Test fun iterationLimitsStopWithoutExtraRequests() = runBlocking {
        assertEquals(emptyList(), api.iteratePublicGames(options = IterationOptions(limit = 0)).toList())
        assertEquals(0, server.requestCount)
        reply(page("/api/v1/public/games/", 1, "?page=2"))
        assertEquals(1, api.iteratePublicGames().take(1).toList().size)
        assertEquals(1, server.requestCount)
        reply(page("/api/v1/public/games/", 1, "?page=2"))
        assertEquals(1, api.iteratePublicGames(options = IterationOptions(limit = 1)).toList().size)
        reply(page("/api/v1/public/games/", 1, "?page=2"))
        assertEquals("invalid_pagination", assertFailsWith<ApiException> { api.iteratePublicGames(options = IterationOptions(maxPages = 1)).toList() }.code)
    }

    @Test fun paginationRejectsCyclesForeignUrlsAndResourceChanges() = runBlocking {
        for (next in listOf("https://other.example/api/v1/public/games/", "//other.example/api/v1/public/games/", "/api/v1/account/me/", "/api/v1/public/players/", "/api/v1/public/games/", "http://[")) {
            reply(page("/api/v1/public/games/", 1, next))
            val count = server.requestCount
            val error = assertFailsWith<ApiException> { api.iteratePublicGames().toList() }
            assertTrue(error.code in setOf("invalid_pagination", "unsafe_url"))
            assertEquals(count + 1, server.requestCount)
        }
    }

    @Test fun identifiersFiltersAndOptionsRejectBadInput() = runBlocking {
        for (value in listOf("../x", "%2e%2e", "x?format=txt", "x#y", "https://evil.example", "a\\b", "", "a/b/c", "a\nb")) {
            assertFailsWith<ApiException> { api.publicPlayer(value) }
            assertFailsWith<ApiException> { api.publicPgn(value) }
            assertFailsWith<ApiException> { api.annotatedGame("book", value) }
        }
        for (base in listOf("file:///etc", "https://user:pass@example.org", "https://example.org/path", "https://example.org?token=x")) {
            assertFailsWith<ApiException> { ClassicChessClient(ClientOptions(baseUrl = base)) }
        }
        assertFailsWith<ApiException> { api.publicGames(PublicGameFilters(page = 0)) }
        assertFailsWith<ApiException> { api.publicGames(PublicGameFilters(pageSize = 101)) }
        assertFailsWith<ApiException> { api.publicGames(PublicGameFilters(sort = "sideways")) }
        assertFailsWith<ApiException> { api.exportPublicGames(tokens = emptyList()) }
        assertFailsWith<ApiException> { api.exportPublicGames(tokens = List(301) { "game" }) }
        assertEquals(0, server.requestCount)
    }

    @Test fun structuredErrorsAndProxyErrors() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "900").setBody("""{"error":{"code":"rate_limited","message":"Slow down"}}"""))
        val error = assertFailsWith<ApiException> { api.publicPlayers() }
        assertEquals(429, error.status); assertEquals("rate_limited", error.code)
        assertEquals("900", error.retryAfter); assertEquals("Slow down", error.message)
        for ((status, body) in listOf(503 to "<html>Unavailable</html>", 404 to "null", 400 to "{}")) {
            server.enqueue(MockResponse().setResponseCode(status).setBody(body))
            val failure = assertFailsWith<ApiException> { api.publicPlayers() }
            assertEquals(status, failure.status); assertEquals("http_error", failure.code)
        }
    }

    @Test fun schemaRejectsMalformedResponsesAndKeepsMissingBioNull() = runBlocking {
        for (payload in listOf("not json", "null", "{}", players().replace("\"count\":3", "\"count\":\"wrong\""))) {
            reply(payload)
            assertEquals("invalid_response", assertFailsWith<ApiException> { api.publicPlayers() }.code)
        }
    }

    @Test fun responseSizeRedirectAndTimeoutBoundaries() = runBlocking {
        ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString(), maxResponseBytes = 8)).use { small ->
            reply("♜♞♝")
            assertEquals("response_too_large", assertFailsWith<ApiException> { small.publicPgn("game") }.code)
        }
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", "https://other.example/"))
        assertEquals("unsafe_redirect", assertFailsWith<ApiException> { api.publicPlayers() }.code)
        ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString(), timeoutMillis = 100)).use { slow ->
            server.enqueue(MockResponse().setBody(pgn).setBodyDelay(2, TimeUnit.SECONDS))
            assertEquals("timeout", assertFailsWith<ApiException> { slow.publicPgn("game") }.code)
        }
        assertEquals(3, server.requestCount)
    }

    @Test fun cancellationStopsTheRequestAndClientCloseIsExplicit() = runBlocking {
        server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
        val job = async { api.publicPlayers() }
        assertNotNull(withContext(Dispatchers.IO) { server.takeRequest(2, TimeUnit.SECONDS) })
        job.cancelAndJoin()
        assertTrue(job.isCancelled)
        reply(players()); assertEquals(3, api.publicPlayers().results.size)
        api.close()
        assertEquals("client_closed", assertFailsWith<ApiException> { api.publicPlayers() }.code)
    }

    @Test fun discoveryUsesPublicOriginAndDescriptiveUserAgent() = runBlocking {
        reply("""{"name":"Classic Chess","version":"v1","authentication":{},"documentation":{},"collections":{},"player_resources":{},"tools":{},"pagination":{}}""")
        assertEquals("v1", api.discovery().version)
        val request = server.takeRequest()
        assertEquals("/api/v1/", request.path)
        assertEquals("classicchess-kotlin-client/0.1.0", request.getHeader("User-Agent"))
        assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
    }

    companion object {
        const val pgn = "[Event \"Test\"]\n\n1. e4 e5 *\n"
        const val biography = """{"epithet":"","lifespan":"","crown":"","lede":"A <b>text</b> biography","vitals":[],"sections":[],"numbers":[],"quotes_by":[],"quotes_about":[],"legacy":"","sources":[]}"""
        fun player(slug: String, name: String) = """{"slug":"$slug","name":"$name","archive_bucket":{},"game_count":3,"archive_years":"1950–1960","bookmarked":false,"criteria":"","portrait":{},"bio":null,"urls":{}}"""
        fun players() = """{"source":"public","query":"","active_bucket":"","archive_buckets":[],"count":3,"results":[${player("tal", "Tal")},${player("capa", "José Capablanca")},${player("petro", "Petrosian")}]}"""
        fun game(token: String) = """{"token":"$token","player":null,"white":"White","black":"Black","result":"1-0","event":"Test","date":"1960.??.??","urls":{"api_pgn":"/api/v1/public/games/$token/pgn/"}}"""
        fun books() = """{"source":"annotated","count":3,"results":[${(1..3).joinToString { """{"slug":"book-$it","label":"Book $it","source_url":"","game_count":3,"cover_image":"","urls":{},"download":null}""" }}]}"""
        fun annotatedGame(token: String) = game(token).dropLast(1) + """, "book":{"slug":"book","label":"Book","source_url":""},"annotation":{"source_label":"Source","source_url":"","license":"public-domain","license_name":"Public domain","is_public_domain":true,"note_count":0}}"""
        fun page(path: String, number: Int, next: String?): String {
            val annotated = path.contains("annotated")
            return """{"source":"${if (annotated) "annotated" else "public"}","book":{},"count":3,"page":$number,"page_size":1,"page_count":3,"previous":null,"next":${next?.let { "\"$it\"" } ?: "null"},"results":[${if (annotated) annotatedGame("book/game-$number") else game("game-$number")}]}"""
        }
        fun notables() = """{"source":"public","player":{"name":"Tal","slug":"Tal"},"count":2,"results":[{"position":1,"title":"Second game","annotation":"Context","game":${game("two")}},{"position":2,"title":"First game","annotation":"More context","game":${game("one")}}]}"""
    }
}
