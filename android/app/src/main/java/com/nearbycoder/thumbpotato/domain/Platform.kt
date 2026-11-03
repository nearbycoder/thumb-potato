package com.nearbycoder.thumbpotato.domain

import java.net.URLEncoder

/** Consoles, PC, Mac and mobile, in display order. `id` is used in catalog files, backups and settings. */
enum class Platform(
    val id: String,
    val displayName: String,
    val shortName: String,
    val monogram: String,
    /** Brand color as RRGGBB. Used only for small tiles, dots and monograms. */
    val colorHex: String,
    /** RAWG platform ids (Switch 2 is not mapped yet: no key to look it up during development). */
    val rawgIds: List<Int>,
    /** Wikidata platform items, as in the crawler's PLATFORMS list. */
    val wikidataIds: List<String>,
) {
    SWITCH2("switch2", "Nintendo Switch 2", "Switch 2", "NS2", "E60012", emptyList(), listOf("Q122761124")),
    SWITCH("switch", "Nintendo Switch", "Switch", "NS", "E60012", listOf(7), listOf("Q19610114")),
    PS5("ps5", "PlayStation 5", "PS5", "PS5", "0070D1", listOf(187), listOf("Q63184502")),
    PS4("ps4", "PlayStation 4", "PS4", "PS4", "003791", listOf(18), listOf("Q5014725")),
    XBOX_SERIES("xbox-series", "Xbox Series X|S", "Xbox Series", "XS", "107C10", listOf(186), listOf("Q98973368")),
    XBOX_ONE("xbox-one", "Xbox One", "Xbox One", "XB1", "0E6A0E", listOf(1), listOf("Q13361286")),
    PC("pc", "PC", "PC", "PC", "2A475E", listOf(4), listOf("Q1406")),
    MAC("mac", "Mac", "Mac", "MAC", "A2AAAD", listOf(5), listOf("Q14116")),
    MOBILE("mobile", "iOS & Android", "Mobile", "MOB", "5E5CE6", listOf(3, 21), listOf("Q48493", "Q94"));

    companion object {
        fun withId(id: String?): Platform? = entries.firstOrNull { it.id == id }

        fun withRawgId(rawgId: Int): Platform? = entries.firstOrNull { rawgId in it.rawgIds }

        fun withWikidataId(qid: String): Platform? = entries.firstOrNull { qid in it.wikidataIds }

        /** Home feed platforms when the user hasn't picked any. */
        val defaults: List<Platform> = listOf(SWITCH, PS5, XBOX_SERIES, PC)
    }
}

/** Where to Play stores. */
enum class Store(
    val id: String,
    val displayName: String,
    val monogram: String,
    val colorHex: String,
    val platforms: List<Platform>,
    /** `{q}` is the URL-encoded game name. */
    private val searchTemplate: String,
) {
    STEAM("steam", "Steam", "ST", "1B2838", listOf(Platform.PC, Platform.MAC), "https://store.steampowered.com/search/?term={q}"),
    EPIC("epic", "Epic Games Store", "EG", "2F2F2F", listOf(Platform.PC, Platform.MAC), "https://store.epicgames.com/en-US/browse?q={q}"),
    GOG("gog", "GOG", "GOG", "86328A", listOf(Platform.PC, Platform.MAC), "https://www.gog.com/en/games?query={q}"),
    PLAYSTATION("playstation", "PlayStation Store", "PS", "0070D1", listOf(Platform.PS5, Platform.PS4), "https://store.playstation.com/en-us/search/{q}"),
    XBOX("xbox", "Xbox Store", "XB", "107C10", listOf(Platform.XBOX_SERIES, Platform.XBOX_ONE, Platform.PC), "https://www.xbox.com/en-US/search/results/games?q={q}"),
    NINTENDO("nintendo", "Nintendo eShop", "N", "E60012", listOf(Platform.SWITCH2, Platform.SWITCH), "https://www.nintendo.com/us/search/#q={q}&p=1&cat=gme&sort=df"),
    APPSTORE("appstore", "App Store", "AS", "0A84FF", listOf(Platform.MOBILE, Platform.MAC), "https://apps.apple.com/us/search?term={q}"),
    GOOGLEPLAY("googleplay", "Google Play", "GP", "01875F", listOf(Platform.MOBILE), "https://play.google.com/store/search?q={q}&c=apps");

    fun url(gameName: String, steamAppId: Int? = null): String {
        if (this == STEAM && steamAppId != null) return "https://store.steampowered.com/app/$steamAppId"
        val q = URLEncoder.encode(gameName, "UTF-8").replace("+", "%20")
        return searchTemplate.replace("{q}", q)
    }

    companion object {
        fun withId(id: String?): Store? = entries.firstOrNull { it.id == id }

        /**
         * Store rows for a game's platforms: stores serving one of my platforms first,
         * then the rest, each in catalog order.
         */
        fun offers(game: Game, mine: Set<Platform>, steamPrice: String? = null): List<StoreOffer> {
            val gamePlatforms = game.platforms.toSet()
            return entries
                .filter { store -> store.platforms.any { it in gamePlatforms } }
                .map { store ->
                    StoreOffer(
                        store = store,
                        url = store.url(game.name, game.steamAppId),
                        price = if (store == STEAM) steamPrice else null,
                        isMine = store.platforms.any { it in gamePlatforms && it in mine },
                    )
                }
                .sortedBy { if (it.isMine) 0 else 1 }
        }
    }
}
