// Generated from OpenAPI by scripts/generate_models.py. Do not edit.
package com.classicchess.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class ChessTodayDay(
    val `date`: String? = null,
    val `month`: Long,
    val `day`: Long,
    val `timezone`: String,
    val `url`: String,
    @SerialName("featured_entry_id") val `featuredEntryId`: String?,
    val `entries`: List<ChessTodayEntry>,
)

@Serializable
data class ChessTodayEntry(
    val `id`: String,
    val `category`: String,
    val `year`: Long,
    val `text`: String,
    val `details`: List<String>,
    val `sources`: List<ChessTodayEntrySourcesItem>,
    val `related`: List<ChessTodayEntryRelatedItem>,
    @SerialName("target_url") val `targetUrl`: String,
    val `photograph`: ChessTodayEntryPhotograph?,
)

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
data class PublicEvent(
    val `name`: String,
    val `slug`: String,
    val `year`: Long?,
    @SerialName("game_count") val `gameCount`: Long,
    @SerialName("expected_game_count") val `expectedGameCount`: Long,
    @SerialName("event_type") val `eventType`: PublicEventEventType,
    val `series`: EventSeriesIdentity?,
    val `source`: JsonObject,
    val `urls`: ResourceLinks,
)

@Serializable
data class EventSeriesIdentity(
    val `name`: String,
    val `slug`: String,
)

@Serializable
data class PublicEventDetail(
    val `source`: String,
    val `event`: PublicEvent,
)

@Serializable
data class LabelValue(
    val `label`: String,
    val `value`: String,
)

@Serializable
data class EventBiography(
    val `tagline`: String,
    val `lede`: String,
    val `vitals`: List<JsonObject>,
    val `sections`: List<JsonObject>,
    val `numbers`: List<JsonObject>,
    val `quotes`: List<JsonObject>,
    val `images`: List<EventBiographyImagesItem>,
    val `sources`: List<JsonObject>,
)

@Serializable
data class PublicEventAbout(
    val `source`: String,
    val `event`: PublicEvent,
    val `dates`: String,
    val `location`: String,
    @SerialName("auto_vitals") val `autoVitals`: List<LabelValue>,
    val `bio`: EventBiography?,
    val `crosstable`: JsonObject?,
)

@Serializable
data class GalleryPlayer(
    val `name`: String,
    val `slug`: String?,
    @SerialName("api_detail") val `apiDetail`: String?,
)

@Serializable
data class GalleryEventLink(
    val `name`: String,
    val `slug`: String,
    val `urls`: ResourceLinks,
)

@Serializable
data class GalleryPhoto(
    val `id`: String,
    val `players`: List<GalleryPlayer>,
    val `date`: String,
    @SerialName("date_detail") val `dateDetail`: String,
    @SerialName("display_date") val `displayDate`: String,
    val `year`: String,
    val `event`: String,
    @SerialName("event_link") val `eventLink`: GalleryEventLink?,
    val `location`: String,
    val `scene`: String,
    @SerialName("alt_text") val `altText`: String,
    val `image`: GalleryPhotoImage,
    val `source`: GalleryPhotoSource,
    val `attribution`: GalleryPhotoAttribution,
    val `urls`: ResourceLinks,
)

@Serializable
data class GalleryPage(
    val `source`: String,
    val `query`: String,
    @SerialName("catalog_count") val `catalogCount`: Long,
    val `count`: Long,
    val `page`: Long,
    @SerialName("page_size") val `pageSize`: Long,
    @SerialName("page_count") val `pageCount`: Long,
    @SerialName("has_next") val `hasNext`: Boolean,
    @SerialName("has_previous") val `hasPrevious`: Boolean,
    val `results`: List<GalleryPhoto>,
)

@Serializable
data class GalleryPhotoDetail(
    val `source`: String,
    val `photo`: GalleryPhoto,
)

@Serializable
data class PublicGameSummary(
    val `token`: String,
    val `slug`: String,
    val `player`: PlayerIdentity?,
    @SerialName("display_white") val `displayWhite`: String,
    @SerialName("display_black") val `displayBlack`: String,
    val `result`: String,
    val `date`: String,
    val `year`: String,
    val `event`: String,
    val `descriptor`: String,
    val `urls`: ResourceLinks,
)

