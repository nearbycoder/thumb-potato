import Foundation
import Observation

/// One front door to every data source. Key-less, it pages and filters the published
/// per-platform catalog locally and adds Wikipedia, Steam and CheapShark; with a RAWG
/// key, platform pages, moods, genres and search go to RAWG instead.
@MainActor
@Observable
final class Catalog {
    let prefs: Preferences
    private let store = CatalogStore.shared
    private let wikipedia = WikipediaClient()

    @ObservationIgnored private var memo: [String: [Game]] = [:]
    /// Bumped by `reset()` so views can reload.
    private(set) var generation = 0

    /// Page size for catalog lists, which arrive whole and are paged locally.
    nonisolated static let pageSize = 60

    init(prefs: Preferences) { self.prefs = prefs }

    var rawg: RAWGClient? {
        let key = prefs.effectiveRAWGKey
        return key.isEmpty ? nil : RAWGClient(key: key)
    }

    var hasRAWG: Bool { rawg != nil }

    private var steam: SteamClient { SteamClient(region: prefs.region) }

    /// My platforms, or every platform before any are picked.
    var feedPlatforms: [Platform] { prefs.myPlatforms.isEmpty ? Platform.all : prefs.myPlatforms }

    /// Drops cached lists (e.g. after the region, key or platforms change).
    func reset() {
        memo = [:]
        generation += 1
        Task { await store.reset() }
    }

    private func remember(_ key: String, _ load: () async throws -> [Game]) async throws -> [Game] {
        let fullKey = "\(key)|\(prefs.region)|\(hasRAWG)"
        if let hit = memo[fullKey] { return hit }
        let value = try await load()
        memo[fullKey] = value
        return value
    }

    func pool(_ platforms: [Platform]) async throws -> [Game] {
        try await store.pool(platforms.map(\.id))
    }

    func catalogIndex() async -> CatalogIndex? { await store.index() }

    // MARK: Tonight rows

    /// Popular releases from the last 18 months on my platforms, with box art.
    func hero() async throws -> [Game] {
        try await remember("hero-\(prefs.myPlatformIDs.sorted())") {
            let mine = Self.recentHits(try await pool(feedPlatforms))
            if mine.count >= 3 { return mine }
            return Self.recentHits(try await pool(Platform.all))
        }
    }

    nonisolated static func recentHits(_ games: [Game], now: Date = .now) -> [Game] {
        let since = Calendar.current.date(byAdding: .month, value: -18, to: now) ?? now
        return Array(games.filter {
            $0.boxArtURL != nil && !$0.isUpcoming && ($0.releaseDate ?? .distantPast) >= since
        }.prefix(8))
    }

    func newReleases() async throws -> [Game] {
        try await remember("new-\(prefs.myPlatformIDs.sorted())") {
            Self.newReleases(try await pool(feedPlatforms))
        }
    }

    nonisolated static func newReleases(_ games: [Game], now: Date = .now) -> [Game] {
        let since = Calendar.current.date(byAdding: .day, value: -120, to: now) ?? now
        return Array(games
            .filter { $0.boxArtURL != nil && !$0.isUpcoming && $0.releaseDatePrecision == .day && ($0.releaseDate ?? .distantPast) >= since }
            .sorted { ($0.releaseDate ?? .distantPast) > ($1.releaseDate ?? .distantPast) }
            .prefix(30))
    }

    func comingSoon() async throws -> [Game] {
        try await remember("soon-\(prefs.myPlatformIDs.sorted())") {
            Self.comingSoon(try await pool(feedPlatforms))
        }
    }

    /// Upcoming games, soonest first; exact dates ahead of "sometime in" dates for the same day.
    nonisolated static func comingSoon(_ games: [Game]) -> [Game] {
        Array(games.filter { $0.isUpcoming && $0.boxArtURL != nil }.sorted { a, b in
            let da = a.releaseDate ?? .distantFuture, db = b.releaseDate ?? .distantFuture
            if da != db { return da < db }
            return a.popularity > b.popularity
        }.prefix(40))
    }

