package com.classicchess.api

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class CapabilityParityTest {
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
}
