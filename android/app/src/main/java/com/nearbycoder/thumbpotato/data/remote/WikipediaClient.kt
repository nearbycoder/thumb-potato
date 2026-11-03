package com.nearbycoder.thumbpotato.data.remote

import com.nearbycoder.thumbpotato.domain.DatePrecision
import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.GameRef
import com.nearbycoder.thumbpotato.domain.GameSource
import com.nearbycoder.thumbpotato.domain.Genre
import com.nearbycoder.thumbpotato.domain.Platform
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import java.time.LocalDate

/** Wikipedia (summaries, box art, search) and Wikidata (facts for games outside the catalog). */
class WikipediaClient {

    suspend fun summary(title: String): WikiSummary =
        Http.get("https://en.wikipedia.org/api/rest_v1/page/summary/${encodeTitle(title)}")

    /** Video game articles matching [query], best match first. */
    suspend fun search(query: String, limit: Int = 20): List<Game> {
        val url = "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2&generator=search" +
            "&gsrsearch=" + java.net.URLEncoder.encode("$query hastemplate:\"Infobox video game\"", "UTF-8").replace("+", "%20") +
            "&gsrlimit=$limit&prop=pageimages%7Cpageprops%7Cextracts&piprop=thumbnail&pithumbsize=600&pilicense=any" +
            "&ppprop=wikibase_item&exintro=1&explaintext=1&exsentences=2&exlimit=max"
        return parseSearch(Http.getString(url))
    }

    /** A game built from its Wikidata item, for games that aren't in any downloaded catalog. */
    suspend fun wikidataGame(qid: String): Game {
        val root = Http.json.parseToJsonElement(Http.getString("https://www.wikidata.org/wiki/Special:EntityData/$qid.json")).jsonObject
        val entity = (root["entities"] as? JsonObject)?.values?.firstOrNull()?.jsonObject ?: throw HttpException(404)
        val facts = WikidataFacts.parse(qid, entity)
        val labelIds = (facts.genreIds + facts.developerIds.take(1) + facts.publisherIds.take(1)).distinct()
        val labels = if (labelIds.isEmpty()) emptyMap() else runCatching { labels(labelIds) }.getOrDefault(emptyMap())
        return facts.game(labels)
    }

    private suspend fun labels(ids: List<String>): Map<String, String> {
        val url = "https://www.wikidata.org/w/api.php?action=wbgetentities&format=json&props=labels&languages=en&ids=" + ids.take(40).joinToString("%7C")
        val root = Http.json.parseToJsonElement(Http.getString(url)).jsonObject
        val entities = root["entities"] as? JsonObject ?: return emptyMap()
        return entities.mapNotNull { (id, el) ->
            val label = ((el as? JsonObject)?.get("labels") as? JsonObject)?.get("en")?.let { (it as? JsonObject)?.get("value") as? JsonPrimitive }?.contentOrNull
            label?.let { id to it }
        }.toMap()
    }