    func top(_ platform: Platform) async throws -> [Game] {
        // Rows skip games without box art; the full platform page still lists them.
        Array(try await store.games(platform.id).filter { !$0.isUpcoming && $0.boxArtURL != nil }.prefix(30))
    }

    /// Steam's top sellers, swapped for catalog records where we have them.
    func steamTopSellers() async throws -> [Game] {
        try await remember("steam-top") {
            let items = try await steam.topSellers()
            return await resolveSteam(items.map(\.id), notes: [:])
        }
    }

    /// Well-reviewed PC games on sale (CheapShark), with Steam art.
    func deals() async throws -> [Game] {
        try await remember("deals") {
            let deals = try await CheapSharkClient.shared.topDeals()
            var seen = Set<Int>()
            var ids: [Int] = []
            var notes: [Int: String] = [:]
            for deal in deals {
                guard let id = deal.steamAppID.flatMap(Int.init), seen.insert(id).inserted else { continue }
                ids.append(id)
                notes[id] = deal.note
            }
            return await resolveSteam(ids, notes: notes)
        }
    }

    /// Steam app IDs → games, preferring catalog records (so the backlog never holds a
    /// `steam-` twin of a `wd-` game), keeping order.
    private func resolveSteam(_ ids: [Int], notes: [Int: String]) async -> [Game] {
        let catalog = await store.bySteamID()
        let missing = ids.filter { catalog[$0] == nil }
        let items = (try? await steam.items(missing)) ?? [:]
        return ids.compactMap { id in
            var game = catalog[id] ?? items[id]?.game
            if game?.boxArtURL == nil, let art = items[id]?.capsuleURL { game?.artURL = art }
            game?.priceNote = notes[id]
            return game?.boxArtURL == nil ? nil : game
        }
    }

    // MARK: Pages

    /// One page of a platform. An empty page means the list is exhausted.
    func platform(_ platform: Platform, filter: GameFilter = GameFilter(), page: Int = 1) async throws -> [Game] {
        if let rawg, let games = try? await rawg.games(platforms: [platform], filter: filter, page: page) {
            return games
        }
        return Self.slice(filter.apply(try await store.games(platform.id)), page: page)
    }

    /// One page of games for a mood, on my platforms.
    func mood(_ mood: Mood, filter: GameFilter = GameFilter(), page: Int = 1) async throws -> [Game] {
        // A mood already picks its genres, so the filter's genre doesn't apply here.
        var filter = filter
        filter.genre = nil
        let games = try await remember("mood-\(mood.rawValue)-\(prefs.myPlatformIDs.sorted())") {
            try await pool(feedPlatforms).filter(mood.matches)
        }
        return Self.slice(filter.apply(games), page: page)
    }

    func genre(_ genre: Genre, filter: GameFilter = GameFilter(), page: Int = 1) async throws -> [Game] {
        var filter = filter
        filter.genre = genre
        if let rawg, let games = try? await rawg.games(platforms: feedPlatforms, filter: filter, page: page) {
            return games
        }
        return Self.slice(filter.apply(try await pool(feedPlatforms)), page: page)
    }

    nonisolated static func slice(_ games: [Game], page: Int) -> [Game] {
        let start = (page - 1) * pageSize
        guard page >= 1, start < games.count else { return [] }
        return Array(games[start..<min(start + pageSize, games.count)])
    }

    // MARK: Search

    func search(_ text: String) async throws -> [Game] {
        let query = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard query.count >= 2 else { return [] }
        if let rawg, let found = try? await rawg.search(query), !found.isEmpty { return found }

        async let wiki = (try? await wikipedia.search(query)) ?? []
        async let steamHits = (try? await steam.search(query)) ?? []
        let all = (try? await pool(Platform.all)) ?? []
        let local = Self.localMatches(query, in: all)
        let catalogBySteam = await store.bySteamID()
        let byQID = Dictionary(all.map { ($0.ref.id, $0) }, uniquingKeysWith: { a, _ in a })

        let steamIDs = await steamHits.filter { ($0.type ?? "app") == "app" }.map(\.id)
        let unknownSteam = steamIDs.filter { catalogBySteam[$0] == nil }.prefix(12)
        let items = (try? await steam.items(Array(unknownSteam))) ?? [:]
        let steamGames = steamIDs.compactMap { catalogBySteam[$0] ?? items[$0]?.game }
        return Self.mergeSearch(local: local, wiki: await wiki, steam: steamGames, catalogByQID: byQID)
    }

