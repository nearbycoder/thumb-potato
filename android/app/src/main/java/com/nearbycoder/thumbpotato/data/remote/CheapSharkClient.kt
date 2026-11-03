package com.nearbycoder.thumbpotato.data.remote

import com.nearbycoder.thumbpotato.domain.Deal
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable

/** CheapShark: key-less PC prices across stores (USD). */
class CheapSharkClient {
    private val storeLock = Mutex()
    private var storeNames: Map<String, String>? = null

    suspend fun stores(): Map<String, String> = storeLock.withLock {
        storeNames ?: runCatching {
            Http.get<List<CsStore>>("$BASE/stores").associate { it.storeID to it.storeName }
        }.getOrDefault(emptyMap()).also { if (it.isNotEmpty()) storeNames = it }
    }

    /** CheapShark's game id for a Steam app, or for a title as a fallback. */
    suspend fun findGameId(steamAppId: Int?, title: String): String? {
        if (steamAppId != null) {
            val hits: List<CsLookup> = Http.get(Http.url(BASE, "/games", mapOf("steamAppID" to steamAppId.toString())))
            hits.firstOrNull()?.let { return it.gameID }
        }
        val hits: List<CsLookup> = Http.get(Http.url(BASE, "/games", mapOf("title" to title, "limit" to "5")))
        val wanted = normalize(title)
        return (hits.firstOrNull { normalize(it.external) == wanted } ?: hits.firstOrNull { steamAppId == null && normalize(it.external).startsWith(wanted) })?.gameID
    }

    /** The best [limit] deals for a game, cheapest first. */
    suspend fun deals(steamAppId: Int?, title: String, limit: Int = 6): List<Deal> {
        val id = findGameId(steamAppId, title) ?: return emptyList()
        val game: CsGame = Http.get(Http.url(BASE, "/games", mapOf("id" to id)))
        return parseDeals(game, stores(), limit)
    }

    /** Highly rated games on sale, for the Tonight "Deals" row. */
    suspend fun topDeals(): List<CsDealListing> =
        Http.get("$BASE/deals?sortBy=Deal%20Rating&pageSize=30&metacritic=75&onSale=1")

    companion object {
        const val BASE = "https://www.cheapshark.com/api/1.0"

        private fun normalize(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

        fun parseDeals(game: CsGame, storeNames: Map<String, String>, limit: Int = 6): List<Deal> =
            game.deals.mapNotNull { d ->
                val price = d.price.toDoubleOrNull() ?: return@mapNotNull null
                Deal(
                    storeId = d.storeID,
                    storeName = storeNames[d.storeID] ?: "Store ${d.storeID}",
                    price = price,
                    retailPrice = d.retailPrice.toDoubleOrNull() ?: price,
                    savings = d.savings.toDoubleOrNull() ?: 0.0,
                    dealId = d.dealID,
                )
            }.sortedBy { it.price }.distinctBy { it.storeId }.take(limit)
    }
}

@Serializable
data class CsStore(val storeID: String, val storeName: String, val isActive: Int = 1)

@Serializable
data class CsLookup(val gameID: String, val steamAppID: String? = null, val external: String = "", val cheapest: String? = null)

@Serializable
data class CsGame(val info: Info = Info(), val deals: List<CsDeal> = emptyList()) {
    @Serializable
    data class Info(val title: String = "", val steamAppID: String? = null)
}

@Serializable
data class CsDeal(val storeID: String, val dealID: String, val price: String, val retailPrice: String = "", val savings: String = "")

@Serializable
data class CsDealListing(
    val title: String = "",
    val dealID: String = "",
    val storeID: String = "",
    val salePrice: String = "",
    val normalPrice: String = "",
    val savings: String = "",
    val metacriticScore: String? = null,
    val steamAppID: String? = null,
) {
    val steamId: Int? get() = steamAppID?.toIntOrNull()?.takeIf { it > 0 }
}
