import XCTest
import SwiftData
@testable import ThumbPotato

final class ThumbPotatoTests: XCTestCase {
    // MARK: Refs

    func testGameRefRoundTripsThroughKey() {
        let ref = GameRef(source: .wd, id: "Q64577191")
        XCTAssertEqual(ref.key, "wd-Q64577191")
        XCTAssertEqual(GameRef(key: ref.key), ref)
        XCTAssertEqual(GameRef(key: "steam-1145360"), GameRef(source: .steam, id: "1145360"))
        XCTAssertEqual(GameRef(key: "rawg-3498")?.source, .rawg)
        XCTAssertEqual(ref.deepLink.absoluteString, "thumbpotato://game/wd-Q64577191")
        XCTAssertNil(GameRef(key: "nope"))
        XCTAssertNil(GameRef(key: "tmdb-1"))
        XCTAssertNil(GameRef(key: "wd-"))
    }

    // MARK: Catalog decoding

    func testCatalogRecordsDecode() throws {
        // The shape scripts/build_catalog.py writes to docs/catalog/v1/<platform>.json.
        let json = """
        [{"id":"Q64577191","name":"The Legend of Zelda: Tears of the Kingdom","date":"2023-05-12",
          "genres":["Action","Adventure"],"dev":"Nintendo EPD","pub":"Nintendo",
          "cover":"https://upload.wikimedia.org/totk.jpg","deck":3,"summary":"Two sentences.",
          "wiki":"The_Legend_of_Zelda:_Tears_of_the_Kingdom","steam":1145360,"metacritic":96,"pop":52,
          "platforms":["switch","switch2"]},
         {"id":"Q1","name":"Bare","wiki":"Bare","pop":3,"platforms":["pc"]},
         {"id":"Q2","name":"Someday","date":"2027-01-01","datePrecision":"year","wiki":"Someday","pop":90,"platforms":["ps5"],
          "art":"https://shared.akamai.steamstatic.com/capsule.jpg","hero":"https://shared.akamai.steamstatic.com/hero.jpg"}]
        """
        let records = try JSONDecoder().decode([CatalogRecord].self, from: Data(json.utf8))
        let games = CatalogStore.decode(records)
        // Sorted by popularity.
        XCTAssertEqual(games.map(\.ref.key), ["wd-Q2", "wd-Q64577191", "wd-Q1"])
        let totk = games[1]
        XCTAssertEqual(totk.year, 2023)
        XCTAssertEqual(totk.genres, ["Action", "Adventure"])
        XCTAssertEqual(totk.metacritic, 96)
        XCTAssertEqual(totk.steamAppID, 1145360)
        XCTAssertEqual(totk.steamDeck, 3)
        XCTAssertEqual(totk.platforms, ["switch", "switch2"])
        XCTAssertEqual(totk.boxArtURL?.lastPathComponent, "totk.jpg")
        XCTAssertEqual(totk.releaseDatePrecision, .day)
        XCTAssertNil(games[2].boxArtURL)
        // Box art falls back to the Steam capsule.
        XCTAssertEqual(games[0].boxArtURL?.lastPathComponent, "capsule.jpg")
        XCTAssertEqual(games[0].releaseDatePrecision, .year)
    }

    func testBundledDevCatalogDecodesIfPresent() throws {
        // docs/catalog/v1/switch.json in the repo (dev copy); skipped when not reachable from the test host.
        let url = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
            .appendingPathComponent("docs/catalog/v1/switch.json")
        guard let data = try? Data(contentsOf: url) else { throw XCTSkip("dev catalog not available") }
        let games = CatalogStore.decode(try JSONDecoder().decode([CatalogRecord].self, from: data))
        XCTAssertGreaterThan(games.count, 100)
        XCTAssertTrue(games.allSatisfy { $0.platforms.contains("switch") })
        let allowed = Set(Genre.allCases.map(\.rawValue))
        XCTAssertTrue(games.allSatisfy { Set($0.genres).isSubset(of: allowed) })
    }

    // MARK: Release dates

