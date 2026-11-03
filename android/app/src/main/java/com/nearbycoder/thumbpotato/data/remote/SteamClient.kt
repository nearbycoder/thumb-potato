package com.nearbycoder.thumbpotato.data.remote

import com.nearbycoder.thumbpotato.domain.DatePrecision
import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.GameRef
import com.nearbycoder.thumbpotato.domain.GameSource
import com.nearbycoder.thumbpotato.domain.Genre
import com.nearbycoder.thumbpotato.domain.Platform
import com.nearbycoder.thumbpotato.domain.strippingHtml
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlinx.serialization.json.addJsonObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Key-less Steam store endpoints: featured lists, art/reviews/Deck status, app details and search. */
class SteamClient(private val region: String = "US") {

    suspend fun featured(): SteamFeatured =
        Http.get("https://store.steampowered.com/api/featuredcategories?cc=$region&l=english")

    /** Art, platforms, reviews and release for up to 50 apps per call. */
    suspend fun items(appIds: List<Int>): List<SteamStoreItem> {
        if (appIds.isEmpty()) return emptyList()
        return appIds.distinct().chunked(50).flatMap { chunk ->
            val response: SteamItemsResponse = Http.get(itemsUrl(chunk, region))
            response.response.storeItems.filter { it.success == null || it.success == 1 }
        }
    }

    suspend fun appDetails(appId: Int): SteamAppData? {
        val envelope: Map<String, SteamAppEnvelope> =
            Http.get("https://store.steampowered.com/api/appdetails?appids=$appId&cc=$region&l=english")
        return envelope[appId.toString()]?.takeIf { it.success }?.data
    }

    suspend fun search(term: String): List<SteamSearchItem> {
        val result: SteamSearchResponse = Http.get(Http.url("https://store.steampowered.com", "/api/storesearch/", mapOf("term" to term, "cc" to region, "l" to "english")))
        return result.items.filter { it.type == null || it.type == "app" }
    }

    companion object {
        const val ASSET_BASE = "https://shared.akamai.steamstatic.com/store_item_assets/"

        /** `steam/apps/N/${FILENAME}?t=…` + `library_600x900_2x.jpg` → full image URL. */
        fun assetUrl(format: String?, filename: String?): String? {
            if (format.isNullOrEmpty() || filename.isNullOrEmpty()) return null
            return ASSET_BASE + format.replace("\${FILENAME}", filename)
        }

        fun itemsUrl(appIds: List<Int>, region: String): String {
            val input = buildJsonObject {
                putJsonArray("ids") { appIds.forEach { id -> addJsonObject { put("appid", id) } } }
                putJsonObject("context") {
                    put("language", "english")
                    put("country_code", region)
                }
                putJsonObject("data_request") {
                    put("include_assets", true)
                    put("include_platforms", true)
                    put("include_basic_info", true)
                    put("include_reviews", true)
                    put("include_release", true)
                }
            }
            return Http.url("https://api.steampowered.com", "/IStoreBrowseService/GetItems/v1/", mapOf("input_json" to input.toString()))
        }

        /** Names in featured lists that aren't games: hardware, demos, soundtracks. */
        fun isLikelyGame(name: String): Boolean {
            val lower = name.lowercase()
            if (listOf("demo", "soundtrack", " ost").any { lower.contains(it) }) return false
            return lower !in HARDWARE
        }

        private val HARDWARE = setOf("steam frame", "steam deck", "steam controller", "valve index", "steam machine", "steam link")

        private val dayFormats = listOf("MMM d, yyyy", "d MMM, yyyy", "MMMM d, yyyy", "d MMMM, yyyy")
            .map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }
        private val monthFormats = listOf("MMM yyyy", "MMMM yyyy").map { DateTimeFormatter.ofPattern(it, Locale.ENGLISH) }

