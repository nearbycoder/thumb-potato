package com.nearbycoder.thumbpotato

import com.nearbycoder.thumbpotato.data.remote.Http
import com.nearbycoder.thumbpotato.data.remote.SteamAppEnvelope
import com.nearbycoder.thumbpotato.data.remote.SteamClient
import com.nearbycoder.thumbpotato.data.remote.SteamFeatured
import com.nearbycoder.thumbpotato.data.remote.SteamItemsResponse
import com.nearbycoder.thumbpotato.domain.DatePrecision
import com.nearbycoder.thumbpotato.domain.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.time.LocalDate

class SteamTest {
    private val getItems = """
        {"response":{"store_items":[{"item_type":0,"id":1145360,"success":1,"visible":true,"name":"Hades","appid":1145360,
          "assets":{"asset_url_format":"steam/apps/1145360/${'$'}{FILENAME}?t=1758127023","main_capsule":"capsule_616x353.jpg",
            "header":"header.jpg","library_capsule":"library_600x900.jpg","library_capsule_2x":"library_600x900_2x.jpg",
            "library_hero":"library_hero.jpg","library_hero_2x":"library_hero_2x.jpg"},
          "platforms":{"windows":true,"mac":true,"steam_deck_compat_category":3},
          "reviews":{"summary_filtered":{"review_count":287737,"percent_positive":97,"review_score":9,"review_score_label":"Overwhelmingly Positive"}},
          "release":{"steam_release_date":1600353507}}]}}
    """.trimIndent()

    @Test
    fun `builds asset urls from the format`() {
        assertEquals(
            "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1145360/library_600x900_2x.jpg?t=1758127023",
            SteamClient.assetUrl("steam/apps/1145360/\${FILENAME}?t=1758127023", "library_600x900_2x.jpg"),
        )
        assertNull(SteamClient.assetUrl(null, "x.jpg"))
        assertNull(SteamClient.assetUrl("steam/apps/1/\${FILENAME}", null))
    }

    @Test
    fun `decodes GetItems into a game with art, deck and reviews`() {
        val item = Http.json.decodeFromString<SteamItemsResponse>(getItems).response.storeItems.single()
        assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1145360/library_600x900_2x.jpg?t=1758127023", item.capsuleUrl)
        assertEquals("https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1145360/library_hero_2x.jpg?t=1758127023", item.heroUrl)
        assertEquals("Overwhelmingly Positive · 97%", item.reviewLabel)
        val game = item.game
        assertEquals("steam-1145360", game.ref.key)
        assertEquals(3, game.steamDeck)
        assertEquals(listOf(Platform.PC, Platform.MAC), game.platforms)
        assertEquals(LocalDate.of(2020, 9, 17), game.releaseDate)
        assertEquals(item.capsuleUrl, game.boxArtUrl)
    }

    @Test
    fun `GetItems request carries the ids and data request`() {
        val url = SteamClient.itemsUrl(listOf(1145360, 620), "US")
        val decoded = URLDecoder.decode(url, "UTF-8")
        assertTrue(decoded.startsWith("https://api.steampowered.com/IStoreBrowseService/GetItems/v1/?input_json="))
        assertTrue(decoded.contains("""{"appid":1145360}"""))
        assertTrue(decoded.contains("""{"appid":620}"""))
        assertTrue(decoded.contains(""""include_assets":true"""))
        assertTrue(decoded.contains(""""country_code":"US""""))
    }

    @Test
    fun `featured lists dedupe and skip non-games`() {
        val json = """
            {"top_sellers":{"items":[
              {"id":4165890,"name":"Steam Frame","final_price":105900},
              {"id":1,"name":"Game One"},{"id":1,"name":"Game One"},
              {"id":2,"name":"Game Two Demo"},{"id":3,"name":"Game Three Soundtrack"},{"id":4,"name":"Game Four"}]},
             "new_releases":{"items":[]}}
        """.trimIndent()
        val featured = Http.json.decodeFromString<SteamFeatured>(json)
        assertEquals(listOf(1, 4), featured.topSellers.games.map { it.id })
        assertTrue(featured.comingSoon.games.isEmpty())
    }

    @Test
    fun `appdetails maps to a game with trailer, price and mapped genres`() {
        val json = """
            {"1145360":{"success":true,"data":{"name":"Hades","steam_appid":1145360,"short_description":"Defy the god of the dead.",
              "screenshots":[{"path_full":"https://s/1.jpg"}],"movies":[{"name":"Trailer","hls_h264":"https://v/t.m3u8"}],
              "genres":[{"description":"Action"},{"description":"Indie"},{"description":"RPG"}],
              "metacritic":{"score":93},"release_date":{"coming_soon":false,"date":"Sep 17, 2020"},
              "price_overview":{"final_formatted":"${'$'}24.99"},"developers":["Supergiant Games"],"publishers":["Supergiant Games"],
              "website":"http://www.supergiantgames.com","platforms":{"windows":true,"mac":true,"linux":false}}}}
        """.trimIndent()
        val data = Http.json.decodeFromString<Map<String, SteamAppEnvelope>>(json)["1145360"]!!.data!!
        val game = data.game
        assertEquals(listOf("Action", "RPG"), game.genres)
        assertEquals(93, game.metacritic)
        assertEquals(LocalDate.of(2020, 9, 17), game.releaseDate)
        assertEquals("Supergiant Games", game.developer)
        assertEquals("https://v/t.m3u8", data.trailerUrl)
        assertEquals("$24.99", data.priceLabel)
    }

    @Test
    fun `parses steam display dates with precision`() {
        assertEquals(LocalDate.of(2020, 9, 17) to DatePrecision.DAY, SteamClient.parseReleaseDate("Sep 17, 2020"))
        assertEquals(LocalDate.of(2020, 9, 17) to DatePrecision.DAY, SteamClient.parseReleaseDate("17 Sep, 2020"))
        assertEquals(LocalDate.of(2027, 3, 1) to DatePrecision.MONTH, SteamClient.parseReleaseDate("Mar 2027"))
        assertEquals(LocalDate.of(2027, 1, 1) to DatePrecision.YEAR, SteamClient.parseReleaseDate("Q2 2027"))
        assertNull(SteamClient.parseReleaseDate("Coming soon"))
        assertNull(SteamClient.parseReleaseDate(null))
    }

    @Test
    fun `recognizes hardware and extras`() {
        assertFalse(SteamClient.isLikelyGame("Steam Deck"))
        assertFalse(SteamClient.isLikelyGame("Hollow Knight Soundtrack"))
        assertTrue(SteamClient.isLikelyGame("Hades II"))
    }
}
