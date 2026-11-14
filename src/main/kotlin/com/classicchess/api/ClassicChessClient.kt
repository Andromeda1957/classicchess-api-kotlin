package com.classicchess.api

import java.io.Closeable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

data class PageOptions(val page: Int? = null, val pageSize: Int? = null)
data class MasterGameFilters(
    val query: String, val page: Int? = null, val pageSize: Int? = null,
    val player: String? = null, val side: String = "either", val result: String = "any",
)
data class MasterStatsFilters(val query: String? = null, val player: String? = null, val opponent: String? = null, val mode: String? = null)
/** [since] and [until] keep games from those years when the source has year indexes. */
data class ExplorerFilters(val fen: String? = null, val play: String? = null, val moves: Int = 12,
    val topGames: Int? = null, val sourceType: String? = null, val sourceKey: String? = null,
    val since: Int? = null, val until: Int? = null)
/**
 * Lichess Explorer: no player covers the whole Lichess database; a Lichess username covers that player's games.
 * [since] counts from January of that year and [until] through December of that year.
 */
data class LichessExplorerFilters(val fen: String? = null, val player: String? = null,
    val color: String = "both", val result: String = "all", val since: Int? = null, val until: Int? = null)
/**
 * A MasterDB player's games that reach the position [fen]. [color] is the player's side and [result] the
 * player's result; a year range leaves out games of unknown year.
 */
data class PlayerExplorerFilters(val player: String, val fen: String? = null, val color: String = "both",
    val result: String = "all", val page: Int? = null, val since: Int? = null, val until: Int? = null)
private val lichessUsername = Regex("^[A-Za-z0-9_-]{2,30}$")
data class PublicGameFilters(
    val query: String? = null, val archivePlayer: String? = null, val archiveEvent: String? = null,
    val since: Int? = null, val until: Int? = null, val sort: String? = null,
    val page: Int? = null, val pageSize: Int? = null,
    /** Which fields [query] searches: "players" (players, event, site, date), "openings" (opening name, ECO) or "all" (default). */
    val searchMode: String? = null,
    /** Comma-separated theme slugs from studyThemes(); every one must match. */
    val theme: String? = null,
    /** An ECO code or prefix, such as D or D30. */
    val eco: String? = null,
    /** "1-0", "0-1", "1/2-1/2" or "*". */
    val result: String? = null,
    /** Words searched in published annotation notes; every one must match. */
    val annotationQ: String? = null,
    /** Author text in the published annotation source label. */
    val annotationAuthor: String? = null,
    /** Book title text in the published annotation source label. */
    val annotationBook: String? = null,
)
/**
 * An annotated book's game list: [query] searches the published notes, [headerQuery] the game headers
 * (players, event, site, date, opening, ECO) as the book page's search box does, and [author] the source
 * label. [sort] "asc" or "desc" orders by game date, undated games last.
 */
data class AnnotatedGameFilters(val query: String? = null, val headerQuery: String? = null,
    val author: String? = null, val sort: String? = null)
data class GalleryFilters(val query: String? = null, val page: Int? = null, val pageSize: Int? = null)
data class IterationOptions(val limit: Long? = null, val maxPages: Int = 100_000)

private fun pageQuery(options: PageOptions): Map<String, Any?> {
    if (options.page != null && options.page < 1 || options.pageSize != null && options.pageSize !in 1..100) {
        throw ApiException("page must be positive and pageSize must be 1–100.", "invalid_pagination")
    }
    return mapOf("page" to options.page, "page_size" to options.pageSize)
}
private fun gameQuery(filters: PublicGameFilters): Map<String, Any?> {
    if (filters.sort != null && filters.sort !in listOf("asc", "desc", "event")) throw ApiException("sort must be asc, desc or event.", "invalid_query")
    if (filters.sort == "event" && filters.archiveEvent.isNullOrBlank()) throw ApiException("sort=event requires archiveEvent.", "invalid_query")
    if (filters.searchMode != null && filters.searchMode !in listOf("all", "players", "openings")) {
        throw ApiException("searchMode must be all, players or openings.", "invalid_query")
    }
    if (filters.result != null && filters.result !in listOf("1-0", "0-1", "1/2-1/2", "*")) {
        throw ApiException("result must be 1-0, 0-1, 1/2-1/2 or *.", "invalid_query")
    }
    return pageQuery(PageOptions(filters.page, filters.pageSize)) + mapOf(
        "q" to filters.query, "archive_player" to filters.archivePlayer, "archive_event" to filters.archiveEvent,
        "search_mode" to filters.searchMode,
        "since" to filters.since, "until" to filters.until, "sort" to filters.sort,
        "theme" to filters.theme, "eco" to filters.eco, "result" to filters.result,
        "annotation_q" to filters.annotationQ, "annotation_author" to filters.annotationAuthor,
        "annotation_book" to filters.annotationBook,
    )
}