@Serializable
data class PublicDailyGame(
    val `source`: String,
    val `date`: String,
    val `game`: PublicGameSummary?,
)

@Serializable
data class PublicBeginnerGames(
    val `source`: String,
    val `count`: Long,
    val `stages`: List<PublicBeginnerGamesStagesItem>,
    val `urls`: ResourceLinks,
)

@Serializable
data class SearchCounts(
    val `players`: Long,
    val `events`: Long,
    val `games`: Long,
)

@Serializable
data class SearchPlayer(
    val `name`: String,
    val `slug`: String,
    @SerialName("game_count") val `gameCount`: Long,
    @SerialName("archive_bucket") val `archiveBucket`: String,
    val `portrait`: String,
    val `urls`: ResourceLinks,
)

@Serializable
data class SearchEvent(
    val `name`: String,
    val `slug`: String,
    val `type`: String,
    val `year`: Long?,
    @SerialName("game_count") val `gameCount`: Long,
    val `series`: String,
    val `urls`: ResourceLinks,
)

@Serializable
data class SearchGame(
    val `slug`: String,
    val `white`: String,
    val `black`: String,
    val `result`: String,
    val `date`: String,
    val `event`: String,
    val `opening`: String,
    val `url`: String,
    val `memberships`: List<JsonObject>,
    val `urls`: ResourceLinks,
)

@Serializable
data class SiteSearchPreview(
    val `source`: String,
    val `mode`: String,
    val `query`: String,
    val `intent`: String,
    val `counts`: SearchCounts,
    val `players`: List<SearchPlayer>,
    val `events`: List<SearchEvent>,
    val `games`: List<SearchGame>,
    @SerialName("html_urls") val `htmlUrls`: ResourceLinks,
)

@Serializable
data class SiteSearchPage(
    val `source`: String,
    val `mode`: String,
    val `query`: String,
    val `intent`: String,
    val `kind`: String,
    val `counts`: SearchCounts,
    val `count`: Long,
    val `page`: Long,
    @SerialName("page_size") val `pageSize`: Long,
    @SerialName("page_count") val `pageCount`: Long,
    @SerialName("has_next") val `hasNext`: Boolean,
    @SerialName("has_previous") val `hasPrevious`: Boolean,
    val `results`: List<JsonObject>,
    @SerialName("html_urls") val `htmlUrls`: ResourceLinks,
)

@Serializable
data class TablebaseMove(
    val `uci`: String,
    val `san`: String,
    val `wdl`: String,
    val `zeroing`: Boolean,
    val `dtz`: Long? = null,
)

@Serializable
data class TablebaseProbe(
    val `available`: Boolean,
    val `covered`: Boolean,
    val `coverage`: String,
    val `set`: String,
    val `maximumPieces`: Long,
    val `positionPieces`: Long? = null,
    val `sourceLabel`: String? = null,
    val `wdl`: String? = null,
    val `dtz`: Long? = null,
    val `moves`: List<TablebaseMove>,
    val `message`: String? = null,
)

@Serializable
data class AccountGame(
    val `token`: String,
    val `white`: String,
    val `black`: String,
    @SerialName("display_white") val `displayWhite`: String,
    @SerialName("display_black") val `displayBlack`: String,
    val `result`: String,
    val `event`: String,
    val `site`: String? = null,
    val `date`: String,
    @SerialName("played_on") val `playedOn`: String? = null,
    val `round`: String? = null,
    @SerialName("white_elo") val `whiteElo`: Long? = null,
    @SerialName("black_elo") val `blackElo`: Long? = null,
    val `eco`: String? = null,
    val `opening`: String? = null,
    @SerialName("move_count") val `moveCount`: Long? = null,
    @SerialName("imported_visibility") val `importedVisibility`: String,
    @SerialName("imported_at") val `importedAt`: String? = null,
    val `urls`: ResourceLinks,
)