    /// Instant results from the downloaded catalog, shown while the network search runs.
    func localSearch(_ text: String) async -> [Game] {
        let all = (try? await pool(Platform.all)) ?? []
        return Self.localMatches(text, in: all)
    }

    /// Catalog games whose names contain the query; names that start with it first.
    nonisolated static func localMatches(_ query: String, in games: [Game]) -> [Game] {
        let needle = query.normalizedTitle
        guard !needle.isEmpty else { return [] }
        let hits = games.filter { $0.name.normalizedTitle.contains(needle) }
        let prefix = hits.filter { $0.name.normalizedTitle.hasPrefix(needle) }
        let rest = hits.filter { !$0.name.normalizedTitle.hasPrefix(needle) }
        return Array((prefix + rest).prefix(40))
    }

    /// Merges catalog, Wikipedia and Steam results: Wikipedia hits become their richer
    /// catalog record when the QID matches, and duplicates (same key, same Steam app,
    /// or the same normalized name) are dropped, keeping the first.
    nonisolated static func mergeSearch(local: [Game], wiki: [Game], steam: [Game], catalogByQID: [String: Game]) -> [Game] {
        var result: [Game] = []
        var keys = Set<String>()
        var steamIDs = Set<Int>()
        var names = Set<String>()
        func add(_ game: Game) {
            guard !keys.contains(game.id) else { return }
            if let id = game.steamAppID, steamIDs.contains(id) { return }
            let name = game.name.normalizedTitle
            if game.ref.source == .steam, names.contains(name) { return }
            keys.insert(game.id)
            if let id = game.steamAppID { steamIDs.insert(id) }
            names.insert(name)
            result.append(game)
        }
        let enriched = wiki.map { hit -> Game in
            guard var record = catalogByQID[hit.ref.id] else { return hit }
            if record.coverURL == nil { record.coverURL = hit.coverURL }
            return record
        }
        // Wikipedia's relevance order leads, then catalog name matches, then Steam.
        enriched.prefix(8).forEach(add)
        local.forEach(add)
        enriched.dropFirst(8).forEach(add)
        steam.forEach(add)
        return result
    }

    // MARK: Details

    func detail(_ ref: GameRef) async throws -> GameDetail {
        switch ref.source {
        case .rawg:
            guard let rawg else { throw HTTPError.missingKey }
            var detail = try await rawg.detail(ref.id)
            detail.stores = Store.offers(for: detail.game, myPlatforms: prefs.myPlatformIDs)
            if detail.game.platforms.contains(where: { $0 == "pc" || $0 == "mac" }) {
                detail.pcDeals = (try? await CheapSharkClient.shared.deals(steamAppID: nil, title: detail.game.name)) ?? []
            }
            detail.similar = await similar(to: detail.game)
            return detail
        case .steam:
            guard let appID = Int(ref.id) else { throw HTTPError.notFound }
            if let record = await store.bySteamID()[appID] { return try await assemble(record) }
            let item = try? await steam.items([appID])[appID]
            var game = item?.game ?? Game(ref: ref, name: "", platforms: ["pc"], steamAppID: appID)
            game.steamAppID = appID
            return try await assemble(game, steamItem: item)
        case .wd:
            let all = (try? await pool(Platform.all)) ?? []
            if let record = all.first(where: { $0.ref == ref }) { return try await assemble(record) }
            return try await assemble(try await wikipedia.wikidataGame(ref.id))
        }
    }