private fun annotatedGameQuery(page: PageOptions, filters: AnnotatedGameFilters): Map<String, Any?> {
    if (filters.sort != null && filters.sort !in listOf("asc", "desc")) throw ApiException("sort must be asc or desc.", "invalid_query")
    return pageQuery(page) + mapOf("q" to filters.query, "header_q" to filters.headerQuery,
        "author" to filters.author, "sort" to filters.sort)
}

private fun playerExplorerQuery(filters: PlayerExplorerFilters): Map<String, Any?> {
    val player = filters.player.trim()
    if (player.isEmpty() || player.length > 80 || (filters.fen?.length ?: 0) > 100) {
        throw ApiException("Use a player of 1 to 80 characters and a FEN of at most 100.", "invalid_query")
    }
    if (filters.color !in listOf("both", "white", "black") || filters.result !in listOf("all", "win", "loss", "draw")) {
        throw ApiException("color must be both, white or black; result must be all, win, loss or draw.", "invalid_query")
    }
    if (filters.page != null && filters.page < 1) throw ApiException("page must be positive.", "invalid_pagination")
    return mapOf("player" to player, "fen" to filters.fen, "color" to filters.color, "result" to filters.result,
        "page" to filters.page, "since" to filters.since, "until" to filters.until)
}

/** At most 300 export tokens, one per line: the body form keeps long batches out of the URL. */
private fun tokenBody(tokens: List<String>, check: (String) -> String): String {
    if (tokens.size !in 1..300) throw ApiException("Export between 1 and 300 tokens.", "invalid_export")
    return tokens.joinToString("\n") { check(it) }
}

private fun exportFormat(format: String, pgnInJson: Boolean): Map<String, Any?> =
    mapOf("format" to format, "pgnInJson" to if (format == "ndjson") pgnInJson else null)

private val gymShareToken = Regex("[0-9]+\\.[A-Za-z0-9_-]{1,255}")
private val lichessGameId = Regex("[A-Za-z0-9]{8}")
private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
private val TEXT_MEDIA = "text/plain; charset=utf-8".toMediaType()

private fun masterQuery(filters: MasterGameFilters): Map<String, Any?> {
    val query = filters.query
    if (query.isBlank() || query.length > 240 || query.codePointCount(0, query.length) > 120) {
        throw ApiException("Use a nonempty MasterDB query of at most 120 characters.", "invalid_query")
    }
    val player = filters.player.orEmpty().trim()
    if (player.length > 160 || player.codePointCount(0, player.length) > 80 ||
        filters.side !in listOf("either", "white", "black") ||
        filters.result !in listOf("any", "win", "draw", "loss") ||
        player.isEmpty() && (filters.side != "either" || filters.result != "any")) {
        throw ApiException("Use a player of at most 80 characters, side either/white/black and result any/win/draw/loss.", "invalid_query")
    }
    return pageQuery(PageOptions(filters.page, filters.pageSize)) + mapOf(
        "q" to query, "player" to player.takeIf { it.isNotEmpty() },
        "side" to filters.side.takeIf { player.isNotEmpty() },
        "result" to filters.result.takeIf { player.isNotEmpty() },
    )
}

private fun siteSearchQuery(query: String): String {
    if (query.isBlank() || query.length > 240 || query.codePointCount(0, query.length) > 120) {
        throw ApiException("Use a search query of 1 to 120 characters.", "invalid_query")
    }
    return query
}

private fun masterToken(token: String): String {
    if (!Regex("g[0-9a-z]{1,66}-[0-9a-f]{12}").matches(token)) {
        throw ApiException("Use an exact MasterDB game token returned by search.", "invalid_identifier")
    }
    return token
}