    func testCoarseReleaseDatesFormatWithoutADay() throws {
        let year = try XCTUnwrap(DateParsing.date("2027-01-01"))
        let month = try XCTUnwrap(DateParsing.date("2027-03-01"))
        XCTAssertEqual(ReleaseDateFormat.label(year, precision: .year), "2027")
        XCTAssertEqual(ReleaseDateFormat.radarLabel(year, precision: .year), "TBA 2027")
        XCTAssertTrue(ReleaseDateFormat.label(month, precision: .month).contains("March"))
        XCTAssertFalse(ReleaseDateFormat.label(month, precision: .month).contains("1,"))
        XCTAssertNil(ReleaseDateFormat.countdown(year, precision: .year))
        // No release-day alerts for coarse dates.
        let now = try XCTUnwrap(DateParsing.date("2026-11-03"))
        XCTAssertNil(Reminders.releaseFireDate(year, precision: .year, now: now))
        XCTAssertNil(Reminders.releaseFireDate(month, precision: .month, now: now))
        let exact = try XCTUnwrap(Reminders.releaseFireDate(DateParsing.date("2026-12-10"), precision: .day, now: now))
        XCTAssertEqual(Calendar.current.component(.hour, from: exact), 9)
        XCTAssertNil(Reminders.releaseFireDate(DateParsing.date("2026-10-01"), precision: .day, now: now))
        // "2026" is still upcoming in November 2026; "March 2026" isn't.
        XCTAssertTrue(ReleaseDateFormat.isUpcoming(try XCTUnwrap(DateParsing.date("2026-01-01")), precision: .year, now: now))
        XCTAssertFalse(ReleaseDateFormat.isUpcoming(try XCTUnwrap(DateParsing.date("2026-03-01")), precision: .month, now: now))
        XCTAssertEqual(ReleaseDateFormat.countdown(try XCTUnwrap(DateParsing.date("2026-11-15")), precision: .day, now: now), "in 12 days")
    }

    func testSteamReleaseDatesParseWithPrecision() {
        XCTAssertEqual(DateParsing.steam("Sep 17, 2020")?.1, .day)
        XCTAssertEqual(DateParsing.steam("March 2027")?.1, .month)
        XCTAssertEqual(DateParsing.steam("2027")?.1, .year)
        XCTAssertNil(DateParsing.steam("Coming soon"))
    }

    // MARK: Genres, moods, sessions

    func testGenreMappingOntoFixedList() {
        XCTAssertEqual(Genre.map(["Role-playing (RPG)", "Massively Multiplayer"]), ["RPG"])
        XCTAssertEqual(Genre.map(["Action", "Indie", "RPG"]), ["Action", "RPG"])
        XCTAssertEqual(Genre.map(["Casual"]), ["Puzzle"])
        XCTAssertEqual(Genre.map(["Casual", "Racing"]), ["Racing"])
        XCTAssertEqual(Genre.map(["action roguelike", "platform game", "survival horror"]), ["Roguelike", "Platformer", "Horror"])
        XCTAssertEqual(Genre.map(["Free to Play"]), [])
    }

    private func game(_ name: String, year: Int? = nil, date: String? = nil, precision: DatePrecision = .day,
                      genres: [String] = [], platforms: [String] = ["switch"], score: Int? = nil, pop: Double = 0,
                      steam: Int? = nil, source: GameSource = .wd) -> Game {
        let d = date ?? year.map { "\($0)-06-01" }
        return Game(ref: GameRef(source: source, id: source == .steam ? String(steam ?? 0) : name), name: name,
                    coverURL: URL(string: "https://example.com/\(name.count).jpg"), releaseDate: DateParsing.date(d), releaseDatePrecision: precision, year: year ?? DateParsing.year(d),
                    genres: genres, platforms: platforms, metacritic: score, steamAppID: steam, popularity: pop)
    }

    func testMoodsAndSessionLengthsMapToGenres() {
        XCTAssertEqual(Mood.cozy.genres, [.simulation, .sandbox, .puzzle, .party, .music])
        XCTAssertEqual(Mood.spooky.genres, [.horror, .survival])
        XCTAssertEqual(Mood.coop.title, "Couch Co-op")
        XCTAssertTrue(Mood.epic.matches(game("A", genres: ["RPG"])))
        XCTAssertFalse(Mood.epic.matches(game("B", genres: ["Puzzle"])))
        XCTAssertEqual(SessionLength.quick.label, "Quick break")
        XCTAssertTrue(SessionLength.quick.matches(game("C", genres: ["Party"])))
        XCTAssertTrue(SessionLength.long.matches(game("D", genres: ["Visual Novel"])))
        XCTAssertFalse(SessionLength.hour.matches(game("E", genres: ["Strategy"])))
        XCTAssertEqual(Set(SessionLength.hour.genres.map(\.rawValue)),
                       ["Action", "Shooter", "Metroidvania", "Adventure", "Horror", "Platformer", "Roguelike"])
    }