@Serializable
data class AccountCollectionItem(
    val `token`: String,
    val `white`: String,
    val `black`: String,
    @SerialName("display_white") val `displayWhite`: String,
    @SerialName("display_black") val `displayBlack`: String,
    val `result`: String,
    val `event`: String,
    val `site`: String? = null,
    val `date`: String,
    @SerialName("played_on") val `playedOn`: String? = null,
    val `round`: String? = null,
    @SerialName("white_elo") val `whiteElo`: Long? = null,
    @SerialName("black_elo") val `blackElo`: Long? = null,
    val `eco`: String? = null,
    val `opening`: String? = null,
    @SerialName("move_count") val `moveCount`: Long? = null,
    @SerialName("imported_visibility") val `importedVisibility`: String,
    @SerialName("imported_at") val `importedAt`: String? = null,
    val `urls`: ResourceLinks,
    @SerialName("collection_item_id") val `collectionItemId`: Long,
    @SerialName("added_at") val `addedAt`: String?,
    val `changed`: Boolean,
    val `message`: String,
)

@Serializable
data class AccountStarredGame(
    val `token`: String,
    val `white`: String,
    val `black`: String,
    @SerialName("display_white") val `displayWhite`: String,
    @SerialName("display_black") val `displayBlack`: String,
    val `result`: String,
    val `event`: String,
    val `site`: String? = null,
    val `date`: String,
    @SerialName("played_on") val `playedOn`: String? = null,
    val `round`: String? = null,
    @SerialName("white_elo") val `whiteElo`: Long? = null,
    @SerialName("black_elo") val `blackElo`: Long? = null,
    val `eco`: String? = null,
    val `opening`: String? = null,
    @SerialName("move_count") val `moveCount`: Long? = null,
    @SerialName("imported_visibility") val `importedVisibility`: String,
    @SerialName("imported_at") val `importedAt`: String? = null,
    val `urls`: ResourceLinks,
    @SerialName("starred_at") val `starredAt`: String,
)

@Serializable
data class AccountStarredPlayer(
    val `name`: String,
    val `slug`: String,
    @SerialName("game_count") val `gameCount`: Long,
    @SerialName("starred_at") val `starredAt`: String,
    val `urls`: ResourceLinks,
)

@Serializable
data class AccountStarredGames(
    val `count`: Long,
    val `page`: Long,
    @SerialName("page_size") val `pageSize`: Long,
    @SerialName("page_count") val `pageCount`: Long,
    val `results`: List<AccountStarredGame>,
)

@Serializable
data class AccountStarredPlayers(
    val `count`: Long,
    val `page`: Long,
    @SerialName("page_size") val `pageSize`: Long,
    @SerialName("page_count") val `pageCount`: Long,
    val `results`: List<AccountStarredPlayer>,
)

@Serializable
data class AccountStarState(
    val `slug`: String,
    val `starred`: Boolean,
    val `changed`: Boolean,
    val `message`: String,
)

@Serializable
data class PublicImportedGame(
    val `token`: String,
    val `username`: String,
    val `white`: String,
    val `black`: String,
    @SerialName("display_white") val `displayWhite`: String,
    @SerialName("display_black") val `displayBlack`: String,
    val `result`: String,
    val `event`: String,
    val `site`: String,
    val `date`: String,
    val `round`: String,
    @SerialName("white_elo") val `whiteElo`: Long?,
    @SerialName("black_elo") val `blackElo`: Long?,
    val `eco`: String,
    val `opening`: String,
    @SerialName("move_count") val `moveCount`: Long?,
    @SerialName("imported_at") val `importedAt`: String?,
    val `pgn`: String,
    val `mainline`: JsonObject,
    val `urls`: ResourceLinks,
)

@Serializable
data class AccountNotification(
    val `id`: Long,
    val `topic`: String,
    @SerialName("topic_label") val `topicLabel`: String,
    val `title`: String,
    val `body`: String,
    val `url`: String,
    @SerialName("published_at") val `publishedAt`: String,
    val `read`: Boolean,
    val `urls`: ResourceLinks,
)

@Serializable
data class AccountNotificationPage(
    @SerialName("unread_count") val `unreadCount`: Long,
    @SerialName("sound_enabled") val `soundEnabled`: Boolean,
    val `count`: Long,
    val `page`: Long,
    @SerialName("page_size") val `pageSize`: Long,
    @SerialName("page_count") val `pageCount`: Long,
    val `results`: List<AccountNotification>,
)

