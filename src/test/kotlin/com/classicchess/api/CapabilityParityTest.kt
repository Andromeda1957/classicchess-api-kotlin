package com.classicchess.api

import kotlin.test.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer

class CapabilityParityTest {
    private fun archive(test: suspend (MockWebServer, ClassicChessClient) -> Unit) {
        runBlocking {
            MockWebServer().use { server ->
                server.start()
                ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString())).use { api -> test(server, api) }
            }
        }
    }

    private fun MockWebServer.reply(body: String) = enqueue(MockResponse().setBody(body))

    @Test fun binaryUploadsPreserveEveryByteAndContentType() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("{\"ok\":true}"))
            ApplicationClient(ClientOptions(baseUrl = server.url("/").toString())).use { api ->
                val bytes = ByteArray(256) { it.toByte() }
                assertTrue(api.requestBytes("/api/v1/desktop/notebooks/sync/", bytes, "fixture").ok)
                val request = server.takeRequest()
                assertContentEquals(bytes, request.body.readByteArray())
                assertEquals("application/octet-stream", request.getHeader("Content-Type"))
                assertEquals("Bearer fixture", request.getHeader("Authorization"))
            }
        }
    }

    @Test fun pgnBundlesKeepOrderAndRejectOtherOrigins() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            repeat(2) { server.enqueue(MockResponse().setBody(" PGN ${it + 1}\n")) }
            ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString())).use { api ->
                assertEquals("PGN 1\n\nPGN 2\n", api.pgnTextForGames(listOf(
                    "/api/v1/public/games/one/pgn/", "/api/v1/annotated/books/book/pgn/")))
                assertEquals("", api.pgnTextForGames(emptyList()))
                assertFailsWith<ApiException> {
                    api.pgnTextForGames(listOf("https://elsewhere.test/api/v1/public/games/one/pgn/"))
                }
                assertEquals(2, server.requestCount)
            }
        }
    }

    @Test fun publicToolsAndExportFormats() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString())).use { api ->
                repeat(4) { server.enqueue(MockResponse().setBody("{\"ok\":true}")) }
                repeat(2) { server.enqueue(MockResponse().setBody("exported")) }
                api.masterStats(MasterStatsFilters(query = "Tal & Keres"))
                api.players("Tal & Keres", 7)
                api.explorer(ExplorerFilters(play = "e2e4,e7e5", topGames = 3))
                api.explorerSources()
                assertEquals("exported", api.exportMasterGames(query = "Tal", format = "ndjson", pgnInJson = false))
                assertEquals("exported", api.exportPublicGames(PublicGameFilters(archivePlayer = "Tal"), format = "ndjson", pgnInJson = false))
                val requests = (1..6).map { server.takeRequest() }
                assertEquals(listOf("/api/v1/stats/", "/api/v1/players/", "/api/v1/opening-explorer/",
                    "/api/v1/opening-explorer/sources/", "/api/v1/games/export/", "/api/v1/public/games/export/"),
                    requests.map { it.requestUrl!!.encodedPath })
                assertEquals("Tal & Keres", requests[0].requestUrl!!.queryParameter("q"))
                assertEquals("7", requests[1].requestUrl!!.queryParameter("limit"))
                assertEquals("3", requests[2].requestUrl!!.queryParameter("topGames"))
                requests.drop(4).forEach {
                    assertEquals("ndjson", it.requestUrl!!.queryParameter("format"))
                    assertEquals("false", it.requestUrl!!.queryParameter("pgnInJson"))
                }
                requests.forEach { assertNull(it.getHeader("Authorization")) }
            }
        }
    }

    @Test fun lichessExplorerRelaysThroughTheSiteAndRejectsBadFilters() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString())).use { api ->
                repeat(4) { server.enqueue(MockResponse().setBody("{\"ok\":true,\"count\":3}")) }
                assertEquals("3", api.lichessExplorer()["count"].toString())
                api.lichessExplorer(LichessExplorerFilters(fen = "8/8/8/8/8/2k5/2P5/2K5 w - - 0 1",
                    player = "DrNykterstein", color = "white", result = "win"))
                api.lichessExplorer(LichessExplorerFilters(since = 2015, until = 2016))
                api.lichessExplorer(LichessExplorerFilters(player = "DrNykterstein", since = 2020))
                val rejected = listOf<suspend () -> Unit>(
                    { api.lichessExplorer(LichessExplorerFilters(player = "a")) },
                    { api.lichessExplorer(LichessExplorerFilters(player = "x/../me")) },
                    { api.lichessExplorer(LichessExplorerFilters(result = "win")) },
                    { api.lichessExplorer(LichessExplorerFilters(player = "abc", color = "red")) },
                    { api.lichessExplorer(LichessExplorerFilters(fen = "k".repeat(101))) },
                )
                for (call in rejected) assertFailsWith<ApiException> { call() }
                assertEquals(4, server.requestCount)
                val requests = (1..4).map { server.takeRequest() }
                assertEquals(listOf("/api/v1/opening-explorer/lichess/",
                    "/api/v1/opening-explorer/lichess/?fen=8%2F8%2F8%2F8%2F8%2F2k5%2F2P5%2F2K5%20w%20-%20-%200%201" +
                        "&player=DrNykterstein&color=white&result=win",
                    "/api/v1/opening-explorer/lichess/?since=2015&until=2016",
                    "/api/v1/opening-explorer/lichess/?player=DrNykterstein&color=both&result=all&since=2020"),
                    requests.map { it.path })
                requests.forEach { assertNull(it.getHeader("Authorization")) }
            }
        }
    }

    @Test fun accountHelpersPreserveCredentialsAndBodies() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            ApplicationClient(ClientOptions(baseUrl = server.url("/").toString())).use { api ->
                repeat(4) { server.enqueue(MockResponse().setBody("{\"ok\":true}")) }
                api.accountMe("fixture")
                api.accountCollections("fixture")
                api.accountCreateCollection("Tal archive", "fixture")
                api.accountImportPublicPlayerGames("Tal", "fixture", name = "Tal archive", since = 1960)
                val requests = (1..4).map { server.takeRequest() }
                assertEquals(listOf("/api/v1/account/me/", "/api/v1/account/collections/",
                    "/api/v1/account/collections/", "/api/v1/account/collections/import/"), requests.map { it.path })
                requests.forEach { assertEquals("Bearer fixture", it.getHeader("Authorization")) }
                assertContains(requests[2].body.readUtf8(), "Tal archive")
                assertContains(requests[3].body.readUtf8(), "archive_player")
            }
        }
    }

    @Test fun tokenExportsPostTheTokensAndKeepTheFormatInTheQuery() = archive { server, api ->
        repeat(2) { server.reply("exported") }
        assertEquals("exported", api.exportMasterGamesByToken(listOf("g1a2-0123456789ab", "gz9-00000000000f")))
        assertEquals("exported", api.exportPublicGamesByToken(listOf("tal-vs-larsen", "Mikhail_Tal/g1"), "ndjson", pgnInJson = false))
        val master = server.takeRequest()
        val site = server.takeRequest()
        assertEquals("/api/v1/games/export/?format=pgn", master.path)
        assertEquals("g1a2-0123456789ab\ngz9-00000000000f", master.body.readUtf8())
        assertEquals("/api/v1/public/games/export/?format=ndjson&pgnInJson=false", site.path)
        assertEquals("tal-vs-larsen\nMikhail_Tal/g1", site.body.readUtf8())
        for (request in listOf(master, site)) {
            assertEquals("POST", request.method)
            assertEquals("text/plain; charset=utf-8", request.getHeader("Content-Type"))
            assertNull(request.getHeader("Authorization"))
        }
        val rejected = listOf<suspend () -> Unit>(
            { api.exportMasterGamesByToken(emptyList()) }, { api.exportPublicGamesByToken(List(301) { "g$it" }) },
            { api.exportMasterGamesByToken(listOf("tal-vs-larsen")) }, { api.exportPublicGamesByToken(listOf("../me")) },
        )
        for (call in rejected) assertFailsWith<ApiException> { call() }
        assertEquals(2, server.requestCount)
    }

    @Test fun gameLinksPostTheUrlAndDecodeTheResolvedGame() = archive { server, api ->
        server.reply("""{"pgn":"1. e4 *","orientation":"black"}""")
        val game = api.resolveGameLink("https://lichess.org/abcd1234/black")
        assertEquals("1. e4 *", game.pgn)
        assertEquals(ResolvedGameLink.Orientation.BLACK, game.orientation)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/v1/analysis/game-link/", request.path)
        assertEquals(Json.parseToJsonElement("""{"url":"https://lichess.org/abcd1234/black"}"""),
            Json.parseToJsonElement(request.body.readUtf8()))
        assertNull(request.getHeader("Authorization"))
        server.enqueue(MockResponse().setResponseCode(400).setBody("""{"error":{"code":"unsupported_game_link","message":"No."}}"""))
        val refused = assertFailsWith<ApiException> { api.resolveGameLink("https://example.com/game") }
        assertEquals(400 to "unsupported_game_link", refused.status to refused.code)
        assertFailsWith<ApiException> { api.resolveGameLink("https://lichess.org/" + "a".repeat(481)) }
        assertEquals(2, server.requestCount)
    }

    @Test fun newArchiveReadsUseExactPathsAndQueriesWithoutCredentials() = archive { server, api ->
        repeat(4) { server.reply("{\"ok\":true}") }
        api.playerExplorer(PlayerExplorerFilters(" Tal ", fen = FEN, color = "white", result = "win", page = 2,
            since = 1950, until = 1960))
        api.lichessExplorerGame("abcd1234")
        api.studyThemes()
        api.explorer(ExplorerFilters(play = "e2e4", since = 1950, until = 1960))
        repeat(2) { server.reply(SiteGapFixtures.CHESS_TODAY) }
        assertEquals("tal-born", api.chessToday().featuredEntryId)
        assertEquals("Mikhail Tal is born in Riga.", api.chessTodayOnDate(11, 9).entries.single().text)
        server.reply(SiteGapFixtures.GYM_SHARED_GAME)
        assertEquals("v4", api.sharedGymGame(GYM_SHARE).botVersion)
        server.reply(SiteGapFixtures.SITE_CONTENT_SEARCH)
        assertEquals("Castling", api.siteContentSearch("castling", 2).results.single().heading)
        val requests = (1..8).map { server.takeRequest() }
        assertEquals(listOf(
            "/api/v1/opening-explorer/player/?player=Tal&fen=$ENCODED_FEN&color=white&result=win&page=2&since=1950&until=1960",
            "/api/v1/opening-explorer/lichess/games/abcd1234/", "/api/v1/public/themes/",
            "/api/v1/opening-explorer/?play=e2e4&moves=12&since=1950&until=1960",
            "/api/v1/public/chess-today/", "/api/v1/public/chess-today/11/9/", "/api/v1/public/gym/games/$GYM_SHARE/",
            "/api/v1/public/search/?q=castling&kind=site&page=2",
        ), requests.map { it.path })
        requests.forEach { assertNull(it.getHeader("Authorization")) }
    }

    @Test fun newGameFiltersReachTheQuery() = archive { server, api ->
        server.reply(ClientTest.page("/api/v1/public/games/", 1, null))
        api.publicGames(PublicGameFilters(theme = "sacrifice,king-hunt", eco = "B33", result = "1/2-1/2",
            annotationQ = "knight sacrifice", annotationAuthor = "Botvinnik", annotationBook = "My System"))
        server.reply("exported")
        api.exportPublicGames(PublicGameFilters(eco = "D", result = "*"))
        val filters = AnnotatedGameFilters(query = "sacrifice", headerQuery = "Tal", author = "Botvinnik", sort = "desc")
        repeat(2) { server.reply(ClientTest.page("/api/v1/annotated/books/book/games/", 1, null)) }
        api.annotatedGames("book", PageOptions(page = 2), filters)
        assertEquals(1, api.iterateAnnotatedGames("book", filters = filters).toList().size)
        val queries = (1..4).map { server.takeRequest().requestUrl!!.let { url -> url.queryParameterNames.associateWith { url.queryParameter(it) } } }
        assertEquals(mapOf("theme" to "sacrifice,king-hunt", "eco" to "B33", "result" to "1/2-1/2",
            "annotation_q" to "knight sacrifice", "annotation_author" to "Botvinnik", "annotation_book" to "My System"), queries[0])
        assertEquals(mapOf("eco" to "D", "result" to "*", "format" to "pgn"), queries[1])
        val annotated = mapOf("q" to "sacrifice", "header_q" to "Tal", "author" to "Botvinnik", "sort" to "desc")
        assertEquals(mapOf("page" to "2") + annotated, queries[2])
        assertEquals(annotated, queries[3])
    }

    @Test fun siteUpdatesReadPagesAndIterateUntilTheLast() = archive { server, api ->
        server.reply(SiteGapFixtures.siteUpdatePage(1, true, "2026-11-12-api", "2026-11-11-books"))
        server.reply(SiteGapFixtures.siteUpdatePage(2, false, "2026-11-10-gym"))
        assertEquals(listOf("2026-11-12-api", "2026-11-11-books", "2026-11-10-gym"),
            api.iterateSiteUpdates(pageSize = 2).toList().map { it.id })
        server.reply(SiteGapFixtures.siteUpdatePage(3, false, "2026-11-09-old"))
        assertEquals(1, api.siteUpdates(PageOptions(page = 3)).results.size)
        server.reply("""{"source":"public","post":${SiteGapFixtures.siteUpdatePost("2026-11-12-api")}}""")
        assertEquals("Post 2026-11-12-api", api.siteUpdate("2026-11-12-api").post.title)
        server.reply(SiteGapFixtures.siteUpdatePage(1, true, "2026-11-12-api"))
        assertEquals(1, api.iterateSiteUpdates(options = IterationOptions(limit = 1)).toList().size)
        assertEquals(listOf("/api/v1/public/site-updates/?page=1&page_size=2", "/api/v1/public/site-updates/?page=2&page_size=2",
            "/api/v1/public/site-updates/?page=3", "/api/v1/public/site-updates/2026-11-12-api/",
            "/api/v1/public/site-updates/?page=1"), (1..5).map { server.takeRequest().path })
        repeat(2) { server.reply(SiteGapFixtures.siteUpdatePage(1, true, "2026-11-12-api")) }
        assertEquals("invalid_pagination", assertFailsWith<ApiException> { api.iterateSiteUpdates().toList() }.code)
        assertEquals(7, server.requestCount)
    }

    @Test fun bookPackagesNegotiateTheCcnbFormatWithoutCredentials() = archive { server, api ->
        val bytes = byteArrayOf(67, 67, 78, 66, 0, 3, -1)
        for (format in listOf(null, 2, 3)) {
            server.enqueue(MockResponse().setBody(Buffer().write(bytes)).setHeader("Content-Type", "application/vnd.classicchess.notebook"))
            assertContentEquals(bytes, api.annotatedBookCcnb("my-system", format))
            val request = server.takeRequest()
            assertEquals("/api/v1/annotated/books/my-system/ccnb/", request.path)
            assertEquals(format?.toString(), request.getHeader("CCNB-Format"))
            assertNull(request.getHeader("Authorization"))
        }
        server.enqueue(MockResponse().setBody("{}").setHeader("Content-Type", "application/json"))
        assertEquals("invalid_response", assertFailsWith<ApiException> { api.annotatedBookCcnb("my-system") }.code)
    }

    @Test fun newArchiveReadsRejectInvalidArgumentsBeforeSending() = archive { server, api ->
        val rejected = listOf<suspend () -> Unit>(
            { api.playerExplorer(PlayerExplorerFilters(" ")) }, { api.playerExplorer(PlayerExplorerFilters("x".repeat(81))) },
            { api.playerExplorer(PlayerExplorerFilters("Tal", color = "red")) },
            { api.playerExplorer(PlayerExplorerFilters("Tal", result = "won")) },
            { api.playerExplorer(PlayerExplorerFilters("Tal", page = 0)) },
            { api.playerExplorer(PlayerExplorerFilters("Tal", fen = "k".repeat(101))) },
            { api.lichessExplorerGame("abcd123") }, { api.lichessExplorerGame("abcd/234") },
            { api.chessTodayOnDate(13, 1) }, { api.chessTodayOnDate(1, 0) },
            { api.sharedGymGame("42") }, { api.sharedGymGame("42.a/b") }, { api.siteUpdate("../me") },
            { api.siteUpdates(PageOptions(pageSize = 101)) }, { api.siteContentSearch(" ") }, { api.siteContentSearch("castling", 0) },
            { api.publicGames(PublicGameFilters(result = "win")) },
            { api.annotatedGames("book", filters = AnnotatedGameFilters(sort = "event")) },
            { api.annotatedBookCcnb("my-system", 4) }, { api.annotatedBookCcnb("../me") }, { api.resolveGameLink(" ") },
        )
        for (call in rejected) assertFailsWith<ApiException> { call() }
        assertEquals(0, server.requestCount)
    }
}

private const val FEN = "8/8/8/8/8/2k5/2P5/2K5 w - - 0 1"
private const val ENCODED_FEN = "8%2F8%2F8%2F8%2F8%2F2k5%2F2P5%2F2K5%20w%20-%20-%200%201"
private const val GYM_SHARE = "42.UdIv0NXnPaQ8SDu2cYEOqT1ZYAuPTm7-ZvJRgtfm3fs"