        /** Steam's display dates: "Sep 17, 2020", "Mar 2027", "2027", "Q2 2027"; null for "Coming soon". */
        fun parseReleaseDate(text: String?): Pair<LocalDate, DatePrecision>? {
            val s = text?.trim()?.replace(Regex("\\s+"), " ") ?: return null
            if (s.isEmpty()) return null
            dayFormats.forEach { f -> runCatching { return LocalDate.parse(s, f) to DatePrecision.DAY } }
            monthFormats.forEach { f -> runCatching { return YearMonth.parse(s, f).atDay(1) to DatePrecision.MONTH } }
            Regex("\\b(20\\d\\d|19\\d\\d)\\b").find(s)?.let { return LocalDate.of(it.value.toInt(), 1, 1) to DatePrecision.YEAR }
            return null
        }
    }
}

// DTOs

@Serializable
data class SteamFeatured(
    @SerialName("top_sellers") val topSellers: SteamFeaturedList = SteamFeaturedList(),
    @SerialName("new_releases") val newReleases: SteamFeaturedList = SteamFeaturedList(),
    val specials: SteamFeaturedList = SteamFeaturedList(),
    @SerialName("coming_soon") val comingSoon: SteamFeaturedList = SteamFeaturedList(),
)

@Serializable
data class SteamFeaturedList(val items: List<SteamFeaturedItem> = emptyList()) {
    /** Unique games in list order (lists repeat entries). */
    val games: List<SteamFeaturedItem> get() = items.distinctBy { it.id }.filter { SteamClient.isLikelyGame(it.name) }
}

@Serializable
data class SteamFeaturedItem(
    val id: Int,
    val name: String = "",
    @SerialName("discount_percent") val discountPercent: Int = 0,
    @SerialName("final_price") val finalPrice: Long? = null,
    @SerialName("header_image") val headerImage: String? = null,
)

@Serializable
data class SteamItemsResponse(val response: Inner = Inner()) {
    @Serializable
    data class Inner(@SerialName("store_items") val storeItems: List<SteamStoreItem> = emptyList())
}

@Serializable
data class SteamStoreItem(
    val appid: Int = 0,
    val name: String = "",
    val success: Int? = null,
    val assets: Assets? = null,
    val platforms: Platforms? = null,
    val reviews: Reviews? = null,
    val release: Release? = null,
    @SerialName("basic_info") val basicInfo: BasicInfo? = null,
) {
    @Serializable
    data class Assets(
        @SerialName("asset_url_format") val format: String? = null,
        @SerialName("library_capsule") val libraryCapsule: String? = null,
        @SerialName("library_capsule_2x") val libraryCapsule2x: String? = null,
        @SerialName("library_hero") val libraryHero: String? = null,
        @SerialName("library_hero_2x") val libraryHero2x: String? = null,
        val header: String? = null,
    )

    @Serializable
    data class Platforms(
        val windows: Boolean = false,
        val mac: Boolean = false,
        @SerialName("steam_deck_compat_category") val steamDeck: Int? = null,
    )

    @Serializable
    data class Reviews(@SerialName("summary_filtered") val summary: Summary? = null) {
        @Serializable
        data class Summary(
            @SerialName("percent_positive") val percentPositive: Int? = null,
            @SerialName("review_score_label") val label: String? = null,
            @SerialName("review_count") val count: Int? = null,
        )
    }

    @Serializable
    data class Release(@SerialName("steam_release_date") val steamReleaseDate: Long? = null)

    @Serializable
    data class BasicInfo(@SerialName("short_description") val shortDescription: String? = null)

    val capsuleUrl: String? get() = SteamClient.assetUrl(assets?.format, assets?.libraryCapsule2x ?: assets?.libraryCapsule)
    val heroUrl: String? get() = SteamClient.assetUrl(assets?.format, assets?.libraryHero2x ?: assets?.libraryHero)

    /** "Very Positive · 91%" */
    val reviewLabel: String?
        get() {
            val s = reviews?.summary ?: return null
            val label = s.label?.takeIf { it.isNotBlank() } ?: return null
            return s.percentPositive?.let { "$label · $it%" } ?: label
        }

    val game: Game
        get() {
            val date = release?.steamReleaseDate?.let { Instant.ofEpochSecond(it).atZone(ZoneOffset.UTC).toLocalDate() }
            return Game(
                ref = GameRef(GameSource.STEAM, appid.toString()),
                name = name,
                summary = basicInfo?.shortDescription?.strippingHtml() ?: "",
                artUrl = capsuleUrl,
                heroUrl = heroUrl,
                releaseDate = date,
                platforms = buildList {
                    if (platforms?.windows != false) add(Platform.PC)
                    if (platforms?.mac == true) add(Platform.MAC)
                },
                steamAppId = appid,
                steamDeck = platforms?.steamDeck,
            )
        }
}

