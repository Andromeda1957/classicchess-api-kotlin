// Generated from OpenAPI by scripts/generate_models.py. Do not edit.
package com.classicchess.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class MasterGame(
    val `token`: String,
    val `white`: String,
    val `black`: String,
    @SerialName("display_white") val `displayWhite`: String,
    @SerialName("display_black") val `displayBlack`: String,
    val `result`: String,
    val `event`: String,
    val `site`: String,
    val `date`: String,
    @SerialName("played_on") val `playedOn`: String?,
    val `year`: Long?,
    val `round`: String,
    @SerialName("white_elo") val `whiteElo`: Long?,
    @SerialName("black_elo") val `blackElo`: Long?,
    val `eco`: String,
    val `opening`: String,
    @SerialName("ply_count") val `plyCount`: Long?,
    @SerialName("move_count") val `moveCount`: Long?,
    @SerialName("source_count") val `sourceCount`: Long,
    val `urls`: ResourceLinks,
)

@Serializable
data class MasterGameDetail(
    val `token`: String,
    val `white`: String,
    val `black`: String,
    @SerialName("display_white") val `displayWhite`: String,
    @SerialName("display_black") val `displayBlack`: String,
    val `result`: String,
    val `event`: String,
    val `site`: String,
    val `date`: String,
    @SerialName("played_on") val `playedOn`: String?,
    val `year`: Long?,
    val `round`: String,
    @SerialName("white_elo") val `whiteElo`: Long?,
    @SerialName("black_elo") val `blackElo`: Long?,
    val `eco`: String,
    val `opening`: String,
    @SerialName("ply_count") val `plyCount`: Long?,
    @SerialName("move_count") val `moveCount`: Long?,
    @SerialName("source_count") val `sourceCount`: Long,
    val `urls`: ResourceLinks,
    val `pgn`: String,
    val `mainline`: JsonObject,
)

@Serializable
data class MasterGamePage(
    val `query`: String,
    val `count`: Long,
    val `page`: Long,
    @SerialName("page_size") val `pageSize`: Long,
    @SerialName("page_count") val `pageCount`: Long,
    @SerialName("count_is_exact") val `countIsExact`: Boolean,
    @SerialName("result_limit") val `resultLimit`: Long,
    @SerialName("hit_result_limit") val `hitResultLimit`: Boolean,
    val `next`: String?,
    val `previous`: String?,
    val `message`: String?,
    val `hint`: String,
    val `results`: List<MasterGame>,
)

@Serializable
data class ApiError(
    val `error`: ApiErrorError,
)

@Serializable
data class ApiDiscovery(
    val `name`: String,
    val `version`: String,
    val `authentication`: JsonObject,
    val `documentation`: ResourceLinks,
    val `collections`: Map<String, ApiDiscoveryCollectionsValue>,
    @SerialName("player_resources") val `playerResources`: JsonObject,
    val `tools`: ResourceLinks,
    val `pagination`: JsonObject,
)

typealias ResourceLinks = Map<String, String?>

@Serializable
data class PlayerIdentity(
    val `name`: String,
    val `slug`: String,
)

@Serializable
data class Biography(
    val `epithet`: String,
    val `lifespan`: String,
    val `crown`: String,
    val `lede`: String,
    val `vitals`: List<BiographyVitalsItem>,
    val `sections`: List<BiographySectionsItem>,
    val `numbers`: List<JsonObject>,
    @SerialName("quotes_by") val `quotesBy`: List<BiographyQuote>,
    @SerialName("quotes_about") val `quotesAbout`: List<BiographyQuote>,
    val `legacy`: String,
    val `sources`: List<BiographySourcesItem>,
)

@Serializable
data class BiographyQuote(
    val `text`: String? = null,
    val `source`: String? = null,
)

@Serializable
data class PublicPlayer(
    val `name`: String,
    val `slug`: String,
    @SerialName("archive_bucket") val `archiveBucket`: PublicPlayerArchiveBucket,
    @SerialName("game_count") val `gameCount`: Long,
    @SerialName("archive_years") val `archiveYears`: String,
    val `bookmarked`: Boolean,
    val `criteria`: String,
    val `portrait`: PublicPlayerPortrait,
    val `bio`: Biography?,
    val `urls`: Map<String, String?>,
)

