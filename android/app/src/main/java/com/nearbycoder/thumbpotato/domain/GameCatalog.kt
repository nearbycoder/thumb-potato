package com.nearbycoder.thumbpotato.domain

import com.nearbycoder.thumbpotato.data.local.Settings
import com.nearbycoder.thumbpotato.data.remote.CatalogClient
import com.nearbycoder.thumbpotato.data.remote.CatalogIndex
import com.nearbycoder.thumbpotato.data.remote.CheapSharkClient
import com.nearbycoder.thumbpotato.data.remote.HttpException
import com.nearbycoder.thumbpotato.data.remote.MissingKeyException
import com.nearbycoder.thumbpotato.data.remote.RawgClient
import com.nearbycoder.thumbpotato.data.remote.SteamClient
import com.nearbycoder.thumbpotato.data.remote.WikipediaClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.time.LocalDate
import java.net.URLEncoder

/** What a paged grid shows. */
sealed interface GameList {
    data class OnPlatform(val platform: Platform) : GameList
    data class OfMood(val mood: Mood) : GameList
    data class OfGenre(val genre: Genre) : GameList
    data object NewReleases : GameList
    data object ComingSoon : GameList
}

/**
 * One front door to every data source. Key-less, it pages and filters the published
 * per-platform catalog locally and enriches details from Wikipedia, Steam and CheapShark.
 * With a RAWG key, platform/mood/genre grids and search go to RAWG instead.
 */