    // MARK: Filters

    func testGameFilterNarrowsAndSorts() {
        let games = [
            game("Bravo", year: 2015, genres: ["RPG"], score: 75, pop: 40),
            game("alpha", year: 2023, genres: ["Puzzle"], score: 91, pop: 30),
            game("Charlie", year: 1998, genres: ["Racing"], pop: 20),
            game("Delta", year: 2021, genres: ["Puzzle", "Party"], score: 68, pop: 10),
            game("Echo", date: "2099-01-01", precision: .year, genres: ["RPG"], pop: 50),
        ]
        XCTAssertEqual(GameFilter().apply(games).map(\.name), ["Echo", "Bravo", "alpha", "Charlie", "Delta"])
        XCTAssertEqual(GameFilter(released: .y2020s).apply(games).map(\.name), ["alpha", "Delta"])
        XCTAssertEqual(GameFilter(released: .older).apply(games).map(\.name), ["Charlie"])
        XCTAssertEqual(GameFilter(released: .upcoming).apply(games).map(\.name), ["Echo"])
        XCTAssertEqual(GameFilter(genre: .puzzle).apply(games).map(\.name), ["alpha", "Delta"])
        XCTAssertEqual(GameFilter(minScore: 70).apply(games).map(\.name), ["Bravo", "alpha"])
        // Newest drops unreleased games unless the list is Upcoming.
        XCTAssertEqual(GameFilter(sort: .newest).apply(games).map(\.name), ["alpha", "Delta", "Bravo", "Charlie"])
        XCTAssertEqual(GameFilter(sort: .rating).apply(games).map(\.name), ["alpha", "Bravo", "Delta", "Echo", "Charlie"])
        XCTAssertEqual(GameFilter(sort: .title).apply(games).map(\.name), ["alpha", "Bravo", "Charlie", "Delta", "Echo"])
        XCTAssertTrue(GameFilter().isDefault)
        XCTAssertNotEqual(GameFilter(minScore: 80).key, GameFilter().key)
    }

    func testGameFilterBuildsRAWGQueries() throws {
        let now = try XCTUnwrap(DateParsing.date("2026-11-03"))
        let q = GameFilter(sort: .newest, released: .y2010s, genre: .rpg, minScore: 80).rawgQuery(now: now)
        XCTAssertEqual(q["ordering"], "-released")
        XCTAssertEqual(q["dates"], "2010-01-01,2019-12-31")
        XCTAssertEqual(q["genres"], "role-playing-games-rpg")
        XCTAssertEqual(q["metacritic"], "80,100")
        XCTAssertEqual(GameFilter(released: .upcoming).rawgQuery(now: now)["dates"], "2026-11-04,2099-12-31")
        XCTAssertEqual(GameFilter(sort: .title).rawgQuery(now: now)["ordering"], "name")
    }

    func testCatalogPagingSlicesLocally() {
        let games = (0..<130).map { game("G\($0)") }
        XCTAssertEqual(Catalog.slice(games, page: 1).count, 60)
        XCTAssertEqual(Catalog.slice(games, page: 3).count, 10)
        XCTAssertTrue(Catalog.slice(games, page: 4).isEmpty)
        XCTAssertTrue(Catalog.slice(games, page: 0).isEmpty)
    }

    // MARK: Steam

    func testSteamAssetURLsAndItemsRequest() throws {
        let url = SteamClient.assetURL(format: "steam/apps/1145360/${FILENAME}?t=1758127023", file: "library_600x900_2x.jpg")
        XCTAssertEqual(url?.absoluteString,
                       "https://shared.akamai.steamstatic.com/store_item_assets/steam/apps/1145360/library_600x900_2x.jpg?t=1758127023")
        XCTAssertNil(SteamClient.assetURL(format: nil, file: "x.jpg"))

        let request = SteamClient.itemsURL([10, 20], country: "US")
        let input = try XCTUnwrap(URLComponents(url: request, resolvingAgainstBaseURL: false)?
            .queryItems?.first { $0.name == "input_json" }?.value)
        let parsed = try XCTUnwrap(JSONSerialization.jsonObject(with: Data(input.utf8)) as? [String: Any])
        XCTAssertEqual((parsed["ids"] as? [[String: Int]])?.compactMap { $0["appid"] }, [10, 20])
        XCTAssertEqual((parsed["data_request"] as? [String: Bool])?["include_assets"], true)
    }