    companion object {
        fun encodeTitle(title: String): String =
            java.net.URLEncoder.encode(title.replace(' ', '_'), "UTF-8").replace("+", "%20").replace("%2F", "/")

        /** "Hades (video game)" → "Hades"; "Doom (2016 video game)" → "Doom". */
        fun displayName(title: String): String =
            title.replace(Regex("\\s*\\((\\d{4} )?(\\w+ )?video game\\)$"), "").trim()

        fun parseSearch(text: String): List<Game> {
            val root = Http.json.parseToJsonElement(text).jsonObject
            val pages = ((root["query"] as? JsonObject)?.get("pages") as? JsonArray) ?: return emptyList()
            return pages.mapNotNull { it as? JsonObject }
                .sortedBy { (it["index"] as? JsonPrimitive)?.intOrNull ?: Int.MAX_VALUE }
                .mapNotNull { page ->
                    val title = page.str("title") ?: return@mapNotNull null
                    val qid = (page["pageprops"] as? JsonObject)?.str("wikibase_item") ?: return@mapNotNull null
                    Game(
                        ref = GameRef(GameSource.WD, qid),
                        name = displayName(title),
                        summary = page.str("extract") ?: "",
                        coverUrl = (page["thumbnail"] as? JsonObject)?.str("source"),
                        wikiTitle = title.replace(' ', '_'),
                    )
                }
        }

        internal fun JsonObject.str(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
    }
}

@Serializable
data class WikiSummary(
    val title: String = "",
    val extract: String? = null,
    val thumbnail: Image? = null,
    val originalimage: Image? = null,
    @kotlinx.serialization.SerialName("wikibase_item") val wikibaseItem: String? = null,
) {
    @Serializable
    data class Image(val source: String? = null, val width: Int? = null, val height: Int? = null)

    /** Box art: the original upload when it's portrait-ish, else the thumbnail. */
    val imageUrl: String? get() = originalimage?.source ?: thumbnail?.source
}

/** The handful of Wikidata claims a game page needs (P400, P577, P136, P178, P123, P1733). */
data class WikidataFacts(
    val qid: String,
    val label: String?,
    val wikiTitle: String?,
    val platforms: List<Platform>,
    val releaseDate: LocalDate?,
    val precision: DatePrecision,
    val genreIds: List<String>,
    val developerIds: List<String>,
    val publisherIds: List<String>,
    val steamAppId: Int?,
) {
    fun game(labels: Map<String, String>): Game = Game(
        ref = GameRef(GameSource.WD, qid),
        name = wikiTitle?.let { WikipediaClient.displayName(it.replace('_', ' ')) } ?: label ?: qid,
        releaseDate = releaseDate,
        releaseDatePrecision = precision,
        genres = Genre.mapAll(genreIds.mapNotNull { labels[it] }),
        developer = developerIds.firstNotNullOfOrNull { labels[it] },
        publisher = publisherIds.firstNotNullOfOrNull { labels[it] },
        platforms = platforms,
        steamAppId = steamAppId,
        wikiTitle = wikiTitle,
    )

    companion object {
        fun parse(qid: String, entity: JsonObject): WikidataFacts {
            val claims = entity["claims"] as? JsonObject ?: JsonObject(emptyMap())
            fun values(prop: String): List<JsonElement> =
                (claims[prop] as? JsonArray)?.mapNotNull { claim ->
                    ((claim as? JsonObject)?.get("mainsnak") as? JsonObject)?.get("datavalue")?.let { (it as? JsonObject)?.get("value") }
                } ?: emptyList()
            fun ids(prop: String): List<String> = values(prop).mapNotNull { (it as? JsonObject)?.get("id")?.let { id -> (id as? JsonPrimitive)?.contentOrNull } }

            // Earliest P577 publication date, with its precision (9 year, 10 month, 11 day).
            val dates = values("P577").mapNotNull { v ->
                val o = v as? JsonObject ?: return@mapNotNull null
                val time = (o["time"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                if (time.startsWith("-")) return@mapNotNull null
                val p = (o["precision"] as? JsonPrimitive)?.intOrNull ?: 11
                val precision = when (p) { 9 -> DatePrecision.YEAR; 10 -> DatePrecision.MONTH; else -> if (p >= 11) DatePrecision.DAY else return@mapNotNull null }
                val raw = time.removePrefix("+").take(10)
                val y = raw.take(4).toIntOrNull() ?: return@mapNotNull null
                val m = raw.substring(5, 7).toIntOrNull()?.coerceIn(1, 12) ?: 1
                val d = raw.substring(8, 10).toIntOrNull()?.coerceAtLeast(1) ?: 1
                val date = when (precision) {
                    DatePrecision.YEAR -> LocalDate.of(y, 1, 1)
                    DatePrecision.MONTH -> LocalDate.of(y, m, 1)
                    DatePrecision.DAY -> runCatching { LocalDate.of(y, m, d) }.getOrNull() ?: return@mapNotNull null
                }
                date to precision
            }
            val earliest = dates.minWithOrNull(compareBy<Pair<LocalDate, DatePrecision>> { it.first }.thenBy { it.second.ordinal })
            val enwiki = ((entity["sitelinks"] as? JsonObject)?.get("enwiki") as? JsonObject)?.get("title")?.let { (it as? JsonPrimitive)?.contentOrNull }
            val label = (((entity["labels"] as? JsonObject)?.get("en") as? JsonObject)?.get("value") as? JsonPrimitive)?.contentOrNull
            val steam = values("P1733").firstNotNullOfOrNull { (it as? JsonPrimitive)?.contentOrNull?.toIntOrNull() }
            return WikidataFacts(
                qid = qid,
                label = label,
                wikiTitle = enwiki?.replace(' ', '_'),
                platforms = ids("P400").mapNotNull { Platform.withWikidataId(it) }.distinct().sortedBy { it.ordinal },
                releaseDate = earliest?.first,
                precision = earliest?.second ?: DatePrecision.DAY,
                genreIds = ids("P136"),
                developerIds = ids("P178"),
                publisherIds = ids("P123"),
                steamAppId = steam,
            )
        }
    }
}
