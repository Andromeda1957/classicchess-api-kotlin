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
import okhttp3.HttpUrl

data class PageOptions(val page: Int? = null, val pageSize: Int? = null)
data class MasterGameFilters(val query: String, val page: Int? = null, val pageSize: Int? = null)
data class MasterStatsFilters(val query: String? = null, val player: String? = null, val opponent: String? = null, val mode: String? = null)
data class ExplorerFilters(val fen: String? = null, val play: String? = null, val moves: Int = 12,
    val topGames: Int? = null, val sourceType: String? = null, val sourceKey: String? = null)
data class PublicGameFilters(
    val query: String? = null, val archivePlayer: String? = null, val archiveEvent: String? = null,
    val since: Int? = null, val until: Int? = null, val sort: String? = null,
    val page: Int? = null, val pageSize: Int? = null,
)
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
    return pageQuery(PageOptions(filters.page, filters.pageSize)) + mapOf(
        "q" to filters.query, "archive_player" to filters.archivePlayer, "archive_event" to filters.archiveEvent,
        "since" to filters.since, "until" to filters.until, "sort" to filters.sort,
    )
}

private fun masterQuery(filters: MasterGameFilters): Map<String, Any?> {
    val query = filters.query
    if (query.isBlank() || query.length > 240 || query.codePointCount(0, query.length) > 120) {
        throw ApiException("Use a nonempty MasterDB query of at most 120 characters.", "invalid_query")
    }
    return pageQuery(PageOptions(filters.page, filters.pageSize)) + ("q" to query)
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
            "moves" to filters.moves, "topGames" to filters.topGames, "source_type" to filters.sourceType, "source_key" to filters.sourceKey))
    suspend fun explorerSources(): JsonObject = read("/api/v1/opening-explorer/sources/", JsonObject.serializer())
    suspend fun exportMasterGames(query: String? = null, tokens: List<String>? = null,
        format: String = "pgn", pgnInJson: Boolean = true): String {
        if (tokens.isNullOrEmpty() && query.isNullOrEmpty() || tokens != null && tokens.size !in 1..300) {
            throw ApiException("Use a query or between 1 and 300 game tokens.", "invalid_export")
        }
        return pgn("/api/v1/games/export/", mapOf("q" to if (tokens.isNullOrEmpty()) query else null,
            "tokens" to tokens?.joinToString(",") { masterToken(it) }, "format" to format,
            "pgnInJson" to if (format == "ndjson") pgnInJson else null))
    }
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
    suspend fun playerNames(query: String? = null): List<String> = publicPlayers(query).results.map { it.name }
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
    suspend fun tablebase(fen: String): TablebaseProbe {
        if (fen.isBlank() || fen.toByteArray(Charsets.UTF_8).size > 200) throw ApiException("Use a FEN of at most 200 bytes.", "invalid_query")
        return read("/api/v1/tablebase/", TablebaseProbe.serializer(), mapOf("fen" to fen))
    }
    suspend fun annotatedBooks(): AnnotatedBooks = read("/api/v1/annotated/books/", AnnotatedBooks.serializer())
    suspend fun bookTitles(): List<String> = annotatedBooks().results.map { it.label }
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
    suspend fun annotatedGames(bookSlug: String, page: PageOptions = PageOptions()): AnnotatedGamePage =
        read("/api/v1/annotated/books/${segment(bookSlug)}/games/", AnnotatedGamePage.serializer(), pageQuery(page))
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
    fun iterateAnnotatedGames(bookSlug: String, page: PageOptions = PageOptions(), options: IterationOptions = IterationOptions()): Flow<AnnotatedGame> =
        iterate(transport.url("/api/v1/annotated/books/${segment(bookSlug)}/games/", pageQuery(page)), options) { url ->
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
    private suspend fun <T> decode(url: HttpUrl, serializer: DeserializationStrategy<T>): T {
        val text = transport.read(url, "application/json")
        return withContext(Dispatchers.Default) {
            try { json.decodeFromString(serializer, text) }
            catch (error: SerializationException) { throw ApiException("Response does not match the API schema.", "invalid_response", cause = error) }
        }
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
