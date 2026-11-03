package com.nearbycoder.thumbpotato.data.remote

import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.GameFilter
import com.nearbycoder.thumbpotato.domain.GameRef
import com.nearbycoder.thumbpotato.domain.GameSource
import com.nearbycoder.thumbpotato.domain.Genre
import com.nearbycoder.thumbpotato.domain.Platform
import com.nearbycoder.thumbpotato.domain.Store
import com.nearbycoder.thumbpotato.domain.StoreOffer
import com.nearbycoder.thumbpotato.domain.strippingHtml
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * RAWG (optional, user-supplied key). Isolated here so the key-less app never depends on it.
 * Untested against the live API: no key was available during development.
 */
class RawgClient(private val key: String) {

    private suspend inline fun <reified T> get(path: String, query: Map<String, String?> = emptyMap()): T =
        Http.get(Http.url(BASE, path, query + ("key" to key)))

    /** Cheap call used by Settings to check a key. */
    suspend fun validate() {
        get<RawgPage>("/games", mapOf("page_size" to "1"))
    }

    suspend fun search(text: String): List<Game> =
        get<RawgPage>("/games", mapOf("search" to text, "page_size" to "40")).results.map { it.game }

    /** One page of games on [platforms] with [filter]; [genres] overrides the filter's genre (moods). */
    suspend fun games(platforms: List<Platform>, filter: GameFilter, page: Int, genres: List<Genre>? = null): List<Game> {
        val params = LinkedHashMap(filter.rawgParams())
        val ids = platforms.flatMap { it.rawgIds }
        if (ids.isNotEmpty()) params["platforms"] = ids.joinToString(",")
        if (genres != null) params["genres"] = genres.mapNotNull { it.rawgSlug }.distinct().joinToString(",").ifEmpty { null }
        params["page"] = page.toString()
        params["page_size"] = "40"
        return try {
            get<RawgPage>("/games", params).results.map { it.game }
        } catch (e: HttpException) {
            // RAWG answers 404 past the last page.
            if (e.status == 404) emptyList() else throw e
        }
    }

    data class Detail(val game: Game, val screenshots: List<String>, val stores: List<StoreOffer>, val similar: List<Game>, val website: String?)

    suspend fun detail(id: String, mine: Set<Platform>): Detail = coroutineScope {
        val game = async { get<RawgGame>("/games/$id") }
        val shots = async { runCatching { get<RawgScreenshots>("/games/$id/screenshots").results.map { it.image } }.getOrDefault(emptyList()) }
        val stores = async { runCatching { get<RawgStores>("/games/$id/stores").results }.getOrDefault(emptyList()) }
        val series = async { runCatching { get<RawgPage>("/games/$id/game-series").results.map { it.game } }.getOrDefault(emptyList()) }
        val g = game.await()
        val base = g.game
        val links = stores.await().mapNotNull { s ->
            val store = RAWG_STORES[s.storeId] ?: return@mapNotNull null
            StoreOffer(store = store, url = s.url ?: store.url(base.name), isMine = store.platforms.any { it in mine && it in base.platforms })
        }.distinctBy { it.store }
        val fallback = Store.offers(base, mine).filter { offer -> links.none { it.store == offer.store } }
        Detail(
            game = base,
            screenshots = shots.await(),
            stores = (links + fallback).sortedBy { if (it.isMine) 0 else 1 },
            similar = series.await(),
            website = g.website?.takeIf { it.isNotBlank() },
        )
    }

    companion object {
        const val BASE = "https://api.rawg.io/api"

        /** RAWG store ids → our stores. */
        val RAWG_STORES: Map<Int, Store> = mapOf(
            1 to Store.STEAM, 2 to Store.XBOX, 3 to Store.PLAYSTATION, 4 to Store.APPSTORE,
            5 to Store.GOG, 6 to Store.NINTENDO, 8 to Store.GOOGLEPLAY, 11 to Store.EPIC,
        )
    }
}

@Serializable
data class RawgPage(val results: List<RawgGame> = emptyList(), val next: String? = null)

@Serializable
data class RawgGame(
    val id: Int,
    val name: String = "",
    val released: String? = null,
    val tba: Boolean = false,
    @SerialName("background_image") val backgroundImage: String? = null,
    val metacritic: Int? = null,
    val added: Int = 0,
    val genres: List<Named> = emptyList(),
    val platforms: List<PlatformEntry>? = null,
    @SerialName("description_raw") val descriptionRaw: String? = null,
    val description: String? = null,
    val developers: List<Named>? = null,
    val publishers: List<Named>? = null,
    val website: String? = null,
) {
    @Serializable
    data class Named(val id: Int = 0, val name: String = "")

    @Serializable
    data class PlatformEntry(val platform: Named = Named())

    val game: Game
        get() {
            val date = DateParsing.date(released)
            return Game(
                ref = GameRef(GameSource.RAWG, id.toString()),
                name = name,
                summary = descriptionRaw ?: description?.strippingHtml() ?: "",
                artUrl = backgroundImage,
                heroUrl = backgroundImage,
                releaseDate = date,
                genres = Genre.mapAll(genres.map { it.name }),
                developer = developers?.firstOrNull()?.name,
                publisher = publishers?.firstOrNull()?.name,
                platforms = (platforms ?: emptyList()).mapNotNull { Platform.withRawgId(it.platform.id) }.distinct().sortedBy { it.ordinal },
                metacritic = metacritic,
                popularity = added.toDouble(),
            )
        }
}

@Serializable
data class RawgScreenshots(val results: List<Shot> = emptyList()) {
    @Serializable
    data class Shot(val image: String)
}

@Serializable
data class RawgStores(val results: List<Entry> = emptyList()) {
    @Serializable
    data class Entry(@SerialName("store_id") val storeId: Int = 0, val url: String? = null)
}