@Serializable
data class SteamAppEnvelope(val success: Boolean = false, val data: SteamAppData? = null)

@Serializable
data class SteamAppData(
    val name: String = "",
    @SerialName("steam_appid") val appId: Int = 0,
    @SerialName("short_description") val shortDescription: String? = null,
    val screenshots: List<Screenshot> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val genres: List<Described> = emptyList(),
    val metacritic: Metacritic? = null,
    @SerialName("release_date") val releaseDate: ReleaseDate? = null,
    @SerialName("price_overview") val price: Price? = null,
    @SerialName("is_free") val isFree: Boolean = false,
    val developers: List<String> = emptyList(),
    val publishers: List<String> = emptyList(),
    val website: String? = null,
    val platforms: Platforms? = null,
) {
    @Serializable
    data class Screenshot(@SerialName("path_full") val full: String? = null, @SerialName("path_thumbnail") val thumbnail: String? = null)

    @Serializable
    data class Movie(
        val name: String? = null,
        @SerialName("hls_h264") val hls: String? = null,
        val mp4: Map<String, String>? = null,
    ) {
        val url: String? get() = mp4?.get("max") ?: mp4?.values?.firstOrNull() ?: hls
    }

    @Serializable
    data class Described(val description: String = "")

    @Serializable
    data class Metacritic(val score: Int? = null)

    @Serializable
    data class ReleaseDate(@SerialName("coming_soon") val comingSoon: Boolean = false, val date: String? = null)

    @Serializable
    data class Price(
        @SerialName("final_formatted") val finalFormatted: String? = null,
        @SerialName("initial_formatted") val initialFormatted: String? = null,
        @SerialName("discount_percent") val discountPercent: Int = 0,
    )

    @Serializable
    data class Platforms(val windows: Boolean = false, val mac: Boolean = false)

    val trailerUrl: String? get() = movies.firstNotNullOfOrNull { it.url }

    val priceLabel: String? get() = price?.finalFormatted?.takeIf { it.isNotBlank() } ?: if (isFree) "Free" else null

    val game: Game
        get() {
            val date = SteamClient.parseReleaseDate(releaseDate?.date)
            return Game(
                ref = GameRef(GameSource.STEAM, appId.toString()),
                name = name,
                summary = shortDescription?.strippingHtml() ?: "",
                releaseDate = date?.first,
                releaseDatePrecision = date?.second ?: DatePrecision.DAY,
                genres = Genre.mapAll(genres.map { it.description }),
                developer = developers.firstOrNull(),
                publisher = publishers.firstOrNull(),
                platforms = buildList {
                    if (platforms?.windows != false) add(Platform.PC)
                    if (platforms?.mac == true) add(Platform.MAC)
                },
                metacritic = metacritic?.score,
                steamAppId = appId,
            )
        }
}

@Serializable
data class SteamSearchResponse(val items: List<SteamSearchItem> = emptyList())

@Serializable
data class SteamSearchItem(val id: Int, val name: String = "", val type: String? = null, val metascore: String? = null)