/** Public archive reads for Kotlin JVM and Android. Reuse a client, then close it when its owner ends. */
class ClassicChessClient(options: ClientOptions = ClientOptions()) : Closeable {
    private val transport = Transport(options)
    private val json = Json { ignoreUnknownKeys = true }
    val baseUrl: String get() = transport.base.toString().removeSuffix("/")

    suspend fun discovery(): ApiDiscovery = read("/api/v1/", ApiDiscovery.serializer())
    suspend fun players(query: String, limit: Int = 10): JsonObject =
        read("/api/v1/players/", JsonObject.serializer(), mapOf("q" to query, "limit" to limit))
    suspend fun masterStats(filters: MasterStatsFilters): JsonObject {
        if (!filters.query.isNullOrEmpty()) return read("/api/v1/stats/", JsonObject.serializer(), mapOf("q" to filters.query))
        val mode = filters.mode ?: if (!filters.opponent.isNullOrEmpty()) "head_to_head" else "summary"
        if (filters.player.isNullOrEmpty() || mode == "head_to_head" && filters.opponent.isNullOrEmpty()) {
            throw ApiException("Use a stats query or player; head_to_head requires opponent.", "invalid_query")
        }
        return read("/api/v1/stats/", JsonObject.serializer(), mapOf("player" to filters.player, "opponent" to filters.opponent, "mode" to mode))
    }
    suspend fun explorer(filters: ExplorerFilters = ExplorerFilters()): JsonObject =
        read("/api/v1/opening-explorer/", JsonObject.serializer(), mapOf("fen" to filters.fen, "play" to filters.play,
            "moves" to filters.moves, "topGames" to filters.topGames, "source_type" to filters.sourceType, "source_key" to filters.sourceKey,
            "since" to filters.since, "until" to filters.until))
    suspend fun explorerSources(): JsonObject = read("/api/v1/opening-explorer/sources/", JsonObject.serializer())
    /**
     * A MasterDB player's games at a position, one page at a time. An ambiguous or unknown player returns
     * `candidates` and no `results`. While the player's position index is first built the reply is
     * `{"pending": true, "retry_after_ms": ...}`: ask again after that delay.
     */
    suspend fun playerExplorer(filters: PlayerExplorerFilters): JsonObject =
        read("/api/v1/opening-explorer/player/", JsonObject.serializer(), playerExplorerQuery(filters))
    /** One public Lichess game by its eight-character ID, as `title` and validated `pgn`; nothing is imported. */
    suspend fun lichessExplorerGame(gameId: String): JsonObject {
        if (!lichessGameId.matches(gameId)) throw ApiException("Use an eight-character Lichess game ID.", "invalid_identifier")
        return read("/api/v1/opening-explorer/lichess/games/$gameId/", JsonObject.serializer())
    }
    /**
     * The PGN of one public Lichess or Chess.com game from its link, as the Analysis Board loads it; nothing is
     * saved. `orientation` is the side a Lichess /white or /black link names, otherwise null.
     */
    suspend fun resolveGameLink(url: String): ResolvedGameLink {
        if (url.isBlank() || url.length > 500) throw ApiException("Use a game link of at most 500 characters.", "invalid_query")
        val body = buildJsonObject { put("url", url) }.toString().toRequestBody(JSON_MEDIA)
        return parse(transport.post(transport.url("/api/v1/analysis/game-link/"), body, "application/json"), ResolvedGameLink.serializer())
    }
    /** Explore a position in the Lichess database; Classic Chess relays it with its own Lichess credential. */
    suspend fun lichessExplorer(filters: LichessExplorerFilters = LichessExplorerFilters()): JsonObject {
        val player = filters.player.orEmpty().trim()
        if ((filters.fen?.length ?: 0) > 100) throw ApiException("Use a FEN of at most 100 characters.", "invalid_query")
        if (player.isNotEmpty() && !lichessUsername.matches(player)) throw ApiException("Use a valid Lichess username.", "invalid_query")
        if (filters.color !in listOf("both", "white", "black") || filters.result !in listOf("all", "win", "loss", "draw")) {
            throw ApiException("color must be both, white or black; result must be all, win, loss or draw.", "invalid_query")
        }
        if (player.isEmpty() && filters.result != "all") throw ApiException("A Lichess username is needed for result filters.", "invalid_query")
        val fen = filters.fen?.takeIf { it.isNotEmpty() }
        val query = if (player.isEmpty()) mapOf("fen" to fen)
            else mapOf("fen" to fen, "player" to player, "color" to filters.color, "result" to filters.result)
        return read("/api/v1/opening-explorer/lichess/", JsonObject.serializer(),
            query + mapOf("since" to filters.since, "until" to filters.until))
    }
    suspend fun exportMasterGames(query: String? = null, tokens: List<String>? = null,
        format: String = "pgn", pgnInJson: Boolean = true): String {
        if (tokens.isNullOrEmpty() && query.isNullOrEmpty() || tokens != null && tokens.size !in 1..300) {
            throw ApiException("Use a query or between 1 and 300 game tokens.", "invalid_export")
        }
        return pgn("/api/v1/games/export/", mapOf("q" to if (tokens.isNullOrEmpty()) query else null,
            "tokens" to tokens?.joinToString(",") { masterToken(it) }, "format" to format,
            "pgnInJson" to if (format == "ndjson") pgnInJson else null))
    }
    /** Export 1 to 300 MasterDB game tokens sent in the request body rather than the URL. */
    suspend fun exportMasterGamesByToken(tokens: List<String>, format: String = "pgn", pgnInJson: Boolean = true): String =
        transport.post(transport.url("/api/v1/games/export/", exportFormat(format, pgnInJson)),
            tokenBody(tokens, ::masterToken).toRequestBody(TEXT_MEDIA), "application/x-chess-pgn")
    suspend fun masterGames(filters: MasterGameFilters): MasterGamePage =
        read("/api/v1/games/", MasterGamePage.serializer(), masterQuery(filters))
    suspend fun masterGame(token: String): MasterGameDetail =
        read("/api/v1/games/${masterToken(token)}/", MasterGameDetail.serializer())
    suspend fun masterPgn(token: String): String = pgn("/api/v1/games/${masterToken(token)}/pgn/")
    /** Lower-bound counts do not determine completion: follow next through the server's search cap. */
    fun iterateMasterGames(filters: MasterGameFilters, options: IterationOptions = IterationOptions()): Flow<MasterGame> =
        iterate(transport.url("/api/v1/games/", masterQuery(filters)), options) { url ->
            val page = decode(url, MasterGamePage.serializer())
            Page(page.results, page.next)
        }
    suspend fun publicPlayers(query: String? = null): PublicPlayers =
        read("/api/v1/public/players/", PublicPlayers.serializer(), mapOf("q" to query))
    /** The players catalog for lists and pickers: biography headline only, about a tenth the size. */
    suspend fun publicPlayerCards(query: String? = null): PublicPlayerCards =
        read("/api/v1/public/players/cards/", PublicPlayerCards.serializer(), mapOf("q" to query))
    suspend fun playerNames(query: String? = null): List<String> = publicPlayerCards(query).results.map { it.name }
    suspend fun publicPlayer(slug: String): PublicPlayerDetail =
        read("/api/v1/public/players/${segment(slug)}/", PublicPlayerDetail.serializer())
    suspend fun publicPlayerBio(slug: String): PublicPlayerBiography =
        read("/api/v1/public/players/${segment(slug)}/bio/", PublicPlayerBiography.serializer())
    suspend fun publicNotableGames(slug: String): PublicNotableGames =
        read("/api/v1/public/players/${segment(slug)}/notable-games/", PublicNotableGames.serializer())
    suspend fun eventSeries(query: String? = null, series: String? = null): PublicEventIndex {
        if (query != null && (query.length > 240 || query.codePointCount(0, query.length) > 120) ||
            !series.isNullOrEmpty() && !Regex("[a-z0-9-]{1,160}").matches(series) || !query.isNullOrEmpty() && !series.isNullOrEmpty()) {
            throw ApiException("Use either an event query of at most 120 characters or an exact series slug.", "invalid_query")
        }
        return read("/api/v1/public/event-series/", PublicEventIndex.serializer(), mapOf("q" to query, "series" to series))
    }
    suspend fun publicEvents(query: String? = null): PublicEvents =
        read("/api/v1/public/events/", PublicEvents.serializer(), mapOf("q" to query))
    suspend fun eventNames(query: String? = null): List<String> = publicEvents(query).results.map { it.name }
    suspend fun publicEvent(slug: String): PublicEventDetail =
        read("/api/v1/public/events/${segment(slug)}/", PublicEventDetail.serializer())
    suspend fun publicEventAbout(slug: String): PublicEventAbout =
        read("/api/v1/public/events/${segment(slug)}/about/", PublicEventAbout.serializer())
    suspend fun gallery(filters: GalleryFilters = GalleryFilters()): GalleryPage =
        read("/api/v1/public/gallery/", GalleryPage.serializer(),
            pageQuery(PageOptions(filters.page, filters.pageSize)) + ("q" to filters.query))
    suspend fun galleryPhoto(photoId: String): GalleryPhotoDetail =
        read("/api/v1/public/gallery/${segment(photoId)}/", GalleryPhotoDetail.serializer())
    suspend fun beginnerGames(): PublicBeginnerGames = read("/api/v1/public/beginner-games/", PublicBeginnerGames.serializer())
    /** Today's Game of the Day; `game` is null when none can be published. */
    suspend fun dailyGame(): PublicDailyGame = read("/api/v1/public/daily/", PublicDailyGame.serializer())
    /** The homepage's Event of the day; `event` is null when none can be published. */
    suspend fun eventOfTheDay(): PublicEventOfTheDay =
        read("/api/v1/public/event-of-the-day/", PublicEventOfTheDay.serializer())
    /** Every face of today's homepage card; each of player, game, onThisDay and event is null when absent. */
    suspend fun todaysPicks(): PublicTodaysPicks =
        read("/api/v1/public/todays-picks/", PublicTodaysPicks.serializer())
    /** Today's sourced chess-history anniversaries in the site's timezone, in featured order. */
    suspend fun chessToday(): ChessTodayDay = read("/api/v1/public/chess-today/", ChessTodayDay.serializer())
    /** The chess-history anniversaries of one calendar day. */
    suspend fun chessTodayOnDate(month: Int, day: Int): ChessTodayDay {
        if (month !in 1..12 || day !in 1..31) throw ApiException("Use a month from 1 to 12 and a day from 1 to 31.", "invalid_query")
        return read("/api/v1/public/chess-today/$month/$day/", ChessTodayDay.serializer())
    }
    /** The study theme taxonomy: each theme's rule, measured precision and game count. Use slugs as PublicGameFilters.theme. */
    suspend fun studyThemes(): JsonObject = read("/api/v1/public/themes/", JsonObject.serializer())
    /** One page of What's New posts, newest first; a page past the last is a 404. */
    suspend fun siteUpdates(page: PageOptions = PageOptions()): SiteUpdatePage =
        read("/api/v1/public/site-updates/", SiteUpdatePage.serializer(), pageQuery(page))
    suspend fun siteUpdate(updateId: String): SiteUpdateDetail =
        read("/api/v1/public/site-updates/${segment(updateId)}/", SiteUpdateDetail.serializer())
    /** Every What's New post, newest first, page by page until the last. */
    fun iterateSiteUpdates(pageSize: Int? = null, options: IterationOptions = IterationOptions()): Flow<SiteUpdatePost> = flow {
        if (options.maxPages < 1 || options.limit != null && options.limit < 0) throw ApiException("Use positive maxPages and a nonnegative limit.", "invalid_pagination")
        var page = 1
        var count = 0L
        while (options.limit == null || count < options.limit) {
            currentCoroutineContext().ensureActive()
            if (page > options.maxPages) throw ApiException("Pagination exceeded maxPages.", "invalid_pagination")
            val result = siteUpdates(PageOptions(page, pageSize))
            if (result.page != page.toLong()) throw ApiException("Pagination repeated a page.", "invalid_pagination")
            for (post in result.results) {
                emit(post)
                count++
                if (options.limit != null && count >= options.limit) return@flow
            }
            if (!result.hasNext) return@flow
            page++
        }
    }
    /** A Gym game by the signed token in its share link (urls.api_share), as the link shows it to anyone. */
    suspend fun sharedGymGame(token: String): GymSharedGame {
        if (!gymShareToken.matches(token)) throw ApiException("Use the token from a Gym game's share link.", "invalid_identifier")
        return read("/api/v1/public/gym/games/$token/", GymSharedGame.serializer())
    }
    /** The site search box's grouped preview of players, events and games. */
    suspend fun siteSearch(query: String): SiteSearchPreview =
        read("/api/v1/public/search/", SiteSearchPreview.serializer(), mapOf("q" to siteSearchQuery(query)))
    /** One 50-row page of one site search result kind: games, events or players. */
    suspend fun siteSearchPage(query: String, kind: String, page: Int = 1): SiteSearchPage {
        if (kind !in listOf("games", "events", "players")) throw ApiException("kind must be games, events, or players.", "invalid_query")
        if (page < 1) throw ApiException("page must be positive.", "invalid_pagination")
        return read("/api/v1/public/search/", SiteSearchPage.serializer(),
            mapOf("q" to siteSearchQuery(query), "kind" to kind, "page" to page))
    }
    /**
     * One 20-row page of the site's own content: its pages and their sections, player biographies, event
     * histories and What's New posts, best match first, each with a quoted snippet and the matched ranges.
     */
    suspend fun siteContentSearch(query: String, page: Int = 1): SiteContentSearchPage {
        if (page < 1) throw ApiException("page must be positive.", "invalid_pagination")
        return read("/api/v1/public/search/", SiteContentSearchPage.serializer(),
            mapOf("q" to siteSearchQuery(query), "kind" to "site", "page" to page))
    }
    suspend fun tablebase(fen: String): TablebaseProbe {
        if (fen.isBlank() || fen.toByteArray(Charsets.UTF_8).size > 200) throw ApiException("Use a FEN of at most 200 bytes.", "invalid_query")
        return read("/api/v1/tablebase/", TablebaseProbe.serializer(), mapOf("fen" to fen))
    }
    suspend fun annotatedBooks(ccnbFormat: Int? = null): AnnotatedBooks =
        decode(transport.url("/api/v1/annotated/books/"), AnnotatedBooks.serializer(), ccnbFormat)
    suspend fun bookTitles(ccnbFormat: Int? = null): List<String> = annotatedBooks(ccnbFormat).results.map { it.label }
    /** Exact package bytes; use the same ccnbFormat as annotatedBooks for matching revisions. */
    suspend fun annotatedNotebookFile(bookSlug: String, ccnbFormat: Int? = null): ByteArray =
        transport.ccnbDownload("/annotated/${segment(bookSlug)}/download/", ccnbFormat)
    /**
     * The book as a .ccnb Notebook package from the versioned API (`urls.ccnb`): every annotated game with its
     * notes, the mapped source pages and rights metadata. ccnbFormat 3 selects the binary container; omitted is 2.
     */
    suspend fun annotatedBookCcnb(bookSlug: String, ccnbFormat: Int? = null): ByteArray =
        transport.ccnbDownload("/api/v1/annotated/books/${segment(bookSlug)}/ccnb/", ccnbFormat)
    suspend fun publicGames(filters: PublicGameFilters = PublicGameFilters()): PublicGamePage =
        read("/api/v1/public/games/", PublicGamePage.serializer(), gameQuery(filters))
    suspend fun publicGame(token: String): PublicGame =
        read("/api/v1/public/games/${gameToken(token)}/", PublicGame.serializer())
    suspend fun publicPgn(token: String): String = pgn("/api/v1/public/games/${gameToken(token)}/pgn/")

