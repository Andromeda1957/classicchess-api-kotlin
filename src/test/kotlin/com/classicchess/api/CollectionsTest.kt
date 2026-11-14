package com.classicchess.api

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

class CollectionsTest {
    @Test fun collectionHelpersEncodeNamesAndPreserveBulkContinuation() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            ApplicationClient(ClientOptions(baseUrl = server.url("/").toString())).use { api ->
                server.enqueue(MockResponse().setBody("""{"count":0,"results":[]}"""))
                api.accountFindCollection("  1920 & café?  ", "fixture")
                assertEquals("1920 & café?", server.takeRequest().requestUrl!!.queryParameter("name"))
                server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"code":"insufficient_scope"}}"""))
                assertEquals(403, api.accountDeleteCollection(12, "fixture").status)
                val deleted = server.takeRequest()
                assertEquals("DELETE", deleted.method)
                assertEquals("/api/v1/account/collections/12/", deleted.path)
                for ((after, dry) in listOf(0L to true, 87L to false)) {
                    val reply = """{"matched":2,"has_more":true,"next_after_game_id":87}"""
                    server.enqueue(MockResponse().setBody(reply))
                    val response = api.accountAddCuratedGames(12, listOf(CuratedGameFilter(players = listOf("alekhine"),
                        since = 1920, until = 1920), CuratedGameFilter(notable = false, annotationAuthor = "Réti")), "fixture",
                        exclude = listOf(CuratedGameFilter(events = listOf("london-1922"))), batchSize = 1,
                        afterGameId = after, dryRun = dry)
                    assertEquals(Json.parseToJsonElement(reply), response.data)
                    val request = server.takeRequest()
                    assertEquals("Bearer fixture", request.getHeader("Authorization"))
                    assertEquals("/api/v1/account/collections/import/", request.path)
                    assertEquals(Json.parseToJsonElement("""{"source":"curated_games","collection_id":12,
                        "filters":[{"players":["alekhine"],"since":1920,"until":1920},{"notable":false,"annotation_author":"Réti"}],
                        "exclude":[{"events":["london-1922"]}],"batch_size":1,"after_game_id":$after,"dry_run":$dry}"""),
                        Json.parseToJsonElement(request.body.readUtf8()))
                }
                assertFailsWith<ApiException> { api.accountDeleteCollection(0, "fixture") }
                assertFailsWith<ApiException> { api.accountFindCollection(" ", "fixture") }
                assertFailsWith<ApiException> { api.accountAddCuratedGames(12, listOf(CuratedGameFilter(notable = true)), "fixture", batchSize = 1001) }
                assertFailsWith<ApiException> { api.accountAddCuratedGames(12, listOf(CuratedGameFilter(notable = true)), "fixture", afterGameId = -1) }
                assertEquals(4, server.requestCount)
            }
        }
    }

    private fun library(test: suspend (MockWebServer, ApplicationClient) -> Unit) {
        runBlocking {
            MockWebServer().use { server ->
                server.start()
                ApplicationClient(ClientOptions(baseUrl = server.url("/").toString())).use { api -> test(server, api) }
            }
        }
    }

    @Test fun libraryCallsUseExactMethodsPathsQueriesAndBodies() = library { server, api ->
        val c = "/api/v1/account/collections/12/"
        val imports = "/api/v1/account/imported-games/"
        val cases = listOf<Triple<suspend () -> ApplicationResponse, String, Pair<String, String?>>>(
            Triple(suspend { api.accountCollectionGames(12, "fixture") }, "GET", "${c}?page=1&page_size=50" to null),
            Triple(suspend { api.accountCollectionGames(12, "fixture", 2, 10, "manual", "Tal&Keres") }, "GET",
                "${c}?page=2&page_size=10&sort=manual&q=Tal%26Keres" to null),
            Triple(suspend { api.accountUpdateCollection(12, "fixture", name = "Tal attacks", isPlan = true) }, "PATCH",
                c to """{"name": "Tal attacks", "is_plan": true}"""),
            Triple(suspend { api.accountUpdateCollectionItem(12, 5, "fixture", note = "Watch the e-file", studied = true) }, "PATCH",
                "${c}items/5/" to """{"note": "Watch the e-file", "studied": true}"""),
            Triple(suspend { api.accountRemoveCollectionItem(12, 5, "fixture") }, "DELETE", "${c}items/5/" to null),
            Triple(suspend { api.accountRemoveCollectionGame(12, "tal-vs-larsen", "fixture") }, "DELETE",
                "${c}games/tal-vs-larsen/" to null),
            Triple(suspend { api.accountReorderCollection(12, listOf(9L, 5L), "fixture") }, "PUT",
                "${c}reorder/" to """{"item_ids": [9, 5]}"""),
            Triple(suspend { api.accountPlanProgress(12, "fixture", 2, 25) }, "GET", "${c}progress/?page=2&page_size=25" to null),
            Triple(suspend { api.accountCreatePlan("Tal attacks", listOf(PlanGame("tal-vs-larsen", "The knight sacrifice"),
                PlanGame("g1a2-0123456789ab")), "plan-1", "fixture", "Two games") }, "POST", "/api/v1/account/plans/" to
                """{"name": "Tal attacks", "description": "Two games", "idempotency_key": "plan-1",
                    "items": [{"game": "tal-vs-larsen", "note": "The knight sacrifice"}, {"game": "g1a2-0123456789ab"}]}"""),
            Triple(suspend { api.accountTogglePlayerBookmark("Mikhail_Tal", "fixture") }, "POST",
                "/api/v1/account/players/Mikhail_Tal/bookmark/" to ""),
            Triple(suspend { api.accountGameLibrary("tal-vs-larsen", "fixture") }, "GET",
                "/api/v1/account/games/tal-vs-larsen/library/" to null),
            Triple(suspend { api.accountImportedGames("fixture") }, "GET", "${imports}?page=1&page_size=50" to null),
            Triple(suspend { api.accountImportedGames("fixture", 2, 10, "Tal", "lichess", "date_desc") }, "GET",
                "${imports}?q=Tal&source=lichess&sort=date_desc&page=2&page_size=10" to null),
            Triple(suspend { api.accountImportGame("pgn", "fixture", pgn = "1. e4 *") }, "POST",
                imports to """{"source": "pgn", "pgn": "1. e4 *"}"""),
            Triple(suspend { api.accountImportGame("lichess", "fixture", remoteUrl = "https://lichess.org/abcd1234",
                visibility = "public") }, "POST",
                imports to """{"source": "lichess", "remote_url": "https://lichess.org/abcd1234", "visibility": "public"}"""),
            Triple(suspend { api.accountImportedGame("mine", "fixture") }, "GET", "${imports}mine/" to null),
        )
        for ((call, method, expected) in cases) {
            server.enqueue(MockResponse().setBody("{}"))
            assertTrue(call().ok)
            val request = server.takeRequest()
            assertEquals(method, request.method)
            assertEquals(expected.first, request.path)
            assertEquals("Bearer fixture", request.getHeader("Authorization"))
            val body = request.body.readUtf8()
            when (val json = expected.second) {
                null, "" -> assertEquals("", body)
                else -> assertEquals(Json.parseToJsonElement(json), Json.parseToJsonElement(body))
            }
        }
    }

    @Test fun libraryHelpersRejectInvalidArgumentsBeforeSending() = library { server, api ->
        val game = listOf(PlanGame("tal-vs-larsen"))
        val invalid = listOf<suspend () -> Unit>(
            { api.accountCollectionGames(12, "fixture", 0) }, { api.accountCollectionGames(12, "fixture", sort = "random") },
            { api.accountUpdateCollection(12, "fixture") }, { api.accountUpdateCollectionItem(12, 5, "fixture") },
            { api.accountRemoveCollectionItem(12, 0, "fixture") }, { api.accountRemoveCollectionGame(12, "../me", "fixture") },
            { api.accountReorderCollection(12, List(10_001) { it + 1L }, "fixture") },
            { api.accountCreatePlan("Tal", emptyList(), "plan-1", "fixture") },
            { api.accountCreatePlan("Tal", List(101) { PlanGame("game-$it") }, "plan-1", "fixture") },
            { api.accountCreatePlan("Tal", game, "", "fixture") }, { api.accountCreatePlan(" ", game, "plan-1", "fixture") },
            { api.accountImportedGames("fixture", source = "fide") }, { api.accountImportedGames("fixture", sort = "random") },
            { api.accountImportGame("pgn", "fixture") }, { api.accountImportGame("lichess", "fixture", pgn = "1. e4 *") },
            { api.accountImportGame("pgn", "fixture", pgn = "1. e4 *", visibility = "unlisted") },
            { api.accountGameLibrary("a/b", "fixture") },
        )
        for (call in invalid) assertFailsWith<ApiException> { call() }
        assertEquals(0, server.requestCount)
    }
}
