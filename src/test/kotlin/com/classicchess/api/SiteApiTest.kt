package com.classicchess.api

import kotlin.test.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer

/** Site page reads decode payloads captured from the server into the generated models. */
class SiteApiTest {
    private val fixtures: JsonObject = Json.parseToJsonElement(SITE_API_FIXTURES).jsonObject

    private fun body(name: String) = fixtures.getValue(name).toString()

    @Test fun sitePageReadsDecodeServerPayloadsFromVersionedRoutes() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            val photoDetail = "{\"source\":\"public\",\"photo\":" +
                fixtures.getValue("gallery").jsonObject.getValue("results").jsonArray.first() + "}"
            for (name in listOf("daily", "gallery", "photo", "beginner", "event", "about", "search", "search_page", "tablebase")) {
                server.enqueue(MockResponse().setBody(if (name == "photo") photoDetail else body(name)))
            }
            ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString())).use { api ->
                val daily = api.dailyGame()
                assertEquals(fixtures["daily"]!!.jsonObject["game"]!!.jsonObject["slug"]!!.jsonPrimitive.content, daily.game!!.slug)
                assertTrue(daily.game!!.descriptor.isNotBlank())
                assertTrue(daily.game!!.urls["api_detail"]!!.startsWith("/api/v1/public/games/"))
                val gallery = api.gallery(GalleryFilters(query = "tal", page = 2, pageSize = 12))
                val photo = gallery.results.single()
                assertTrue(photo.image.url.startsWith("http"))
                assertTrue(photo.attribution.license.isNotBlank())
                assertEquals(photo, api.galleryPhoto(photo.id).photo)
                assertEquals(1, api.beginnerGames().stages.single().entries.single().step)
                val event = api.publicEvent("wcc-1972")
                val about = api.publicEventAbout("wcc-1972")
                assertEquals(event.event.slug, about.event.slug)
                assertNotNull(about.bio)
                assertEquals("Format", about.autoVitals.first().label)
                assertEquals("matchup", api.siteSearch("fischer spassky").intent)
                assertEquals("games", api.siteSearchPage("fischer spassky", "games", 2).kind)
                val probe = api.tablebase("8/8/8/8/8/2k5/2P5/2K5 w - - 0 1")
                assertEquals("draw", probe.moves.single().wdl)
                val requests = (1..9).map { server.takeRequest() }
                assertEquals(listOf(
                    "/api/v1/public/daily/", "/api/v1/public/gallery/?page=2&page_size=12&q=tal",
                    "/api/v1/public/gallery/${photo.id}/", "/api/v1/public/beginner-games/",
                    "/api/v1/public/events/wcc-1972/", "/api/v1/public/events/wcc-1972/about/",
                    "/api/v1/public/search/?q=fischer%20spassky", "/api/v1/public/search/?q=fischer%20spassky&kind=games&page=2",
                    "/api/v1/tablebase/?fen=8%2F8%2F8%2F8%2F8%2F2k5%2F2P5%2F2K5%20w%20-%20-%200%201",
                ), requests.map { it.path })
                assertTrue(requests.all { it.getHeader("Authorization") == null })
            }
        }
    }

    @Test fun dailyGameDecodesAnEmptyPool() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setBody("{\"source\":\"public\",\"date\":\"2026-09-22\",\"game\":null}"))
            ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString())).use { api ->
                assertNull(api.dailyGame().game)
            }
        }
    }

    @Test fun sitePageReadsRejectUnsafeInputBeforeRequesting() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            ClassicChessClient(ClientOptions(baseUrl = server.url("/").toString())).use { api ->
                val calls = listOf<suspend () -> Unit>(
                    { api.publicEvent("../account/me") }, { api.publicEventAbout("a/b") }, { api.galleryPhoto("x?y") },
                    { api.gallery(GalleryFilters(pageSize = 500)) }, { api.siteSearch("") }, { api.siteSearch("x".repeat(121)) },
                    { api.siteSearchPage("tal", "users") }, { api.siteSearchPage("tal", "games", 0) },
                    { api.tablebase("") }, { api.tablebase("k".repeat(201)) },
                )
                for (call in calls) assertFailsWith<ApiException> { call() }
                assertEquals(0, server.requestCount)
            }
        }
    }

    @Test fun libraryHelpersSendExactMethodsPathsAndBodies() = runBlocking {
        MockWebServer().use { server ->
            server.start()
            repeat(9) { server.enqueue(MockResponse().setBody("{\"changed\":true}")) }
            ApplicationClient(ClientOptions(baseUrl = server.url("/").toString())).use { api ->
                assertTrue(api.accountAddCollectionGame(7, "tal-vs-larsen", "fixture").ok)
                api.accountStarredPlayers("fixture", 2, 10)
                api.accountStarPlayer("mikhail-tal", "fixture")
                api.accountUnstarPlayer("mikhail-tal", "fixture")
                api.accountStarredGames("fixture")
                api.accountStarGame("g1", "fixture")
                api.accountUnstarGame("g1", "fixture")
                api.accountSetImportedGameVisibility("mine", "public", "fixture")
                api.accountDeleteImportedGame("mine", "fixture")
                val requests = (1..9).map { server.takeRequest() }
                assertEquals(listOf(
                    "POST /api/v1/account/collections/7/items/", "GET /api/v1/account/starred/players/?page=2&page_size=10",
                    "PUT /api/v1/account/starred/players/mikhail-tal/", "DELETE /api/v1/account/starred/players/mikhail-tal/",
                    "GET /api/v1/account/starred/games/?page=1&page_size=50", "PUT /api/v1/account/starred/games/g1/",
                    "DELETE /api/v1/account/starred/games/g1/", "PATCH /api/v1/account/imported-games/mine/",
                    "DELETE /api/v1/account/imported-games/mine/",
                ), requests.map { "${it.method} ${it.path}" })
                assertEquals("{\"game\":\"tal-vs-larsen\"}", requests[0].body.readUtf8())
                assertEquals("{\"visibility\":\"public\"}", requests[7].body.readUtf8())
                assertTrue(requests.all { it.getHeader("Authorization") == "Bearer fixture" })
                val calls = listOf<suspend () -> Unit>(
                    { api.accountAddCollectionGame(0, "g", "t") }, { api.accountAddCollectionGame(7, "../me", "t") },
                    { api.accountStarPlayer("a/b", "t") }, { api.accountUnstarGame("", "t") },
                    { api.accountStarredGames("t", 1, 500) }, { api.accountSetImportedGameVisibility("mine", "PUBLIC", "t") },
                    { api.accountDeleteImportedGame("x?y", "t") },
                )
                for (call in calls) assertFailsWith<ApiException> { call() }
                assertEquals(9, server.requestCount)
            }
        }
    }
}