    /** A game another account imported and made public, by the username and slug in its page address. */
    suspend fun publicImportedGame(username: String, gameSlug: String): PublicImportedGame =
        read("/api/v1/public/imported-games/" + usernameSegment(username) + "/" + segment(gameSlug) + "/",
            PublicImportedGame.serializer())
    suspend fun publicImportedPgn(username: String, gameSlug: String): String =
        pgn("/api/v1/public/imported-games/" + usernameSegment(username) + "/" + segment(gameSlug) + "/pgn/")

    /** The service caps this export at 300 games. Iterate games and batch tokens to export larger archives. */
    suspend fun exportPublicGames(filters: PublicGameFilters = PublicGameFilters(), tokens: List<String>? = null,
        format: String = "pgn", pgnInJson: Boolean = true): String {
        if (tokens != null && tokens.size !in 1..300) throw ApiException("Export between 1 and 300 tokens.", "invalid_export")
        return pgn("/api/v1/public/games/export/", gameQuery(filters.copy(page = null, pageSize = null))
            + mapOf("tokens" to tokens?.joinToString(",") { gameToken(it) }, "format" to format,
                "pgnInJson" to if (format == "ndjson") pgnInJson else null))
    }
    /** Export 1 to 300 public game tokens sent in the request body rather than the URL. */
    suspend fun exportPublicGamesByToken(tokens: List<String>, format: String = "pgn", pgnInJson: Boolean = true): String =
        transport.post(transport.url("/api/v1/public/games/export/", exportFormat(format, pgnInJson)),
            tokenBody(tokens, ::gameToken).toRequestBody(TEXT_MEDIA), "application/x-chess-pgn")
    suspend fun annotatedGames(bookSlug: String, page: PageOptions = PageOptions(),
        filters: AnnotatedGameFilters = AnnotatedGameFilters()): AnnotatedGamePage =
        read("/api/v1/annotated/books/${segment(bookSlug)}/games/", AnnotatedGamePage.serializer(), annotatedGameQuery(page, filters))
    suspend fun annotatedGame(bookSlug: String, gameSlug: String): AnnotatedGame =
        read("/api/v1/annotated/books/${segment(bookSlug)}/games/${segment(gameSlug)}/", AnnotatedGame.serializer())
    suspend fun annotatedPgn(bookSlug: String, gameSlug: String? = null): String {
        val suffix = if (gameSlug == null) "pgn/" else "games/${segment(gameSlug)}/pgn/"
        return pgn("/api/v1/annotated/books/${segment(bookSlug)}/$suffix")
    }
    fun iteratePublicGames(filters: PublicGameFilters = PublicGameFilters(), options: IterationOptions = IterationOptions()): Flow<PublicGame> =
        iterate(transport.url("/api/v1/public/games/", gameQuery(filters)), options) { url ->
            val page = decode(url, PublicGamePage.serializer())
            Page(page.results, page.next)
        }
    fun iterateAnnotatedGames(bookSlug: String, page: PageOptions = PageOptions(), options: IterationOptions = IterationOptions(),
        filters: AnnotatedGameFilters = AnnotatedGameFilters()): Flow<AnnotatedGame> =
        iterate(transport.url("/api/v1/annotated/books/${segment(bookSlug)}/games/", annotatedGameQuery(page, filters)), options) { url ->
            val result = decode(url, AnnotatedGamePage.serializer())
            Page(result.results, result.next)
        }

