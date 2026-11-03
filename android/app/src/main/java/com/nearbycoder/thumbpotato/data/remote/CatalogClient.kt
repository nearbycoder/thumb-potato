package com.nearbycoder.thumbpotato.data.remote

import com.nearbycoder.thumbpotato.domain.DatePrecision
import com.nearbycoder.thumbpotato.domain.Game
import com.nearbycoder.thumbpotato.domain.GameRef
import com.nearbycoder.thumbpotato.domain.GameSource
import com.nearbycoder.thumbpotato.domain.Platform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

/** One record of `catalog/v1/<platform>.json`, built weekly by scripts/build_catalog.py. */
@Serializable
data class CatalogRecord(
    val id: String,
    val name: String,
    val date: String? = null,
    val datePrecision: String? = null,
    val genres: List<String> = emptyList(),
    val dev: String? = null,
    val pub: String? = null,
    val cover: String? = null,
    val art: String? = null,
    val hero: String? = null,
    val deck: Int? = null,
    val summary: String? = null,
    val wiki: String? = null,
    val steam: Int? = null,
    val metacritic: Int? = null,
    val pop: Double = 0.0,
    val platforms: List<String> = emptyList(),
) {
    val game: Game
        get() {
            val day = DateParsing.date(date)
            return Game(
                ref = GameRef(GameSource.WD, id),
                name = name,
                summary = summary ?: "",
                coverUrl = cover,
                artUrl = art,
                heroUrl = hero,
                releaseDate = day,
                releaseDatePrecision = DatePrecision.fromRaw(datePrecision),
                year = day?.year,
                genres = genres,
                developer = dev,
                publisher = pub,
                platforms = platforms.mapNotNull { Platform.withId(it) }.sortedBy { it.ordinal },
                metacritic = metacritic,
                steamAppId = steam,
                wikiTitle = wiki,
                popularity = pop,
                steamDeck = deck,
            )
        }
}

@Serializable
data class CatalogIndex(val updated: String? = null, val platforms: Map<String, Int> = emptyMap())

/**
 * The per-platform catalog on GitHub Pages. Files are cached on disk for 24 hours;
 * a stale copy is served when the network fails.
 */
class CatalogClient(private val cacheDir: File?) {

    suspend fun platform(platform: Platform): List<Game> =
        decode(fetch("${platform.id}.json")).map { it.game }

    suspend fun index(): CatalogIndex = Http.json.decodeFromString(CatalogIndex.serializer(), fetch("index.json"))

    /** Platforms whose file is already on disk (for instant local search). */
    fun cachedPlatforms(): List<Platform> =
        Platform.entries.filter { cacheFile("${it.id}.json")?.exists() == true }

    private fun cacheFile(name: String): File? = cacheDir?.let { File(it, name) }

    private suspend fun fetch(name: String): String = withContext(Dispatchers.IO) {
        val file = cacheFile(name)
        val fresh = file != null && file.exists() && System.currentTimeMillis() - file.lastModified() < TTL_MILLIS
        if (fresh) return@withContext file.readText()
        try {
            val text = Http.getString("$BASE/$name")
            if (file != null) {
                file.parentFile?.mkdirs()
                val tmp = File(file.parentFile, "$name.tmp")
                tmp.writeText(text)
                tmp.renameTo(file)
            }
            text
        } catch (e: Exception) {
            if (file != null && file.exists()) file.readText() else throw e
        }
    }

    companion object {
        const val BASE = "https://nearbycoder.github.io/thumb-potato/catalog/v1"
        private const val TTL_MILLIS = 24L * 60 * 60 * 1000

        fun decode(text: String): List<CatalogRecord> =
            Http.json.decodeFromString(ListSerializer(CatalogRecord.serializer()), text)
    }
}