@Serializable
data class PublicPlayers(
    val `source`: String,
    val `query`: String,
    @SerialName("active_bucket") val `activeBucket`: String,
    @SerialName("archive_buckets") val `archiveBuckets`: List<JsonObject>,
    val `count`: Long,
    val `results`: List<PublicPlayer>,
)

@Serializable
data class PublicPlayerDetail(
    val `source`: String,
    val `player`: PublicPlayer,
)

@Serializable
data class PublicPlayerBiography(
    val `source`: String,
    val `player`: PlayerIdentity,
    val `bio`: Biography?,
)

@Serializable
data class PublicGame(
    val `token`: String,
    val `player`: PlayerIdentity?,
    val `white`: String,
    val `black`: String,
    @SerialName("display_white") val `displayWhite`: String? = null,
    @SerialName("display_black") val `displayBlack`: String? = null,
    val `result`: String,
    val `event`: String,
    val `site`: String? = null,
    val `date`: String,
    @SerialName("played_on") val `playedOn`: String? = null,
    val `round`: String? = null,
    val `eco`: String? = null,
    val `opening`: String? = null,
    @SerialName("event_round") val `eventRound`: String? = null,
    @SerialName("move_count") val `moveCount`: Long? = null,
    @SerialName("white_elo") val `whiteElo`: Long? = null,
    @SerialName("black_elo") val `blackElo`: Long? = null,
    val `urls`: ResourceLinks,
    val `pgn`: String? = null,
    val `mainline`: JsonObject? = null,
    val `memberships`: List<JsonObject>? = null,
)

@Serializable
data class PublicGamePage(
    val `source`: String,
    val `query`: String? = null,
    @SerialName("archive_player") val `archivePlayer`: String? = null,
    @SerialName("archive_event") val `archiveEvent`: String? = null,
    val `since`: Long? = null,
    val `until`: Long? = null,
    val `sort`: String? = null,
    val `count`: Long,
    val `page`: Long,
    @SerialName("page_size") val `pageSize`: Long,
    @SerialName("page_count") val `pageCount`: Long,
    val `next`: String?,
    val `previous`: String?,
    val `results`: List<PublicGame>,
)

@Serializable
data class PublicNotableGames(
    val `source`: String,
    val `player`: PlayerIdentity,
    val `count`: Long,
    val `results`: List<PublicNotableGamesResultsItem>,
)

@Serializable
data class PublicEventIndex(
    val `query`: String,
    @SerialName("series_slug") val `seriesSlug`: String? = null,
    @SerialName("series_cards") val `seriesCards`: List<PublicEventIndexSeriesCardsItem>? = null,
    val `events`: List<PublicEventIndexEventsItem>? = null,
)

@Serializable
data class PublicEvents(
    val `source`: String,
    val `query`: String,
    val `count`: Long,
    val `results`: List<PublicEventsResultsItem>,
)

@Serializable
data class AnnotatedBooks(
    val `source`: String,
    val `count`: Long,
    val `results`: List<AnnotatedBooksResultsItem>,
)

@Serializable
data class AnnotatedGame(
    val `token`: String,
    val `player`: PlayerIdentity?,
    val `white`: String,
    val `black`: String,
    @SerialName("display_white") val `displayWhite`: String? = null,
    @SerialName("display_black") val `displayBlack`: String? = null,
    val `result`: String,
    val `event`: String,
    val `site`: String? = null,
    val `date`: String,
    @SerialName("played_on") val `playedOn`: String? = null,
    val `round`: String? = null,
    val `eco`: String? = null,
    val `opening`: String? = null,
    @SerialName("event_round") val `eventRound`: String? = null,
    @SerialName("move_count") val `moveCount`: Long? = null,
    @SerialName("white_elo") val `whiteElo`: Long? = null,
    @SerialName("black_elo") val `blackElo`: Long? = null,
    val `urls`: ResourceLinks,
    val `pgn`: String? = null,
    val `mainline`: JsonObject? = null,
    val `memberships`: List<JsonObject>? = null,
    val `book`: AnnotatedGameBook,
    val `annotation`: AnnotatedGameAnnotation,
)