    func testSteamItemDecodesArtDeckAndReviews() throws {
        let json = """
        {"response":{"store_items":[{"id":1145360,"appid":1145360,"success":1,"name":"Hades",
          "reviews":{"summary_filtered":{"percent_positive":97,"review_score_label":"Overwhelmingly Positive"}},
          "basic_info":{"short_description":"Defy the god of the dead.","developers":[{"name":"Supergiant Games"}]},
          "assets":{"asset_url_format":"steam/apps/1145360/${FILENAME}?t=1","library_capsule_2x":"library_600x900_2x.jpg","library_hero_2x":"library_hero_2x.jpg"},
          "release":{"steam_release_date":1600353507},
          "platforms":{"windows":true,"mac":true,"steam_deck_compat_category":3}}]}}
        """
        let response = try JSONDecoder().decode(SteamItemsResponse.self, from: Data(json.utf8))
        let item = try XCTUnwrap(response.response?.store_items?.first)
        let game = try XCTUnwrap(item.game)
        XCTAssertEqual(game.ref.key, "steam-1145360")
        XCTAssertEqual(game.platforms, ["pc", "mac"])
        XCTAssertEqual(game.steamDeck, 3)
        XCTAssertEqual(game.year, 2020)
        XCTAssertEqual(game.developer, "Supergiant Games")
        XCTAssertEqual(game.boxArtURL?.lastPathComponent, "library_600x900_2x.jpg")
        XCTAssertEqual(game.heroURL?.lastPathComponent, "library_hero_2x.jpg")
        XCTAssertEqual(item.reviewLine, "Overwhelmingly Positive · 97%")
    }

    func testSteamFeaturedDropsHardwareDemosAndRepeats() {
        let items = [
            SteamFeatured.Item(id: 1, name: "Steam Frame"),
            SteamFeatured.Item(id: 2, name: "Hades II"),
            SteamFeatured.Item(id: 2, name: "Hades II"),
            SteamFeatured.Item(id: 3, name: "Cool Game Demo"),
            SteamFeatured.Item(id: 4, name: "Cool Game Soundtrack"),
        ]
        XCTAssertEqual(SteamClient.cleaned(items).map(\.id), [2])
    }

    func testCheapSharkDealsSortCheapestFirst() {
        let raw = [
            CSGameInfo.RawDeal(storeID: "1", dealID: "a%2Fb", price: "19.99", retailPrice: "24.99", savings: "20.0"),
            CSGameInfo.RawDeal(storeID: "7", dealID: "c", price: "9.99", retailPrice: "24.99", savings: "60.0"),
        ]
        let deals = CheapSharkClient.deals(raw, storeNames: ["1": "Steam", "7": "GOG"])
        XCTAssertEqual(deals.map(\.storeName), ["GOG", "Steam"])
        XCTAssertTrue(deals[0].isDiscounted)
        XCTAssertEqual(deals[1].link.absoluteString, "https://www.cheapshark.com/redirect?dealID=a%2Fb")
    }

    // MARK: Stores

    func testStoresFollowPlatformsMineFirst() {
        var g = game("Hades", platforms: ["switch", "pc", "ps5"], steam: 1145360)
        g.name = "Hades & Co"
        let offers = Store.offers(for: g, myPlatforms: ["ps5"])
        XCTAssertEqual(offers.first?.store, .playstation)
        XCTAssertTrue(offers.contains { $0.store == .nintendo })
        XCTAssertFalse(offers.contains { $0.store == .googlePlay })
        XCTAssertEqual(offers.first { $0.store == .steam }?.link.absoluteString, "https://store.steampowered.com/app/1145360")
        // Names are query-escaped, including "&".
        XCTAssertTrue(offers.first { $0.store == .gog }?.link.absoluteString.contains("Hades%20%26%20Co") == true)
    }

    // MARK: Search

    func testSearchMergeDedupesByQIDSteamIDAndName() {
        let record = game("Hades", year: 2020, genres: ["Roguelike"], platforms: ["switch", "pc"], pop: 80, steam: 1145360)
        var wikiHades = game("Hades", platforms: [])
        wikiHades.ref = record.ref
        let wikiOther = game("Hades II", platforms: [])
        let steamHades = game("Hades", platforms: ["pc"], steam: 1145360, source: .steam)
        let steamHades2 = game("Hades II", platforms: ["pc"], steam: 1145350, source: .steam)
        let steamNew = game("Hadestown", platforms: ["pc"], steam: 999, source: .steam)
        let merged = Catalog.mergeSearch(local: [record], wiki: [wikiHades, wikiOther], steam: [steamHades, steamHades2, steamNew],
                                         catalogByQID: [record.ref.id: record])
        XCTAssertEqual(merged.map(\.name), ["Hades", "Hades II", "Hadestown"])
        // The Wikipedia hit became the richer catalog record.
        XCTAssertEqual(merged[0].genres, ["Roguelike"])
        XCTAssertEqual(merged[0].ref.source, .wd)
        XCTAssertEqual(merged[1].ref.source, .wd)
    }

