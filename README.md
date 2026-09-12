# Classic Chess Kotlin API client

Typed, cancellable public archive reads for Kotlin/JVM and Android: complete
player, event and book catalogs; biographies; curated notable games; MasterDB
search; games and PGNs.
No API key is needed. MIT licensed; see [LICENSE](LICENSE). The Gradle wrapper
retains its Apache 2.0 license in `THIRD_PARTY_LICENSES/gradle-Apache-2.0.txt`.
See the [Python SDK](https://github.com/Andromeda1957/classicchess-api-python),
[TypeScript SDK](https://github.com/Andromeda1957/classicchess-api-typescript),
and [API reference](https://classicchess.com/api/).

## Install

The initial package is available locally; it has **not been published to Maven
Central**. Build the Maven repository ZIP from this SDK directory using JDK 17+
and Python 3 with PyYAML (for the shared schema check):

```sh
./gradlew packageClient
unzip ./artifacts/classicchess-kotlin-api-0.1.0.zip -d classicchess-kotlin
```

Add the extracted repository to your Gradle repositories, alongside Maven Central
and Google's repository for Android dependencies. Keep the whole repository,
including the POM and Gradle module metadata, rather than copying just the JAR.

```kotlin
repositories {
    maven { url = uri("/absolute/path/to/classicchess-kotlin/repository") }
    mavenCentral()
    google()
}
dependencies { implementation("com.classicchess:api-client:0.1.0") }
```

The SDK targets JVM 8 bytecode and is compiled with Kotlin 2.1.20. The Android
consumer example builds with this repository's Kotlin 2.1.20 / AGP 8.9.1 toolchain,
minSdk 23 and Java 17. Dependencies are OkHttp, kotlinx.serialization and
kotlinx.coroutines; Gradle resolves their platform variants automatically.

The private monorepo’s native app includes this project as a Gradle composite
build and depends on `com.classicchess:api-client:0.1.0`. Its APK therefore
contains the client built from the same checkout, without a separate Maven
publication or local package-install step.

## See what is available

Call these inside a coroutine. Reuse one client for its owner's lifetime and
close it when that owner ends.

```kotlin
import com.classicchess.api.*

val api = ClassicChessClient()
api.playerNames().forEach(::println) // All curated player names
api.eventNames().forEach(::println)  // All tournament and match names
api.bookTitles().forEach(::println)  // All annotated book titles

val players = api.publicPlayers()   // Complete profiles, exact slugs and links
val events = api.publicEvents()     // Complete catalog; no pagination
val books = api.annotatedBooks()    // Complete catalog; no pagination
```

The [runnable JVM example](examples/jvm/src/main/kotlin/com/classicchess/example/Main.kt)
uses the built Maven package. After `packageClient`, run it from this SDK directory
with `players`, `events`, `books` or `profile`:

```sh
./gradlew -q -p examples/jvm run --args="players"
./gradlew -q -p examples/jvm run --args="books https://classicchess.com"
```

`publicPlayers("tal")`, `playerNames("tal")`, `publicEvents("1972")` and
`eventNames("1972")` filter those catalogs. Never guess or normalize a slug:
pass back the exact value returned by the API.

## Bios, notable games and PGN

```kotlin
val player = api.publicPlayers("tal").results.first { it.name == "Mikhail Tal" }
val profile = api.publicPlayer(player.slug).player
val bio = api.publicPlayerBio(player.slug).bio
println(bio?.lede ?: "Biography unavailable")
bio?.sources?.forEach { println("${it.label}: ${it.url}") }

val notables = api.publicNotableGames(player.slug) // Complete curated order
notables.results.forEach { println("${it.position}. ${it.title}") }
notables.results.firstOrNull()?.let { notable ->
    val game = api.publicGame(notable.game.token)
    val pgn = api.publicPgn(game.token)
    println(pgn)
}
```

An absent biography is `null`; an absent notable list has empty `results`.
Preserve the notable order and attribution/source metadata in your application.

## Games and pagination

MasterDB search uses its own opaque identifiers and bounded result pages:

```kotlin
val matches = api.masterGames(MasterGameFilters("Karpov", pageSize = 25))
matches.results.firstOrNull()?.let { match ->
    val detail = api.masterGame(match.token) // PGN and mainline moves
    val pgn = api.masterPgn(match.token)
}
api.iterateMasterGames(MasterGameFilters("Kasparov Karpov")).collect { game ->
    println(game.token)
}
```

Queries must contain 1–120 Unicode code points, with at least one non-whitespace
character. Preserve `countIsExact`, `resultLimit`, `hitResultLimit`, `message`
and `hint` when displaying search results. Counts can be lower bounds; the
iterator follows `next` until the server ends the search or caps the result set.
Pass the exact returned MasterDB token to detail/PGN methods.

```kotlin
val filters = PublicGameFilters(archivePlayer = player.slug, pageSize = 50)
val firstPage = api.publicGames(filters) // count, next, previous, results
api.iteratePublicGames(filters, IterationOptions(limit = 150)).collect { game ->
    println("${game.white} – ${game.black}: ${game.token}")
}

val book = api.annotatedBooks().results.first()
api.iterateAnnotatedGames(book.slug).collect { game -> println(game.token) }
val page = api.annotatedGames(book.slug, PageOptions(pageSize = 10))
val gameSlug = page.results.first().token.substringAfter('/')
val detail = api.annotatedGame(book.slug, gameSlug)
val annotatedGamePgn = api.annotatedPgn(book.slug, gameSlug)
val entireBookPgn = api.annotatedPgn(book.slug)
```

Annotated game tokens have the form `book/game`; pass the game portion to
`annotatedGame` and `annotatedPgn`. Each `Flow` is lazy:
collecting requests pages in sequence until `next` is `null`. Cancelling collection
cancels an active HTTP request. `limit = 0` makes no requests; omit `limit` to read
the entire collection. `maxPages` defaults to 100,000 and rejects runaway or cyclic
pagination. No parallel prefetch or automatic retries occur.

Filters include `query`, `archivePlayer`, `archiveEvent`, `since`, `until`, `sort`
(`asc`/`desc`, or `event` with `archiveEvent`), `page` and `pageSize` (1–100). The service caps public PGN exports at
300 games per request:

```kotlin
val pgn = api.exportPublicGames(tokens = notables.results.map { it.game.token })
val filteredPgn = api.exportPublicGames(PublicGameFilters(archivePlayer = player.slug))
```

For larger exports, iterate games, accumulate batches of up to 300 tokens, and
export each batch. Avoid calling token export for an empty list. Whole-book PGN
uses `annotatedPgn(book.slug)`.

## Android and errors

The [Android consumer](examples/android/src/main/java/com/classicchess/example/ArchiveReader.kt)
shows lifecycle ownership and cancellation. Add `android.permission.INTERNET`;
launch calls in your screen's coroutine scope. Suspending requests do not block
the main thread, and JSON decoding runs on `Dispatchers.Default`. Do not use
`runBlocking` on an Android UI thread.

```kotlin
val api = ClassicChessClient(ClientOptions(
    userAgent = "YourApp/1.0 (developer@example.com)",
    timeoutMillis = 30_000,
    maxResponseBytes = 32L * 1024 * 1024,
))
try {
    api.playerNames().forEach(::println)
} catch (error: ApiException) {
    println("${error.code}: ${error.message}")
    // HTTP failures also expose status and retryAfter (e.g. rate limit 429).
} finally {
    api.close()
}
```

Coroutine cancellation remains `CancellationException`; let it propagate.
`ApiException` distinguishes `timeout`, `network_error`, `invalid_response`,
`response_too_large`, `unsafe_url`, `unsafe_redirect`, `invalid_pagination`,
`invalid_identifier`, `invalid_query`, `invalid_export`, `invalid_options` and
`client_closed`, plus server error codes. Honor `Retry-After` on 429/503 and
retry through your app's bounded policy. The default timeout covers the whole
request; the response limit applies to decoded bytes, including PGN. Redirects
and pagination links outside the configured origin/collection are rejected.
`ClassicChessClient` requests send no credentials or cookies. For a local server, set `baseUrl` to
its HTTP(S) origin; this does not change Android's cleartext-network policy.

## Application APIs and images

Reuse `ApplicationClient` for mobile home/release metadata, accounts, notebooks,
opening positions and Cast. Bearers are explicit per request, never stored in
the SDK. The same pooled OkHttp transport bounds responses, rejects redirects,
omits cookies and cancels calls when their coroutine is cancelled or the owner closes.
HTTP responses retain `{ status, ok, data, retryAfter }`; `data` is a `JsonObject`,
including error bodies. Network failures and malformed JSON throw `ApiException`.

```kotlin
val application = ApplicationClient()
val home = application.request("/api/v1/mobile/home/")
val notebooks = application.request("/api/v1/mobile/notebooks/", token = deviceToken)
val result = application.scanPosition(resizedJpeg, deviceToken)
application.close()
```

`request` accepts GET/POST/PUT/PATCH/DELETE and JSON text (maximum 10 MiB).
`scanPosition` bounds the prepared JPEG to 850,000 bytes and builds multipart
content with a fixed safe filename; it retains `saved_failure` in error responses.
Private requests require HTTPS or loopback development. The native debug build
explicitly enables `allowInsecureEmulator` for **10.0.2.2 only**; the release
build never enables that exception. Never enable it for production credentials.

Android's existing ordered mutation executor uses suspending SDK calls from its
background threads. All structured JSON and scanner uploads use the SDK;
public archive callbacks remain activity-owned and run on the UI thread.
Images keep their existing coalescing memory/disk cache and direct HTTPS loader.
The API already provides `portrait.thumbnail` and `portrait.image`; an empty
URL means that player has no portrait. APK and authenticated media downloads
keep their resumable/verified binary download handlers.

`eventSeries()` returns series cards; `eventSeries(series = "matches")` returns
members. `publicGames(PublicGameFilters(archiveEvent = slug, sort = "event"))`
preserves curated round order and round labels. Android also uses
`/api/v1/opening-explorer/player/`, which omits website HTML fragments. Deploy
these new server endpoints before releasing an app that calls them.

## Contract and checks

Models are generated from the shared [OpenAPI schema](https://classicchess.com/api/openapi.yaml).
`check` rejects model drift and exercises the real HTTP transport against a local
mock server. API changes must update that schema and regenerate both Kotlin and
TypeScript models. The typed archive client covers MasterDB, public study, event-series and annotated
archives. `ApplicationClient` covers the application JSON APIs described below.

```sh
python3 ./scripts/generate_models.py
./gradlew check
./gradlew verifyPreview -PapiBaseUrl=http://127.0.0.1:8000
./gradlew -p examples/android assembleDebug lintDebug
```

The preview check is optional and expects a populated development archive with
Tal's biography and notable games. The Android consumer resolves the built local
Maven repository; run `packageClient` first. Nothing in these commands publishes
externally.
