package com.classicchess.api

import java.util.concurrent.TimeUnit
import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.toList
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class MasterClientTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ClassicChessClient
    @BeforeTest fun start() {
        server = MockWebServer().apply { start() }
        api = ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString()))
    }
    @AfterTest fun stop() { api.close(); server.shutdown() }
    private fun reply(body: String) { server.enqueue(MockResponse().setBody(body)) }

    @Test fun searchPreservesLowerBoundsAndEncodesQueriesWithoutCredentials() = runBlocking {
        for (query in listOf("Karpov", "Kasparov Karpov", "Tal & Keres?")) {
            reply(page())
            val result = api.masterGames(MasterGameFilters(query, page = 2, pageSize = 25))
            assertEquals(26L, result.count)
            assertFalse(result.countIsExact)
            assertEquals("Sicilian Defense", result.results.single().opening)
            assertNull(result.results.single().plyCount)
            val request = server.takeRequest(2, TimeUnit.SECONDS)!!
            assertEquals("/api/v1/games/", request.requestUrl!!.encodedPath)
            assertEquals(query, request.requestUrl!!.queryParameter("q"))
            assertEquals("2", request.requestUrl!!.queryParameter("page"))
            assertEquals("25", request.requestUrl!!.queryParameter("page_size"))
            assertNull(request.getHeader("Authorization"))
            assertNull(request.getHeader("Cookie"))
        }
    }

    @Test fun detailAndPgnKeepScoreAndOpaqueIdentifier() = runBlocking {
        reply(game().dropLast(1) + """, "pgn":"1. e4 e5 *","mainline":{"moves":[{"san":"e4"}]}}""")
        val detail = api.masterGame(TOKEN)
        assertEquals(TOKEN, detail.token)
        assertEquals("1. e4 e5 *", detail.pgn)
        assertNotNull(detail.mainline["moves"])
        assertEquals("/api/v1/games/$TOKEN/", server.takeRequest().path)
        reply(ClientTest.pgn)
        assertEquals(ClientTest.pgn, api.masterPgn(TOKEN))
        assertEquals("/api/v1/games/$TOKEN/pgn/", server.takeRequest().path)
    }

    @Test fun lazyPaginationFollowsNextUntilTheServerCap() = runBlocking {
        reply(page(next = "?q=Tal&page=2"))
        reply(page(token = "g2-000000000000", number = 2, capped = true))
        val flow = api.iterateMasterGames(MasterGameFilters("Tal"))
        assertEquals(0, server.requestCount)
        assertEquals(listOf(TOKEN, "g2-000000000000"), flow.toList().map { it.token })
        assertEquals(2, server.requestCount)
        assertEquals("/api/v1/games/?q=Tal", server.takeRequest().path)
        assertEquals("/api/v1/games/?q=Tal&page=2", server.takeRequest().path)
    }

    @Test fun invalidIdentifiersQueriesAndPaginationNeverReachTheNetwork() = runBlocking {
        for (token in listOf("game-slug", "../account", "$TOKEN?x=1", "g1-00000000000z", "g" + "1".repeat(67) + "-000000000000")) {
            assertEquals("invalid_identifier", assertFailsWith<ApiException> { api.masterGame(token) }.code)
            assertFailsWith<ApiException> { api.masterPgn(token) }
        }
        for (query in listOf("", "   ", "a".repeat(121), "♞".repeat(121), "\uD83D\uDC34".repeat(121))) {
            assertEquals("invalid_query", assertFailsWith<ApiException> { api.masterGames(MasterGameFilters(query)) }.code)
        }
        for (filters in listOf(MasterGameFilters("Tal", page = 0), MasterGameFilters("Tal", pageSize = 101))) {
            assertEquals("invalid_pagination", assertFailsWith<ApiException> { api.masterGames(filters) }.code)
        }
        assertEquals(0, server.requestCount)
        reply(page())
        api.masterGames(MasterGameFilters("\uD83D\uDC34".repeat(120)))
        assertEquals(1, server.requestCount)
    }

    @Test fun masterPaginationRejectsAccountForeignAndDifferentCollectionLinks() = runBlocking {
        for (next in listOf("/api/v1/account/me/", "https://other.example/api/v1/games/", "/api/v1/public/games/", "/api/v1/games/$TOKEN/", "/api/v1/games/export/")) {
            reply(page(next = next))
            val before = server.requestCount
            assertEquals("unsafe_url", assertFailsWith<ApiException> { api.iterateMasterGames(MasterGameFilters("Tal")).toList() }.code)
            assertEquals(before + 1, server.requestCount)
        }
    }

    @Test fun masterReadsKeepRateLimitDetailsAndRejectRedirects() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "30")
            .setBody("""{"error":{"code":"rate_limited","message":"Slow down"}}"""))
        val error = assertFailsWith<ApiException> { api.masterGames(MasterGameFilters("Tal")) }
        assertEquals("rate_limited", error.code)
        assertEquals("30", error.retryAfter)
        assertEquals(429, error.status)
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/api/v1/account/me/")))
        assertEquals("unsafe_redirect", assertFailsWith<ApiException> { api.masterGame(TOKEN) }.code)
        assertEquals(2, server.requestCount)
    }

    companion object {
        const val TOKEN = "g1-000000000000"
        fun game(token: String = TOKEN) = """{"token":"$token","white":"Tal","black":"Keres","display_white":"Tal","display_black":"Keres","result":"1-0","event":"Test","site":"Riga","date":"1960.??.??","played_on":null,"year":1960,"round":"1","white_elo":null,"black_elo":null,"eco":"B33","opening":"Sicilian Defense","ply_count":null,"move_count":null,"source_count":1,"urls":{"api":"/api/v1/games/$token/"}}"""
        fun page(token: String = TOKEN, next: String? = null, number: Int = 1, capped: Boolean = false) = """{"query":"Tal","count":26,"count_is_exact":false,"page":$number,"page_size":25,"page_count":2,"result_limit":1000,"hit_result_limit":$capped,"next":${next?.let { "\"$it\"" } ?: "null"},"previous":null,"message":null,"hint":"Refine your search","results":[${game(token)}]}"""
    }
}