    func testLocalSearchPrefersPrefixMatches() {
        let games = [game("Super Mario Odyssey"), game("Mario Kart 8 Deluxe"), game("Zelda")]
        XCTAssertEqual(Catalog.localMatches("mario", in: games).map(\.name), ["Mario Kart 8 Deluxe", "Super Mario Odyssey"])
        XCTAssertEqual(WikipediaClient.cleanTitle("Hades (video game)"), "Hades")
        XCTAssertEqual(WikipediaClient.cleanTitle("Doom (2016 video game)"), "Doom")
    }

    func testTonightRowsFilterByDate() throws {
        let now = try XCTUnwrap(DateParsing.date("2026-11-03"))
        let games = [
            game("Recent", date: "2026-09-01", pop: 5),
            game("Old", date: "2020-01-01", pop: 9),
            game("Soon", date: "2026-12-10", pop: 1),
            game("Later", date: "2027-01-01", precision: .year, pop: 3),
        ]
        XCTAssertEqual(Catalog.newReleases(games, now: now).map(\.name), ["Recent"])
        XCTAssertEqual(Catalog.comingSoon(games).map(\.name), ["Soon", "Later"])
    }

    // MARK: Backup

    @MainActor
    private func memoryContext() throws -> ModelContext {
        let schema = Schema([SavedGame.self, PlaySession.self])
        let container = try ModelContainer(for: schema, configurations: ModelConfiguration(schema: schema, isStoredInMemoryOnly: true, cloudKitDatabase: .none))
        return ModelContext(container)
    }

    @MainActor
    func testBackupImportsAndroidFilesAndRoundTrips() throws {
        let context = try memoryContext()
        // What the Android app writes: fractional-second ISO dates, epoch-millis session start.
        let android = """
        {"format":"thumbpotato.backup","version":1,"exportedAt":"2026-11-03T20:35:33.123Z",
         "games":[{"key":"wd-Q64577191","source":"wd","externalID":"Q64577191","name":"The Legend of Zelda: Tears of the Kingdom",
                   "summary":"Link returns.","coverURL":"https://upload.wikimedia.org/totk.jpg","heroURL":null,"releaseDate":"2023-05-12",
                   "year":2023,"genres":["Action","Adventure"],"platformIDs":["switch","switch2"],"playingOn":"switch","status":"playing",
                   "rating":4,"notes":"Sky islands","hoursPlayed":12.5,"progress":40,"owned":true,"isFollowing":false,"steamAppID":null,
                   "wikiTitle":"The_Legend_of_Zelda:_Tears_of_the_Kingdom","metacritic":96,
                   "addedAt":"2026-10-01T10:00:00.5Z","updatedAt":"2026-10-02T10:00:00Z","finishedAt":null},
                  {"key":"wd-Q2","source":"wd","externalID":"Q2","name":"Someday","releaseDate":"2027-01-01","releaseDatePrecision":"year",
                   "status":"queued","isFollowing":true,"updatedAt":"2026-10-02T10:00:00Z"}],
         "sessions":[{"id":"7E57D004-2B97-4E0B-A8C2-0B2C8A3F9A11","gameKey":"wd-Q64577191","gameName":"Tears of the Kingdom",
                      "coverURL":null,"platformID":"switch","startsAt":1793750400000,"durationMinutes":120,"note":"","hasReminder":true,
                      "isDone":false,"createdAt":"2026-10-02T10:00:00Z"}]}
        """
        let first = try Backup.merge(Data(android.utf8), into: context)
        XCTAssertEqual(first.added, 2)
        XCTAssertEqual(first.sessions, 1)
        let totk = try XCTUnwrap(Library.saved("wd-Q64577191", in: context))
        XCTAssertEqual(totk.status, .playing)
        XCTAssertEqual(totk.rating, 4)
        XCTAssertEqual(totk.hoursPlayed, 12.5)
        XCTAssertEqual(totk.progress, 40)
        XCTAssertTrue(totk.owned)
        XCTAssertEqual(totk.playingOn, "switch")
        XCTAssertEqual(totk.platformIDs, ["switch", "switch2"])
        XCTAssertEqual(totk.releaseDatePrecision, .day)
        let someday = try XCTUnwrap(Library.saved("wd-Q2", in: context))
        XCTAssertEqual(someday.releaseDatePrecision, .year)
        XCTAssertFalse(someday.wantsReleaseAlert)

        // Importing the same file again changes nothing; an older copy never overwrites a newer one.
        let again = try Backup.merge(Data(android.utf8), into: context)
        XCTAssertEqual(again.added + again.updated + again.sessions, 0)

        // Round trip through our own export keeps everything, including the coarse precision.
        let exported = try Backup.export(games: try context.fetch(FetchDescriptor<SavedGame>()),
                                         sessions: try context.fetch(FetchDescriptor<PlaySession>()))
        let text = String(decoding: exported, as: UTF8.self)
        XCTAssertTrue(text.contains("\"format\" : \"thumbpotato.backup\""))
        XCTAssertTrue(text.contains("\"releaseDate\" : \"2023-05-12\""))
        XCTAssertTrue(text.contains("\"releaseDatePrecision\" : \"year\""))
        let other = try memoryContext()
        let copied = try Backup.merge(exported, into: other)
        XCTAssertEqual(copied.added, 2)
        XCTAssertEqual(copied.sessions, 1)
        XCTAssertEqual(Library.saved("wd-Q64577191", in: other)?.hoursPlayed, 12.5)
        XCTAssertEqual(Library.saved("wd-Q2", in: other)?.releaseDatePrecision, .year)
        let session = try XCTUnwrap(try other.fetch(FetchDescriptor<PlaySession>()).first)
        XCTAssertEqual(session.id.uuidString, "7E57D004-2B97-4E0B-A8C2-0B2C8A3F9A11")
        XCTAssertEqual(session.platformID, "switch")
        XCTAssertEqual(session.durationMinutes, 120)
    }

