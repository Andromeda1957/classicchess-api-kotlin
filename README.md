# Classic Chess Kotlin SDK

Use Kotlin on the JVM or Android to read chess games, player biographies, notable
games, tournaments and annotated books from [Classic Chess](https://classicchess.com/).
The SDK also searches the larger MasterDB game database, retrieves opening
statistics, exports games, and calls account and application APIs.

Public reads need no account or API key. The SDK is MIT licensed; see
[LICENSE](LICENSE). Its Gradle wrapper retains the Apache 2.0 notice in
[THIRD_PARTY_LICENSES](THIRD_PARTY_LICENSES/gradle-Apache-2.0.txt).

## Run your first example

Install [JDK 17 or newer](https://adoptium.net/) and [Git](https://git-scm.com/).
Make sure `java -version` works in your terminal, then run:

```sh
git clone https://github.com/Andromeda1957/classicchess-api-kotlin.git
cd classicchess-api-kotlin
./gradlew -q -p examples/jvm run --args="players"
```

On Windows, replace `./gradlew` with `gradlew.bat`. The included Gradle wrapper
downloads the build tool and dependencies automatically. The example builds
this SDK directly from the public checkout and prints the curated player names.
No Maven ZIP, manually copied JAR, private repository or API token is required.

Try `--args="events"`, `--args="books"` or `--args="profile"` to explore other
public data. The runnable source is [Main.kt](examples/jvm/src/main/kotlin/com/classicchess/example/Main.kt).
The first build can take several minutes while Gradle downloads dependencies.

## Add the SDK to your Kotlin project

The SDK has not been published to Maven Central yet. For an existing Kotlin
Gradle project, run this command from the project directory containing
`settings.gradle.kts`:

```sh
git clone https://github.com/Andromeda1957/classicchess-api-kotlin.git vendor/classicchess-api-kotlin
```

Add this block to that project's `settings.gradle.kts`:

```kotlin
includeBuild("vendor/classicchess-api-kotlin") {
    dependencySubstitution {
        substitute(module("com.classicchess:api-client")).using(project(":"))
    }
}
```

Add the dependency to your app module's `build.gradle.kts`, retaining its existing
plugins and repositories. Make sure Maven Central is among its repositories:

```kotlin
repositories { mavenCentral() }
dependencies { implementation("com.classicchess:api-client:0.1.0") }
```

Gradle now builds the dependency from `vendor/classicchess-api-kotlin`. This is
a Gradle composite build: the module name resolves to that source checkout.
The SDK targets JVM 8 bytecode and uses Kotlin 2.1.20, OkHttp,
kotlinx.serialization and kotlinx.coroutines. Its Android example targets
minSdk 23 with JDK 17 and Android Gradle Plugin 8.9.1.

## Read an archive

Here is a complete Kotlin program for a configured JVM application:

```kotlin
import com.classicchess.api.ClassicChessClient
import com.classicchess.api.IterationOptions
import com.classicchess.api.PublicGameFilters
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    ClassicChessClient().use { api ->
        val player = api.publicPlayers().results.firstOrNull()
        if (player == null) {
            println("No curated players available")
            return@use
        }
        println(player.name)
        val bio = api.publicPlayerBio(player.slug).bio
        println(bio?.lede ?: "Biography unavailable")
        api.iteratePublicGames(
            PublicGameFilters(archivePlayer = player.slug),
            IterationOptions(limit = 10),
        ).collect { game -> println("${game.token}: ${game.white} – ${game.black}") }
    }
}
```

`runBlocking` starts a coroutine for this small command-line program. In an
Android app, call the suspend methods from an existing lifecycle-aware coroutine
rather than blocking the UI thread. Reuse a client and close it when finished;
`use` closes it automatically in the example.

Catalogs return complete lists. Game methods return one page; `iterate*` methods
return a coroutine Flow that follows `next` until the archive is exhausted.
Cancelling collection or setting `IterationOptions(limit = ...)` stops early.
Use the exact slugs and tokens returned by the API, not names transformed into slugs.
Missing biographies are `null`, and notable lists can be empty.

## Available operations

| Capability | Kotlin methods |
| --- | --- |
| API discovery | `discovery()` |
| Player, event and book catalogs | `publicPlayers()`, `publicEvents()`, `annotatedBooks()` |
| Names and titles only | `playerNames()`, `eventNames()`, `bookTitles()` |
| Profiles, biographies and notable games | `publicPlayer(slug)`, `publicPlayerBio(slug)`, `publicNotableGames(slug)` |
| Event series | `eventSeries(query = ...)` or `eventSeries(series = ...)` |
| Public game lists, detail and PGN | `publicGames()`, `publicGame(token)`, `publicPgn(token)` |
| MasterDB game lists, detail and PGN | `masterGames(MasterGameFilters(query))`, `masterGame(token)`, `masterPgn(token)` |
| Annotated game lists, detail and PGN | `annotatedGames(bookSlug)`, `annotatedGame(bookSlug, gameSlug)`, `annotatedPgn(bookSlug, gameSlug)` |
| Streaming games | `iteratePublicGames()`, `iterateMasterGames()`, `iterateAnnotatedGames()` |
| Combine PGN text | `pgnTextForGames(pgnUrls)` combines returned `api_pgn` URLs in order |
| PGN or NDJSON exports | `exportPublicGames()`, `exportMasterGames()`; select `format = "pgn"` or `format = "ndjson"` |
| Player search and statistics | `players(query)`, `masterStats(MasterStatsFilters(...))` |
| Opening explorer | `explorer(ExplorerFilters(...))`, `explorerSources()` |
| Event detail and About page | `publicEvent(slug)`, `publicEventAbout(slug)` |
| Photo gallery | `gallery(GalleryFilters(query, page, pageSize))`, `galleryPhoto(photoId)` |
| Beginner games and Game of the Day | `beginnerGames()`, `dailyGame()` |
| Site search | `siteSearch(query)` for a preview; `siteSearchPage(query, kind, page)` for one paginated kind: `games`, `events` or `players` |
| Endgame tablebase | `tablebase(fen)` uses local Syzygy tables and falls back to the Lichess tablebase for larger positions, as the site does |
| Account collections | `ApplicationClient.accountMe(token)`, `accountCollections(token)`, `accountCreateCollection(name, token)`, `accountImportPublicPlayerGames(archivePlayer, token, ...)` |
| Add single games to collections | `ApplicationClient.accountAddCollectionGame(collectionId, gameSlug, token)` |
| Starred players and games | `accountStarredPlayers(token, page, pageSize)`, `accountStarPlayer(slug, token)`, `accountUnstarPlayer(slug, token)`, `accountStarredGames(token, ...)`, `accountStarGame(slug, token)`, `accountUnstarGame(slug, token)` |
| Change or delete imported games | `accountSetImportedGameVisibility(slug, "public" or "private", token)`, `accountDeleteImportedGame(slug, token)` |
| Public imported games | `publicImportedGame(username, gameSlug)`, `publicImportedPgn(username, gameSlug)` read a game another account imported and made public, by its page address |
| GIF exports | `ApplicationClient.masterGameGif(gameToken, token)`, `publicGameGif(slug, token)`, `annotatedGameGif(bookSlug, gameSlug, token)`, `publicImportedGameGif(username, slug, token)`, `accountImportedGameGif(slug, token)`; pass `orientation = "black"` to flip the board |
| Notifications | `accountNotifications(token, page, pageSize)`, `accountMarkNotificationRead(id, token)`, `accountMarkAllNotificationsRead(token)`, `accountDismissNotification(id, token)`, `accountNotificationPreferences(token)`, `accountUpdateNotificationPreferences(token, topics, soundEnabled)` |
| Notebook exports | `accountNotebooks(token)`, `accountNotebook(uuid, token)`, `accountNotebookChapterPgn(uuid, chapterId, token)`, `accountNotebookFile(uuid, token, password)` |
| Notebook, Remote, Cast and other application APIs | `ApplicationClient.request(...)`, `requestBytes(...)`, `download(...)` for files |
| Scanner upload | `ApplicationClient.scanPosition(jpegBytes, token)` |

Public game filters include `query`, `archivePlayer`, `archiveEvent`, `since`,
`until`, `sort`, `page` and `pageSize`. Each export is capped at 300 games; iterate
larger archives and export batches of up to 300 returned tokens. PGN is the
standard text format for chess games. NDJSON contains one JSON object per line;
`pgnInJson = false` omits PGN text from NDJSON records.

## Account and application requests

For private account or device APIs, use `ApplicationClient` and pass a token
explicitly for each request. Create a personal API token in your signed-in
Classic Chess profile settings with the scopes described in the
[account API reference](https://classicchess.com/api/#account-api).
Set `CLASSICCHESS_API_TOKEN` to that token before running this read-only example
in your configured Kotlin project:

```kotlin
import com.classicchess.api.ApplicationClient
import kotlinx.coroutines.runBlocking

fun main() = runBlocking {
    val token = requireNotNull(System.getenv("CLASSICCHESS_API_TOKEN")) {
        "Set CLASSICCHESS_API_TOKEN to your personal API token first"
    }
    ApplicationClient().use { api ->
        val response = api.accountMe(token)
        println("${response.status}: ${response.data}")
    }
}
```

Application responses contain `ok`, `status`, JSON `data` and `retryAfter`,
including HTTP errors such as conflicts. `request` accepts JSON text;
`requestBytes` preserves binary notebook uploads and their content type.
`scanPosition` accepts JPEG bytes up to 850,000 bytes. Personal tokens and device
session credentials have different permissions; use the credential required
by the endpoint. Credentials are never stored by the client or sent on public reads.

GIF, chapter PGN and Notebook file methods return an `ApplicationDownload` with
`ok`, `status`, `bytes` (the file when `ok`), `contentType`, `filename`,
`retryAfter` and `error` (the JSON error reply otherwise). GIF exports need a
registered account, so any personal token or device session works, and they
share the site limit on GIF exports: honor `retryAfter` after a 429.

## Errors and cancellation

Public-client HTTP failures and transport failures throw `ApiException` with
`code`, `status` and `retryAfter` when available. Coroutine cancellation cancels
the active HTTP call. The default timeout is 30 seconds and the response bound
is 32 MiB; configure them with `ClientOptions`. Pagination rejects repeated
pages, changed origins or collections, and more than 100,000 pages.

The application transport follows no redirects and sends no cookies. Bearer
requests require a private API path over HTTPS or loopback development.
Neither client retries mutations automatically. A GET whose pooled keep-alive
connection was already closed by the server is replayed once on a fresh
connection; POST, PUT, PATCH and DELETE never are. Honor `Retry-After` when
handling rate limits. Response models accept new fields; statistics and explorer helpers
return JSON objects to preserve the API's complete data.

## Build and test this repository

From the SDK checkout above, install Python 3 with PyYAML for the generated-model
check, then run the tests:

```sh
python3 -m venv .venv
source .venv/bin/activate
python3 -m pip install PyYAML==6.0.3
./gradlew check
```

`./gradlew packageClient` is an optional contributor command that creates a local
Maven repository ZIP in `artifacts/`; normal source installation does not need it.
The other maintained SDKs have the same API capabilities:
[Python](https://github.com/Andromeda1957/classicchess-api-python) and
[TypeScript](https://github.com/Andromeda1957/classicchess-api-typescript).
See the [API reference](https://classicchess.com/api/) for endpoint permissions,
response fields, rate limits and resource attribution. The SDK license covers
its software; chess resources retain their own stated reuse terms.
Issues and pull requests are welcome; see [CONTRIBUTING.md](https://github.com/Andromeda1957/classicchess-api-kotlin/blob/main/CONTRIBUTING.md).