    /** Combine returned api_pgn URLs in order, confined to this client's public API. */
    suspend fun pgnTextForGames(pgnUrls: Iterable<String>): String {
        val games = mutableListOf<String>()
        for (url in pgnUrls) games.add(transport.read(transport.safeUrl(url), "application/x-chess-pgn").trim())
        return if (games.isEmpty()) "" else games.joinToString("\n\n") + "\n"
    }

    private suspend fun <T> read(path: String, serializer: DeserializationStrategy<T>, params: Map<String, Any?> = emptyMap()): T =
        decode(transport.url(path, params), serializer)
    private suspend fun <T> decode(url: HttpUrl, serializer: DeserializationStrategy<T>, ccnbFormat: Int? = null): T =
        parse(transport.read(url, "application/json", ccnbFormat), serializer)
    private suspend fun <T> parse(text: String, serializer: DeserializationStrategy<T>): T = withContext(Dispatchers.Default) {
        try { json.decodeFromString(serializer, text) }
        catch (error: SerializationException) { throw ApiException("Response does not match the API schema.", "invalid_response", cause = error) }
    }
    private suspend fun pgn(path: String, params: Map<String, Any?> = emptyMap()): String =
        transport.read(transport.url(path, params), "application/x-chess-pgn")

    private data class Page<T>(val results: List<T>, val next: String?)
    private fun <T> iterate(first: HttpUrl, options: IterationOptions, fetch: suspend (HttpUrl) -> Page<T>): Flow<T> = flow {
        if (options.maxPages < 1 || options.limit != null && options.limit < 0) throw ApiException("Use positive maxPages and a nonnegative limit.", "invalid_pagination")
        var url = first
        var count = 0L
        val seen = mutableSetOf<HttpUrl>()
        while (options.limit == null || count < options.limit) {
            currentCoroutineContext().ensureActive()
            if (!seen.add(url) || seen.size > options.maxPages) throw ApiException("Pagination repeated a page or exceeded maxPages.", "invalid_pagination")
            val page = fetch(url)
            for (item in page.results) {
                emit(item)
                count++
                if (options.limit != null && count >= options.limit) return@flow
            }
            val next = page.next ?: return@flow
            url = transport.safeUrl(next, url)
            if (url.encodedPath != first.encodedPath) throw ApiException("Pagination changed the collection.", "unsafe_url")
        }
    }
    override fun close() = transport.close()
}