class GameCatalog(
    private val settingsFlow: StateFlow<Settings>,
    private val scope: CoroutineScope,
    cacheDir: File?,
) {
    private val catalogClient = CatalogClient(cacheDir)
    private val wikipedia = WikipediaClient()
    private val cheapShark = CheapSharkClient()

    private val lock = Mutex()
    private val loads = HashMap<Platform, Deferred<List<Game>>>()
    private val byKey = HashMap<String, Game>()
    private val bySteam = HashMap<Int, Game>()
    private val memo = HashMap<String, List<Game>>()

    private val settings: Settings get() = settingsFlow.value

    private val steam: SteamClient get() = SteamClient(settings.region)

    val rawg: RawgClient?
        get() = settings.effectiveRawgKey.takeIf { it.isNotEmpty() }?.let { RawgClient(it) }

    val hasRawg: Boolean get() = rawg != null

    /** Drops lists that depend on settings (region, key, platforms). Downloaded catalogs stay. */
    suspend fun reset() = lock.withLock { memo.clear() }

    private suspend fun remember(key: String, load: suspend () -> List<Game>): List<Game> {
        val fullKey = "$key|${settings.region}|$hasRawg"
        lock.withLock { memo[fullKey] }?.let { return it }
        val value = load()
        lock.withLock { memo[fullKey] = value }
        return value
    }

    // Catalog files

    /** Every catalog game on [platform], most popular first. Downloads once, then serves memory/disk. */
    suspend fun platform(platform: Platform): List<Game> {
        val deferred = lock.withLock {
            loads[platform]?.takeUnless { it.isCancelled }
                ?: scope.async(Dispatchers.Default) {
                    val games = catalogClient.platform(platform)
                    lock.withLock {
                        for (g in games) {
                            // A game's record is identical in every platform file; keep the first.
                            byKey.putIfAbsent(g.ref.key, g)
                            g.steamAppId?.let { bySteam.putIfAbsent(it, g) }
                        }
                    }
                    games
                }.also { loads[platform] = it }
        }
        return try {
            deferred.await()
        } catch (e: Exception) {
            throw if (e is HttpException) e else HttpException(503)
        }
    }

    /** Games on any of [platforms], deduped, most popular first. Platforms that fail to load are skipped. */
    suspend fun games(platforms: List<Platform>): List<Game> = coroutineScope {
        val lists = platforms.map { p -> async { runCatching { platform(p) }.getOrNull() } }.awaitAll()
        if (lists.all { it == null }) throw HttpException(503)
        lists.filterNotNull().flatten().distinctBy { it.id }.sortedByDescending { it.popularity }
    }

    suspend fun index(): CatalogIndex? = runCatching { catalogClient.index() }.getOrNull()

    /** The catalog record for a QID or Steam app, from files already loaded. */
    suspend fun known(key: String): Game? = lock.withLock { byKey[key] }
    suspend fun knownSteam(appId: Int): Game? = lock.withLock { bySteam[appId] }

    /** Loads the feed platforms plus anything cached on disk, so lookups and search see as much as possible. */
    private suspend fun warm(): List<Game> {
        val platforms = (settings.feedPlatforms + catalogClient.cachedPlatforms()).distinct()
        return runCatching { games(platforms) }.getOrDefault(emptyList())
    }

    // Home rows

    private val feed: List<Platform> get() = settings.feedPlatforms

    /** Popular releases from the last 18 months with box art; falls back to all platforms. */
    suspend fun hero(): List<Game> = remember("hero-${feed.map { it.id }}") {
        val today = LocalDate.now()
        fun pick(list: List<Game>) = list.filter { g ->
            val d = g.releaseDate
            g.boxArtUrl != null && d != null && g.isExactDate && d <= today && d >= today.minusMonths(18)
        }.take(8)
        pick(games(feed)).ifEmpty { pick(games(Platform.entries)) }
    }

    suspend fun newReleases(): List<Game> = remember("new-${feed.map { it.id }}") { newReleasesOf(games(feed).filter { it.boxArtUrl != null }) }

    suspend fun comingSoon(): List<Game> = remember("soon-${feed.map { it.id }}") { comingSoonOf(games(feed).filter { it.boxArtUrl != null }) }

    suspend fun topOn(platform: Platform): List<Game> = platform(platform).filter { it.boxArtUrl != null }.take(20)

    /** Steam's top sellers, resolved to catalog records where possible. */
    suspend fun steamTopSellers(): List<Game> = remember("steam-top") {
        val featured = steam.featured()
        resolveSteamApps(featured.topSellers.games.map { it.id }).take(20)
    }

    /** Highly rated PC games on sale (CheapShark), with Steam art. */
    suspend fun deals(): List<DealGame> {
        val listings = cheapShark.topDeals().filter { it.steamId != null }.distinctBy { it.steamId }
        val games = resolveSteamApps(listings.mapNotNull { it.steamId }).associateBy { it.steamAppId }
        return listings.mapNotNull { l ->
            val game = games[l.steamId] ?: return@mapNotNull null
            DealGame(
                game = game,
                salePrice = l.salePrice.toDoubleOrNull() ?: return@mapNotNull null,
                normalPrice = l.normalPrice.toDoubleOrNull() ?: 0.0,
                savings = l.savings.toDoubleOrNull() ?: 0.0,
                dealId = l.dealID,
            )
        }
    }

    /** Steam app IDs → games: catalog records when known, otherwise Steam's own art and facts. */
    private suspend fun resolveSteamApps(appIds: List<Int>): List<Game> {
        warm()
        val known = appIds.associateWith { knownSteam(it) }
        val unknown = appIds.filter { known[it] == null }
        val items = runCatching { steam.items(unknown) }.getOrDefault(emptyList()).associateBy { it.appid }
        return appIds.mapNotNull { id ->
            known[id] ?: items[id]?.takeIf { it.capsuleUrl != null && SteamClient.isLikelyGame(it.name) }?.game
        }
    }

    // Paged grids

    /** One page of a grid. An empty page means the list is exhausted. */
    suspend fun page(list: GameList, page: Int, filter: GameFilter): List<Game> {
        val client = rawg
        if (client != null && list !is GameList.NewReleases && list !is GameList.ComingSoon) {
            return remember("rawg-$list-${filter.key}-p$page") {
                when (list) {
                    is GameList.OnPlatform -> client.games(listOf(list.platform), filter, page)
                    is GameList.OfMood -> client.games(settings.myPlatforms, filter.copy(genre = null), page, genres = list.mood.genres)
                    is GameList.OfGenre -> client.games(settings.myPlatforms, filter.copy(genre = list.genre), page)
                    else -> emptyList()
                }
            }
        }
        val base: List<Game> = when (list) {
            is GameList.OnPlatform -> platform(list.platform)
            is GameList.OfMood -> games(feed).filter(list.mood::matches)
            is GameList.OfGenre -> games(feed).filter { list.genre.label in it.genres }
            GameList.NewReleases -> newReleasesOf(games(feed), limit = Int.MAX_VALUE)
            GameList.ComingSoon -> comingSoonOf(games(feed), limit = Int.MAX_VALUE)
        }
        val effective = when (list) {
            is GameList.OfMood, is GameList.OfGenre -> filter.copy(genre = null)
            else -> filter
        }
        return slice(effective.apply(base), page)
    }

    private fun slice(games: List<Game>, page: Int): List<Game> {
        val start = (page - 1) * PAGE_SIZE
        if (page < 1 || start >= games.size) return emptyList()
        return games.subList(start, minOf(start + PAGE_SIZE, games.size))
    }

    /** Shuffle's Discover pool: catalog games on [platforms]. */
    suspend fun discover(platforms: List<Platform>): List<Game> = games(platforms.ifEmpty { feed }).filter { it.boxArtUrl != null }.take(600)

    // Search

    suspend fun search(text: String): List<Game> {
        val query = text.trim()
        if (query.length < 2) return emptyList()
        rawg?.let { return it.search(query) }
        return coroutineScope {
            val wiki = async { runCatching { wikipedia.search(query) }.getOrDefault(emptyList()) }
            val steamHits = async { runCatching { steam.search(query) }.getOrDefault(emptyList()) }
            val local = SearchMerge.rankLocal(warm(), query)
            val wikiGames = wiki.await().map { w -> known(w.ref.key)?.merging(w) ?: w }
            val steamGames = resolveSteamApps(steamHits.await().map { it.id }.take(15))
            SearchMerge.merge(local, wikiGames, steamGames)
        }
    }

    // Details

    /** What we can show instantly for [ref] (catalog record + store rows), before any network enrichment. */
    suspend fun preview(ref: GameRef): GameDetail? {
        val game = when (ref.source) {
            GameSource.WD -> known(ref.key) ?: run { warm(); known(ref.key) }
            GameSource.STEAM -> ref.id.toIntOrNull()?.let { id -> knownSteam(id) ?: run { warm(); knownSteam(id) } }
            GameSource.RAWG -> null
        } ?: return null
        return GameDetail(game = game, stores = Store.offers(game, settings.myPlatformSet))
    }

    suspend fun detail(ref: GameRef): GameDetail = when (ref.source) {
        GameSource.WD -> {
            val game = known(ref.key) ?: run { warm(); known(ref.key) } ?: wikipedia.wikidataGame(ref.id)
            enrich(game)
        }
        GameSource.STEAM -> {
            val appId = ref.id.toIntOrNull() ?: throw HttpException(404)
            // A Steam game the catalog knows resolves to its catalog record, so the backlog never holds both.
            val match = knownSteam(appId) ?: run { warm(); knownSteam(appId) }
            if (match != null) {
                enrich(match)
            } else {
                val data = steam.appDetails(appId) ?: throw HttpException(404)
                enrich(data.game)
            }
        }
        GameSource.RAWG -> {
            val client = rawg ?: throw MissingKeyException()
            val d = client.detail(ref.id, settings.myPlatformSet)
            GameDetail(
                game = d.game, screenshots = d.screenshots, stores = d.stores, similar = d.similar,
                website = d.website, trailerUrl = youtubeTrailer(d.game.name),
                pcDeals = if (d.game.platforms.any { it == Platform.PC || it == Platform.MAC }) {
                    runCatching { cheapShark.deals(null, d.game.name) }.getOrDefault(emptyList())
                } else emptyList(),
            )
        }
    }

    /** Fills in summary, art, screenshots, trailer, reviews, Deck status, store rows, PC prices and similar games. */
    private suspend fun enrich(base: Game): GameDetail = coroutineScope {
        val appId = base.steamAppId
        val summary = async {
            base.wikiTitle?.let { title -> runCatching { wikipedia.summary(title) }.getOrNull() }
        }
        val appData = async { appId?.let { runCatching { steam.appDetails(it) }.getOrNull() } }
        val item = async { appId?.let { runCatching { steam.items(listOf(it)).firstOrNull() }.getOrNull() } }
        val onPc = base.platforms.any { it == Platform.PC || it == Platform.MAC } || appId != null
        val deals = async { if (onPc) runCatching { cheapShark.deals(appId, base.name) }.getOrDefault(emptyList()) else emptyList() }
        val similar = async { runCatching { similar(base) }.getOrDefault(emptyList()) }

        var game = base
        summary.await()?.let { s ->
            val extract = s.extract?.trim().orEmpty()
            game = game.copy(
                summary = if (extract.length > game.summary.length) extract else game.summary,
                coverUrl = game.coverUrl ?: s.imageUrl,
            )
        }
        val data = appData.await()
        data?.let { game = game.merging(it.game) }
        val storeItem = item.await()
        storeItem?.let { si ->
            game = game.copy(
                artUrl = game.artUrl ?: si.capsuleUrl,
                heroUrl = game.heroUrl ?: si.heroUrl,
                steamDeck = si.platforms?.steamDeck ?: game.steamDeck,
            )
        }
        GameDetail(
            game = game,
            screenshots = data?.screenshots?.mapNotNull { it.full } ?: emptyList(),
            trailerUrl = data?.trailerUrl ?: youtubeTrailer(game.name),
            stores = Store.offers(game, settings.myPlatformSet, steamPrice = data?.priceLabel),
            pcDeals = deals.await(),
            similar = similar.await(),
            website = data?.website?.takeIf { it.isNotBlank() },
            steamReviews = storeItem?.reviewLabel,
            steamPrice = data?.priceLabel,
        )
    }

    /** Same top genre on the same platforms, from the catalog. */
    suspend fun similar(game: Game): List<Game> {
        val genre = game.genres.firstOrNull() ?: return emptyList()
        val platforms = game.platforms.filter { lock.withLock { loads.containsKey(it) } }
            .ifEmpty { game.platforms.take(1) }
        if (platforms.isEmpty()) return emptyList()
        return games(platforms)
            .filter { it.ref != game.ref && it.steamAppId.let { id -> id == null || id != game.steamAppId } && genre in it.genres && it.boxArtUrl != null }
            .take(15)
    }

    private fun youtubeTrailer(name: String): String =
        "https://www.youtube.com/results?search_query=" + URLEncoder.encode("$name trailer", "UTF-8")

    companion object {
        /** Page size for catalog grids, which arrive whole and are paged locally. */
        const val PAGE_SIZE = 60

        /** Released in the last 120 days on an exact date, newest first. */
        fun newReleasesOf(games: List<Game>, today: LocalDate = LocalDate.now(), limit: Int = 30): List<Game> =
            games.filter { g ->
                val d = g.releaseDate
                g.isExactDate && d != null && d <= today && d >= today.minusDays(120)
            }.sortedByDescending { it.releaseDate }.take(limit)

        /** Not out yet, soonest first; coarse dates sort at the end of their window. */
        fun comingSoonOf(games: List<Game>, today: LocalDate = LocalDate.now(), limit: Int = 30): List<Game> =
            games.filter { it.isUpcoming(today) }
                .sortedWith(compareBy<Game> { ReleaseDates.windowEnd(it.releaseDate!!, it.releaseDatePrecision) }.thenByDescending { it.popularity })
                .take(limit)
    }
}