    @MainActor
    func testBackupRejectsOtherFormats() throws {
        let context = try memoryContext()
        let couch = #"{"format":"couchpotato.backup","version":1,"exportedAt":"2026-11-03T20:00:00Z","games":[],"sessions":[]}"#
        XCTAssertThrowsError(try Backup.merge(Data(couch.utf8), into: context))
    }

    @MainActor
    func testDedupeKeepsNewestAndFoldsProgress() throws {
        let context = try memoryContext()
        let a = SavedGame(from: game("Hades"))
        a.hoursPlayed = 30
        a.updatedAt = .distantPast
        let b = SavedGame(from: game("Hades"))
        b.rating = 5
        context.insert(a)
        context.insert(b)
        Library.dedupe(in: context)
        let all = try context.fetch(FetchDescriptor<SavedGame>())
        XCTAssertEqual(all.count, 1)
        XCTAssertEqual(all[0].rating, 5)
        XCTAssertEqual(all[0].hoursPlayed, 30)
    }

    func testDeepLinksRoute() {
        XCTAssertEqual(Platform.with(id: "xbox-series")?.monogram, "XS")
        XCTAssertEqual(Platform.all.count, 9)
        XCTAssertEqual(Platform.matching(name: "Nintendo Switch 2"), .switch2)
        XCTAssertEqual(Platform.matching(name: "PlayStation 5"), .ps5)
        XCTAssertEqual(Platform.matching(name: "Xbox Series S/X"), .xboxSeries)
        XCTAssertEqual(Platform.matching(name: "iOS"), .mobile)
    }

    @MainActor
    func testAppModelHandlesDeepLinks() {
        let app = AppModel()
        app.handle(url: URL(string: "thumbpotato://shuffle")!)
        XCTAssertEqual(app.selection, .tab(.shuffle))
        app.handle(url: URL(string: "thumbpotato://backlog")!)
        XCTAssertEqual(app.selection, .tab(.backlog))
        app.handle(url: URL(string: "thumbpotato://platform/ps5")!)
        XCTAssertEqual(app.selection, .tab(.browse))
        RunLoop.main.run(until: Date().addingTimeInterval(0.1))
        XCTAssertEqual(app.paths[.tab(.browse)]?.count, 1)
        app.handle(url: URL(string: "thumbpotato://game/wd-Q64577191")!)
        XCTAssertEqual(app.paths[.tab(.browse)]?.count, 2)
    }
}
