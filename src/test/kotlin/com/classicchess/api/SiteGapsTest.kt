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
    private val fen = "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1"

    @BeforeTest fun start() {
        server = MockWebServer(); server.start()
        client = ApplicationClient(ClientOptions(baseUrl = server.url("/").toString()))
    }
    @AfterTest fun stop() { client.close(); server.shutdown() }

    private fun file(bytes: ByteArray, type: String, name: String) = MockResponse().setBody(Buffer().write(bytes))
        .setHeader("Content-Type", type).setHeader("Content-Disposition", "attachment; filename=" + '"' + name + '"')

    private fun sent() = server.takeRequest(2, TimeUnit.SECONDS)!!

    @Test fun ccnbFormatNegotiatesGetAndPasswordPost() = runBlocking {
        for (format in listOf(null, 2, 3)) for (password in listOf(null, "test-only-password")) {
            server.enqueue(file(gif, "application/vnd.classicchess.notebook", "n.ccnb"))
            assertContentEquals(gif, client.accountNotebookFile(notebook, "t", password, ccnbFormat = format).bytes)
            val request = sent()
            assertEquals("/api/v1/account/notebooks/$notebook/file/", request.path)
            assertEquals(format?.toString(), request.getHeader("CCNB-Format"))
            assertEquals("Bearer t", request.getHeader("Authorization"))
            assertEquals(if (password == null) "GET" else "POST", request.method)
        }
        assertEquals("invalid_request", assertFailsWith<ApiException> { client.accountNotebookFile(notebook, "t", ccnbFormat = 4) }.code)
        assertEquals("invalid_request", assertFailsWith<ApiException> { client.download("/api/v1/account/me/", "t", ccnbFormat = 3) }.code)
        assertEquals(6, server.requestCount)
    }

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
            Triple(suspend { client.accountDismissAllNotifications("t") }, "POST", "/api/v1/account/notifications/dismiss-all/"),
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

    @Test fun notebookEditingCallsUseExactMethodsPathsAndBodies() = runBlocking {
        val n = "/api/v1/account/notebooks/$notebook/"
        val cases = listOf<Triple<suspend () -> ApplicationResponse, String, Pair<String, String?>>>(
            Triple(suspend { client.accountCreateNotebook("Najdorf", "t", "Main lines", "unlisted", "6.Bg5", fen) }, "POST",
                "/api/v1/account/notebooks/" to """{"title": "Najdorf", "description": "Main lines", "visibility": "unlisted",
                    "chapter_title": "6.Bg5", "initial_position": "$fen"}"""),
            Triple(suspend { client.accountCreateNotebook("From a game", "t", game = "tal-vs-larsen") }, "POST",
                "/api/v1/account/notebooks/" to """{"title": "From a game", "game": "tal-vs-larsen"}"""),
            Triple(suspend { client.accountRenameNotebook(notebook.uppercase(), "Najdorf 2", "t") }, "PATCH",
                n to """{"title": "Najdorf 2"}"""),
            Triple(suspend { client.accountDeleteNotebook(notebook, "t") }, "DELETE", n to null),
            Triple(suspend { client.accountNotebookSettings(notebook, "t") }, "GET", "${n}settings/" to null),
            Triple(suspend { client.accountUpdateNotebookSettings(notebook, "t", visibility = "public", allowPublicExports = false) },
                "PATCH", "${n}settings/" to """{"visibility": "public", "allow_public_exports": false}"""),
            Triple(suspend { client.accountSetNotebookCollaborator(notebook, "ann.lee", "editor", "t", canPresentLive = true) },
                "POST", "${n}collaborators/" to """{"username": "ann.lee", "role": "editor", "can_present_live": true}"""),
            Triple(suspend { client.accountRemoveNotebookCollaborator(notebook, 4, "t") }, "DELETE", "${n}collaborators/4/" to null),
            Triple(suspend { client.accountLeaveNotebook(notebook, "t") }, "DELETE", "${n}membership/" to null),
            Triple(suspend { client.accountNotebookShare(notebook, "t") }, "GET", "${n}share/" to null),
            Triple(suspend { client.accountCreateNotebookShare(notebook, "t") }, "PUT", "${n}share/" to ""),
            Triple(suspend { client.accountRevokeNotebookShare(notebook, "t") }, "DELETE", "${n}share/" to null),
            Triple(suspend { client.accountCreateNotebookSection(notebook, "Sicilian", "t", parentId = 3) }, "POST",
                "${n}sections/" to """{"title": "Sicilian", "parent_id": 3}"""),
            Triple(suspend { client.accountRenameNotebookSection(notebook, 3, "Open Sicilian", "t") }, "PATCH",
                "${n}sections/3/" to """{"title": "Open Sicilian"}"""),
            Triple(suspend { client.accountDeleteNotebookSection(notebook, 3, "t") }, "DELETE", "${n}sections/3/" to null),
            Triple(suspend { client.accountMoveNotebookSection(notebook, 3, "t", parentId = 2, beforeSectionId = 8) }, "POST",
                "${n}sections/3/move/" to """{"parent_id": 2, "before_section_id": 8}"""),
            Triple(suspend { client.accountMoveNotebookSection(notebook, 3, "t") }, "POST", "${n}sections/3/move/" to "{}"),
            Triple(suspend { client.accountCreateNotebookChapter(notebook, "t", "Poisoned Pawn", sectionId = 3) }, "POST",
                "${n}chapters/" to """{"title": "Poisoned Pawn", "section_id": 3}"""),
            Triple(suspend { client.accountNotebookChapter(notebook, 9, "t") }, "GET", "${n}chapters/9/" to null),
            Triple(suspend { client.accountSaveNotebookChapter(notebook, 9, "1. e4 c5 *", 4, "t", "Sicilian") }, "PUT",
                "${n}chapters/9/" to """{"pgn": "1. e4 c5 *", "content_version": 4, "title": "Sicilian"}"""),
            Triple(suspend { client.accountRenameNotebookChapter(notebook, 9, "Najdorf", "t") }, "PATCH",
                "${n}chapters/9/" to """{"title": "Najdorf"}"""),
            Triple(suspend { client.accountDeleteNotebookChapter(notebook, 9, "t") }, "DELETE", "${n}chapters/9/" to null),
            Triple(suspend { client.accountMoveNotebookChapter(notebook, 9, "t", sectionId = 3, beforeChapterId = 11) }, "POST",
                "${n}chapters/9/move/" to """{"section_id": 3, "before_chapter_id": 11}"""),
            Triple(suspend { client.accountNotebookChapterShare(notebook, 9, "t") }, "GET", "${n}chapters/9/share/" to null),
            Triple(suspend { client.accountCreateNotebookChapterShare(notebook, 9, "t") }, "PUT", "${n}chapters/9/share/" to ""),
            Triple(suspend { client.accountRevokeNotebookChapterShare(notebook, 9, "t") }, "DELETE", "${n}chapters/9/share/" to null),
        )
        for ((call, method, expected) in cases) {
            server.enqueue(MockResponse().setBody("{ \"ok\": true }"))
            assertTrue(call().ok)
            val request = sent()
            assertEquals(method, request.method)
            assertEquals(expected.first, request.path)
            assertEquals("Bearer t", request.getHeader("Authorization"))
            val body = request.body.readUtf8()
            when (val json = expected.second) {
                null, "" -> assertEquals("", body)
                else -> assertEquals(Json.parseToJsonElement(json), Json.parseToJsonElement(body))
            }
        }
    }

    @Test fun aStaleChapterSaveReportsChapterConflict() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(409)
            .setBody("{ \"error\": { \"code\": \"chapter_conflict\", \"message\": \"The chapter changed.\" } }"))
        val stale = client.accountSaveNotebookChapter(notebook, 9, "1. e4 *", 3, "t")
        assertEquals(409, stale.status)
        assertEquals("chapter_conflict", stale.errorCode)
        assertEquals("PUT", sent().method)
        server.enqueue(MockResponse().setBody("{ \"id\": 9, \"content_version\": 4 }"))
        assertNull(client.accountSaveNotebookChapter(notebook, 9, "1. e4 *", 3, "t").errorCode)
        sent()
        assertEquals(2, server.requestCount)
    }

    @Test fun notebookEditingRejectsInvalidArgumentsBeforeSending() = runBlocking {
        val invalid = listOf<suspend () -> Unit>(
            { client.accountCreateNotebook(" ", "t") }, { client.accountCreateNotebook("N", "t", visibility = "secret") },
            { client.accountCreateNotebook("N", "t", initialPosition = fen, game = "tal-vs-larsen") },
            { client.accountRenameNotebook("not-a-uuid", "N", "t") }, { client.accountRenameNotebook(notebook, " ", "t") },
            { client.accountUpdateNotebookSettings(notebook, "t") },
            { client.accountSetNotebookCollaborator(notebook, "ann", "owner", "t") },
            { client.accountRemoveNotebookCollaborator(notebook, 0, "t") },
            { client.accountMoveNotebookSection(notebook, 3, "t", parentId = 0) },
            { client.accountCreateNotebookChapter(notebook, "t", initialPosition = fen, game = "tal-vs-larsen") },
            { client.accountSaveNotebookChapter(notebook, 9, "1. e4 *", 0, "t") },
            { client.accountMoveNotebookChapter(notebook, 0, "t") },
        )
        for (call in invalid) assertFailsWith<ApiException> { call() }
        assertEquals(0, server.requestCount)
    }

    @Test fun gymCallsUseExactMethodsPathsAndBodies() = runBlocking {
        val game = "/api/v1/account/gym/games/7/"
        val newGame = "/api/v1/account/gym/bots/morphy/games/"
        val cases = listOf<Triple<suspend () -> ApplicationResponse, String, Pair<String, String?>>>(
            Triple(suspend { client.accountGym("t") }, "GET", "/api/v1/account/gym/?page_size=20" to null),
            Triple(suspend { client.accountGymGames("t", 2, 10) }, "GET", "/api/v1/account/gym/games/?page=2&page_size=10" to null),
            Triple(suspend { client.accountGymGames("t", player = "others") }, "GET",
                "/api/v1/account/gym/games/?page=1&page_size=20&player=others" to null),
            Triple(suspend { client.accountGymNewGame("morphy", "t") }, "POST",
                newGame to "{ \"color\": \"white\", \"time\": \"unlimited\" }"),
            Triple(suspend { client.accountGymNewGame("morphy", "t", "random", "3+0") }, "POST",
                newGame to "{ \"color\": \"random\", \"time\": \"3+0\" }"),
            Triple(suspend { client.accountGymNewGame("morphy", "t", time = "custom", minutes = 2, increment = 1) }, "POST",
                newGame to "{ \"color\": \"white\", \"time\": \"custom\", \"minutes\": 2, \"increment\": 1 }"),
            Triple(suspend { client.accountGymNewGame("morphy", "t", version = "v4") }, "POST",
                newGame to "{ \"color\": \"white\", \"time\": \"unlimited\", \"version\": \"v4\" }"),
            Triple(suspend { client.accountGymGame(7, "t") }, "GET", game to null),
            Triple(suspend { client.accountGymDeleteGame(7, "t") }, "DELETE", game to null),
            Triple(suspend { client.accountGymMove(7, "e7e8q", 0, "t") }, "POST",
                "${game}move/" to "{ \"move\": \"e7e8q\", \"ply\": 0 }"),
            Triple(suspend { client.accountGymBotMove(7, 1, "t") }, "POST", "${game}bot-move/" to "{ \"ply\": 1 }"),
            Triple(suspend { client.accountGymResign(7, "t") }, "POST", "${game}resign/" to ""),
            Triple(suspend { client.accountGymTakeBack(7, "t") }, "POST", "${game}takeback/" to ""),
            Triple(suspend { client.accountGymClock(7, "t") }, "POST", "${game}clock/" to ""),
            Triple(suspend { client.accountGymAbort(7, "t") }, "POST", "${game}abort/" to ""),
        )
        for ((call, method, expected) in cases) {
            server.enqueue(MockResponse().setBody("{ \"ok\": true }"))
            assertTrue(call().ok)
            val request = sent()
            assertEquals(method, request.method)
            assertEquals(expected.first, request.path)
            assertEquals("Bearer t", request.getHeader("Authorization"))
            val body = request.body.readUtf8()
            when (val json = expected.second) {
                null, "" -> assertEquals("", body)
                else -> assertEquals(Json.parseToJsonElement(json), Json.parseToJsonElement(body))
            }
        }
    }

    @Test fun gymRepliesDecodeIntoTheGeneratedModelsAndABusyEngineIsNotRetried() = runBlocking {
        server.enqueue(MockResponse().setBody(GYM_STATE))
        val state = LENIENT.decodeFromJsonElement(GymGameState.serializer(), client.accountGymGame(7, "t").data)
        assertEquals(listOf("e4"), state.sans)
        assertEquals(179_000L, state.clock!!.whiteMs)
        assertTrue(state.botToMove)
        sent()
        server.enqueue(file("1. e4 *".toByteArray(), "application/x-chess-pgn", "gym.pgn"))
        val pgn = client.accountGymGamePgn(7, "t")
        assertEquals("/api/v1/account/gym/games/7/pgn/", sent().path)
        assertEquals("gym.pgn", pgn.filename)
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "1")
            .setBody("{ \"error\": { \"code\": \"capacity_exhausted\", \"message\": \"Busy.\" } }"))
        val busy = client.accountGymBotMove(7, 1, "t")
        assertEquals(429, busy.status)
        assertEquals("1", busy.retryAfter)
        assertEquals(JsonPrimitive("capacity_exhausted"), busy.data["error"]!!.jsonObject["code"])
        sent()
        assertEquals(3, server.requestCount)
    }

    @Test fun gymHelpersRejectInvalidArgumentsBeforeSending() = runBlocking {
        val invalid = listOf<suspend () -> Unit>(
            { client.accountGym("t", 101) }, { client.accountGymGames("t", player = "everyone") },
            { client.accountGymGames("t", 0) }, { client.accountGymNewGame("../me", "t") },
            { client.accountGymNewGame("morphy", "t", "green") }, { client.accountGymNewGame("morphy", "t", time = "") },
            { client.accountGymNewGame("morphy", "t", time = "custom", minutes = 0, increment = 0) },
            { client.accountGymNewGame("morphy", "t", time = "custom", minutes = 3) },
            { client.accountGymNewGame("morphy", "t", time = "3+0", minutes = 3) },
            { client.accountGymNewGame("morphy", "t", version = " ") },
            { client.accountGymGame(0, "t") }, { client.accountGymMove(7, "e2-e4", 0, "t") },
            { client.accountGymMove(7, "e2e4", -1, "t") }, { client.accountGymBotMove(7, -1, "t") },
            { client.accountGymAbort(-3, "t") },
        )
        for (call in invalid) assertFailsWith<ApiException> { call() }
        assertEquals(0, server.requestCount)
    }

    @Test fun puzzleCallsUseExactMethodsPathsAndBodies() = runBlocking {
        val base = "/api/v1/account/puzzles/"
        val cases = listOf<Triple<suspend () -> ApplicationResponse, String, Pair<String, String?>>>(
            Triple(suspend { client.accountPuzzles("t") }, "GET", base to null),
            Triple(suspend { client.accountPuzzleNext("t") }, "GET", "${base}next/" to null),
            Triple(suspend { client.accountPuzzleNext("t", "p-000ccc1f") }, "GET", "${base}next/?after=p-000ccc1f" to null),
            Triple(suspend { client.accountPuzzle("p-000ccc1f", "t") }, "GET", "${base}p-000ccc1f/" to null),
            Triple(suspend { client.accountPuzzleResult("p-000ccc1f", "missed", "t") }, "POST",
                "${base}p-000ccc1f/result/" to "{ \"result\": \"missed\" }"),
            Triple(suspend { client.accountPuzzleHistory("t", 2, 10) }, "GET", "${base}history/?page=2&page_size=10" to null),
            Triple(suspend { client.accountPuzzleReset("t") }, "POST", "${base}reset/" to ""),
        )
        for ((call, method, expected) in cases) {
            server.enqueue(MockResponse().setBody("{ \"ok\": true }"))
            assertTrue(call().ok)
            val request = sent()
            assertEquals(method, request.method)
            assertEquals(expected.first, request.path)
            assertEquals("Bearer t", request.getHeader("Authorization"))
            val body = request.body.readUtf8()
            when (val json = expected.second) {
                null, "" -> assertEquals("", body)
                else -> assertEquals(Json.parseToJsonElement(json), Json.parseToJsonElement(body))
            }
        }
    }

    @Test fun puzzleRepliesDecodeIntoTheGeneratedModels() = runBlocking<Unit> {
        server.enqueue(MockResponse().setBody(PUZZLE))
        val puzzle = LENIENT.decodeFromJsonElement(Puzzle.serializer(), client.accountPuzzle("p-000ccc1f", "t").data)
        assertEquals(Puzzle.Solver.WHITE, puzzle.solver)
        assertEquals(listOf("Qxf7#"), puzzle.solution.mainSan)
        assertEquals(listOf("h5f7"), puzzle.solution.moves.filter { it.after.isEmpty() }.map { it.move })
        assertEquals(null, puzzle.game)
        sent()
    }

    @Test fun puzzleHelpersRejectInvalidArgumentsBeforeSending() = runBlocking {
        val invalid = listOf<suspend () -> Unit>(
            { client.accountPuzzle("../me", "t") }, { client.accountPuzzle("p-000CCC1F", "t") },
            { client.accountPuzzleNext("t", "p-1") }, { client.accountPuzzleResult("p-000ccc1f", "skipped", "t") },
            { client.accountPuzzleResult("p-000ccc1f/..", "solved", "t") },
            { client.accountPuzzleHistory("t", 0) }, { client.accountPuzzleHistory("t", 1, 101) },
        )
        for (call in invalid) assertFailsWith<ApiException> { call() }
        assertEquals(0, server.requestCount)
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

private val LENIENT = Json { ignoreUnknownKeys = true }

private const val GYM_STATE = """{"id": 7, "bot": "morphy", "bot_name": "Paul Morphy", "player": "someone", "is_player": true,
 "moves": ["e2e4"], "sans": ["e4"], "fen": "rnbqkbnr/pppppppp/8/8/4P3/8/PPPP1PPP/RNBQKBNR b KQkq - 0 1", "ply": 1,
 "human_color": "white", "result": "*", "finished": false, "termination": "", "termination_label": "", "takebacks": 0,
 "can_abort": false, "clock": {"initial_ms": 180000, "increment_ms": 0, "white_ms": 179000, "black_ms": 180000,
 "running": "black"}, "bot_to_move": true, "opening": "King's Pawn Game", "time_control": "3+0",
 "created_at": "2026-09-25T12:00:00+00:00", "urls": {"api": "/api/v1/account/gym/games/7/"}, "extra": 1}"""

private const val PUZZLE = """{"id": "p-000ccc1f", "heading": "Puzzle from Attacker, A vs Defender, D, 1910",
  "prompt": "Find the best move for White.", "solver": "white", "white": "Attacker, A", "black": "Defender, D",
  "event": "Club Match", "year": 1910, "goal": "mate", "goal_label": "Checkmate", "mate_in": 1,
  "start_fen": "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1",
  "lead": ["e2e4", "e7e5", "f1c4", "b8c6", "d1h5", "g8f6"],
  "fen": "r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 4 4",
  "solution": {"main": ["h5f7"], "main_san": ["Qxf7#"],
    "moves": [{"after": [], "move": "h5f7", "reply": null, "from_game": true}]},
  "info_rows": [{"label": "Event", "value": "Club Match"}], "game": null,
  "urls": {"api": "/api/v1/account/puzzles/p-000ccc1f/", "result": "/api/v1/account/puzzles/p-000ccc1f/result/",
    "next": "/api/v1/account/puzzles/next/?after=p-000ccc1f", "html": "/puzzles/p-000ccc1f/"}}"""
