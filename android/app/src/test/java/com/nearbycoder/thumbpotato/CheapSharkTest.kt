package com.nearbycoder.thumbpotato

import com.nearbycoder.thumbpotato.data.remote.CheapSharkClient
import com.nearbycoder.thumbpotato.data.remote.CsDealListing
import com.nearbycoder.thumbpotato.data.remote.CsGame
import com.nearbycoder.thumbpotato.data.remote.CsLookup
import com.nearbycoder.thumbpotato.data.remote.CsStore
import com.nearbycoder.thumbpotato.data.remote.Http
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CheapSharkTest {
    private val game = """
        {"info":{"title":"Hades","steamAppID":"1145360"},"cheapestPriceEver":{"price":"6.24","date":1758129350},
         "deals":[
          {"storeID":"1","dealID":"steam%3D","price":"24.99","retailPrice":"24.99","savings":"0.000000"},
          {"storeID":"7","dealID":"gog%3D","price":"12.49","retailPrice":"24.99","savings":"50.020008"},
          {"storeID":"25","dealID":"epic%3D","price":"9.99","retailPrice":"24.99","savings":"60.024010"},
          {"storeID":"99","dealID":"x","price":"oops","retailPrice":"24.99","savings":"0"}]}
    """.trimIndent()

    @Test
    fun `deals come back cheapest first with store names and links`() {
        val stores = Http.json.decodeFromString<List<CsStore>>(
            """[{"storeID":"1","storeName":"Steam","isActive":1},{"storeID":"7","storeName":"GOG","isActive":1},{"storeID":"25","storeName":"Epic Games Store","isActive":1}]""",
        ).associate { it.storeID to it.storeName }
        val deals = CheapSharkClient.parseDeals(Http.json.decodeFromString<CsGame>(game), stores)
        assertEquals(listOf("Epic Games Store", "GOG", "Steam"), deals.map { it.storeName })
        assertEquals(listOf(9.99, 12.49, 24.99), deals.map { it.price })
        val epic = deals.first()
        assertTrue(epic.isDiscounted)
        assertEquals("$9.99", epic.priceLabel)
        assertEquals("$24.99", epic.retailLabel)
        assertEquals(60, epic.savings.toInt())
        assertEquals("https://www.cheapshark.com/redirect?dealID=epic%3D", epic.url)
        assertFalse(deals.last().isDiscounted)
    }

    @Test
    fun `limits and unknown stores`() {
        val deals = CheapSharkClient.parseDeals(Http.json.decodeFromString<CsGame>(game), emptyMap(), limit = 2)
        assertEquals(2, deals.size)
        assertEquals("Store 25", deals.first().storeName)
    }

    @Test
    fun `lookup and listings`() {
        val hits = Http.json.decodeFromString<List<CsLookup>>(
            """[{"gameID":"196149","steamAppID":"1145360","cheapest":"24.99","cheapestDealID":"x","external":"Hades","internalName":"HADES","thumb":"t"}]""",
        )
        assertEquals("196149", hits.single().gameID)
        val listing = Http.json.decodeFromString<List<CsDealListing>>(
            """[{"title":"Sorry Were Closed","dealID":"d","storeID":"25","salePrice":"0.00","normalPrice":"24.99","savings":"100.000000","metacriticScore":"83","steamAppID":"1796580"},
                {"title":"No Steam","dealID":"e","storeID":"1","salePrice":"1.00","normalPrice":"2.00","savings":"50","steamAppID":null},
                {"title":"Zero","dealID":"f","storeID":"1","salePrice":"1.00","normalPrice":"2.00","savings":"50","steamAppID":"0"}]""",
        )
        assertEquals(1796580, listing[0].steamId)
        assertNull(listing[1].steamId)
        assertNull(listing[2].steamId)
    }
}