    /// Fills in a base game from Wikipedia, Steam and CheapShark, in parallel.
    private func assemble(_ base: Game, steamItem: SteamItem? = nil) async throws -> GameDetail {
        var game = base
        let wikiTitle = game.wikiTitle
        let appID = game.steamAppID
        let name = game.name
        async let summary = wikiTitle.asyncMap { try? await wikipedia.summary($0) }
        async let details = appID.asyncMap { try? await steam.appDetails($0) }
        async let item = steamItem != nil ? steamItem : appID.asyncMap { try? await steam.items([$0])[$0] }

        let app = await details ?? nil
        if game.name.isEmpty { game.name = app?.name ?? name }
        let isPC = !Set(game.platforms).isDisjoint(with: ["pc", "mac"]) || appID != nil
        let dealTitle = game.name
        async let pcDeals = isPC ? ((try? await CheapSharkClient.shared.deals(steamAppID: appID, title: dealTitle)) ?? []) : []

        if let summary = await summary ?? nil {
            if summary.extract.count > game.summary.count { game.summary = summary.extract }
            if game.coverURL == nil, game.artURL == nil { game.coverURL = summary.thumbnail }
        }
        var detail = GameDetail(game: game)
        if let app {
            if game.summary.isEmpty { game.summary = app.short_description?.strippingHTML ?? "" }
            if game.genres.isEmpty { game.genres = Genre.map((app.genres ?? []).map(\.description)) }
            game.metacritic = game.metacritic ?? app.metacritic?.score
            game.developer = game.developer ?? app.developers?.first
            game.publisher = game.publisher ?? app.publishers?.first
            if game.releaseDate == nil, let (date, precision) = DateParsing.steam(app.release_date?.date) {
                game.releaseDate = date
                game.releaseDatePrecision = precision
                game.year = Calendar.current.component(.year, from: date)
            }
            var platforms = game.platforms
            if app.platforms?.windows == true, !platforms.contains("pc") { platforms.append("pc") }
            if app.platforms?.mac == true, !platforms.contains("mac") { platforms.append("mac") }
            game.platforms = Platform.list(platforms).map(\.id)
            detail.screenshots = (app.screenshots ?? []).compactMap { $0.path_full.flatMap(URL.init(string:)) }
            detail.trailerURL = app.trailerURL
            detail.website = app.website.flatMap { $0.isEmpty ? nil : URL(string: $0) }
        }
        if let item = await item ?? nil {
            game.artURL = game.artURL ?? item.capsuleURL
            game.heroURL = game.heroURL ?? item.heroURL
            game.steamDeck = game.steamDeck ?? item.platforms?.steam_deck_compat_category
            detail.steamReviews = item.reviewLine
        }
        if detail.trailerURL == nil, let q = "\(game.name) trailer".addingPercentEncoding(withAllowedCharacters: .urlQueryValueAllowed) {
            detail.trailerURL = URL(string: "https://www.youtube.com/results?search_query=\(q)")
        }
        detail.game = game
        var stores = Store.offers(for: game, myPlatforms: prefs.myPlatformIDs)
        if let price = app?.price_overview, let i = stores.firstIndex(where: { $0.store == .steam }) {
            stores[i].price = price.final_formatted
            if (price.discount_percent ?? 0) > 0 { stores[i].regularPrice = price.initial_formatted }
        } else if app?.is_free == true, let i = stores.firstIndex(where: { $0.store == .steam }) {
            stores[i].price = "Free"
        }
        detail.stores = stores
        detail.pcDeals = await pcDeals
        detail.similar = await similar(to: game)
        return detail
    }

    /// Same top genre on the same platforms, from the catalog.
    func similar(to game: Game) async -> [Game] {
        guard let genre = game.genres.first else { return [] }
        let platforms = game.platformList.isEmpty ? feedPlatforms : game.platformList
        let pool = (try? await self.pool(platforms)) ?? []
        return Array(pool.filter { $0.ref != game.ref && $0.genres.contains(genre) && $0.boxArtURL != nil && $0.name != game.name }
            .prefix(15))
    }
}

extension Optional {
    func asyncMap<U>(_ transform: (Wrapped) async -> U?) async -> U? {
        guard let self else { return nil }
        return await transform(self)
    }
}