@Serializable
data class AccountNotificationChange(
    val `ok`: Boolean,
    val `id`: Long? = null,
    val `marked`: Long? = null,
    @SerialName("unread_count") val `unreadCount`: Long,
)

@Serializable
data class AccountNotificationTopic(
    val `key`: String,
    val `label`: String,
    val `description`: String,
    val `enabled`: Boolean,
    val `default`: Boolean,
)

@Serializable
data class AccountNotificationPreferences(
    @SerialName("sound_enabled") val `soundEnabled`: Boolean,
    val `topics`: List<AccountNotificationTopic>,
)

@Serializable
data class AccountNotebook(
    val `uuid`: String,
    val `title`: String,
    val `description`: String,
    val `visibility`: String,
    @SerialName("allow_public_exports") val `allowPublicExports`: Boolean,
    val `role`: String?,
    @SerialName("can_export") val `canExport`: Boolean,
    @SerialName("chapter_count") val `chapterCount`: Long,
    @SerialName("updated_at") val `updatedAt`: String,
    val `urls`: ResourceLinks,
)

@Serializable
data class AccountNotebooks(
    val `count`: Long,
    val `results`: List<AccountNotebook>,
)

@Serializable
data class AccountNotebookDetail(
    val `notebook`: AccountNotebook,
    val `sections`: List<AccountNotebookDetailSectionsItem>,
    val `chapters`: List<AccountNotebookDetailChaptersItem>,
)

@Serializable
data class ChessTodayEntrySourcesItem(
    val `id`: String,
    val `url`: String,
    val `title`: String,
    val `publisher`: String,
)

@Serializable
data class ChessTodayEntryRelatedItem(
    val `kind`: String,
    val `identifier`: String,
    val `label`: String,
    val `url`: String?,
)

@Serializable
data class ChessTodayEntryPhotograph(
    @SerialName("image_url") val `imageUrl`: String,
    @SerialName("gallery_url") val `galleryUrl`: String,
    @SerialName("alt_text") val `altText`: String,
    val `credit`: String,
    val `artist`: String,
    @SerialName("source_name") val `sourceName`: String,
    @SerialName("source_url") val `sourceUrl`: String,
    val `license`: String,
    @SerialName("license_url") val `licenseUrl`: String,
    val `width`: Long,
    val `height`: Long,
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

@Serializable
data class PublicEventEventType(
    val `key`: String,
    val `label`: String,
)

@Serializable
data class EventBiographyImagesItem(
    val `url`: String,
    val `caption`: String,
    val `credit`: String,
)

@Serializable
data class GalleryPhotoImage(
    val `url`: String,
    val `width`: Long,
    val `height`: Long,
    val `bytes`: Long,
)

@Serializable
data class GalleryPhotoSource(
    val `kind`: String,
    val `id`: String,
    val `name`: String,
    val `title`: String,
    val `reference`: String,
    val `url`: String,
    @SerialName("original_url") val `originalUrl`: String,
    val `width`: Long,
    val `height`: Long,
    val `sha1`: String,
)

@Serializable
data class GalleryPhotoAttribution(
    val `artist`: String,
    val `credit`: String,
    val `license`: String,
    @SerialName("license_url") val `licenseUrl`: String,
    @SerialName("usage_terms") val `usageTerms`: String,
    val `required`: Boolean,
)

@Serializable
data class PublicBeginnerGamesStagesItem(
    val `key`: String,
    val `label`: String,
    val `entries`: List<PublicBeginnerGamesStagesItemEntriesItem>,
)

@Serializable
data class AccountNotebookDetailSectionsItem(
    val `id`: Long,
    @SerialName("parent_id") val `parentId`: Long?,
    val `title`: String,
    @SerialName("sort_order") val `sortOrder`: Long,
)

@Serializable
data class AccountNotebookDetailChaptersItem(
    val `id`: Long,
    val `title`: String,
    @SerialName("section_id") val `sectionId`: Long?,
    @SerialName("sort_order") val `sortOrder`: Long,
    @SerialName("updated_at") val `updatedAt`: String,
    val `urls`: ResourceLinks,
)

@Serializable
data class PublicBeginnerGamesStagesItemEntriesItem(
    val `step`: Long,
    @SerialName("study_focus") val `studyFocus`: String,
    val `game`: PublicGameSummary,
)
