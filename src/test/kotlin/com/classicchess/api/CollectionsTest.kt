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
}