@Serializable
data class AnnotatedGamePage(
    val `source`: String,
    val `book`: JsonObject,
    val `count`: Long,
    val `page`: Long,
    @SerialName("page_size") val `pageSize`: Long,
    @SerialName("page_count") val `pageCount`: Long,
    val `next`: String?,
    val `previous`: String?,
    val `results`: List<AnnotatedGame>,
)

@Serializable
data class ApiErrorError(
    val `code`: String,
    val `message`: String,
)

@Serializable
data class ApiDiscoveryCollectionsValue(
    val `url`: String,
    @SerialName("text_url") val `textUrl`: String? = null,
    val `description`: String,
    val `paginated`: Boolean,
)

@Serializable
data class BiographyVitalsItem(
    val `label`: String? = null,
    val `value`: String? = null,
)

@Serializable
data class BiographySectionsItem(
    val `heading`: String? = null,
    val `body`: List<String>? = null,
)

@Serializable
data class BiographySourcesItem(
    val `label`: String? = null,
    val `url`: String? = null,
)

@Serializable
data class PublicPlayerArchiveBucket(
    val `key`: String? = null,
    val `label`: String? = null,
)

@Serializable
data class PublicPlayerPortrait(
    val `image`: String? = null,
    val `thumbnail`: String? = null,
)

@Serializable
data class PublicNotableGamesResultsItem(
    val `position`: Long,
    val `title`: String,
    val `annotation`: String,
    val `game`: PublicGame,
)

@Serializable
data class PublicEventIndexSeriesCardsItem(
    val `name`: String,
    val `slug`: String,
    val `eyebrow`: String,
    val `description`: String,
    @SerialName("year_span") val `yearSpan`: String,
    @SerialName("year_from") val `yearFrom`: Long?,
    @SerialName("year_to") val `yearTo`: Long?,
    @SerialName("event_count") val `eventCount`: Long,
    @SerialName("game_count") val `gameCount`: Long,
)

@Serializable
data class PublicEventIndexEventsItem(
    val `name`: String,
    val `slug`: String,
    @SerialName("event_type") val `eventType`: String,
    @SerialName("event_type_label") val `eventTypeLabel`: String,
    val `year`: Long?,
    @SerialName("game_count") val `gameCount`: Long,
    @SerialName("expected_game_count") val `expectedGameCount`: Long,
    val `series`: JsonObject,
)

@Serializable
data class PublicEventsResultsItem(
    val `name`: String,
    val `slug`: String,
    val `year`: Long,
    @SerialName("game_count") val `gameCount`: Long,
    @SerialName("expected_game_count") val `expectedGameCount`: Long,
    @SerialName("event_type") val `eventType`: JsonObject,
    val `series`: JsonObject?,
    val `source`: JsonObject,
    val `urls`: ResourceLinks,
)

@Serializable
data class AnnotatedBooksResultsItem(
    val `slug`: String,
    val `label`: String,
    @SerialName("source_url") val `sourceUrl`: String,
    @SerialName("game_count") val `gameCount`: Long,
    @SerialName("cover_image") val `coverImage`: String,
    val `urls`: ResourceLinks,
    val `download`: JsonObject?,
)

@Serializable
data class AnnotatedGameBook(
    val `slug`: String,
    val `label`: String,
    @SerialName("source_url") val `sourceUrl`: String,
)

@Serializable
data class AnnotatedGameAnnotation(
    @SerialName("source_label") val `sourceLabel`: String,
    @SerialName("source_url") val `sourceUrl`: String,
    val `license`: String,
    @SerialName("license_name") val `licenseName`: String,
    @SerialName("is_public_domain") val `isPublicDomain`: Boolean,
    @SerialName("note_count") val `noteCount`: Long,
    val `intro`: String? = null,
    val `notes`: List<JsonObject>? = null,
)
