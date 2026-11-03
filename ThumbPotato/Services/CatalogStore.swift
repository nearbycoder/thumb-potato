import Foundation

/// One record of a published platform catalog (scripts/build_catalog.py → docs/catalog/v1/<platform>.json).
struct CatalogRecord: Decodable {
    var id: String
    var name: String
    var date: String?
    var datePrecision: String?
    var genres: [String]?
    var dev: String?
    var pub: String?
    var cover: String?
    var art: String?
    var hero: String?
    var deck: Int?
    var summary: String?
    var wiki: String?
    var steam: Int?
    var metacritic: Int?
    var pop: Double?
    var platforms: [String]?

    var game: Game {
        let released = DateParsing.date(date)
        return Game(ref: GameRef(source: .wd, id: id), name: name, summary: summary ?? "",
                    coverURL: cover.flatMap(URL.init(string:)), artURL: art.flatMap(URL.init(string:)),
                    heroURL: hero.flatMap(URL.init(string:)), releaseDate: released,
                    releaseDatePrecision: datePrecision.flatMap(DatePrecision.init(rawValue:)) ?? .day,
                    year: DateParsing.year(date), genres: Genre.map(genres ?? []), developer: dev, publisher: pub,
                    platforms: platforms ?? [], metacritic: metacritic, steamAppID: steam, wikiTitle: wiki,
                    popularity: pop ?? 0, steamDeck: deck)
    }
}

struct CatalogIndex: Decodable {
    var updated: String?
    var platforms: [String: Int]?
}

/// Downloads the per-platform catalog files, caches them on disk for a day and serves
/// the stale copy when the network fails.
actor CatalogStore {
    static let shared = CatalogStore()

    /// The published catalog. Override with `-catalogBase <url>` (e.g. a local server) for development.
    static var baseURL: String {
        UserDefaults.standard.string(forKey: "catalogBase") ?? "https://nearbycoder.github.io/thumb-potato/catalog/v1"
    }

    static let ttl: TimeInterval = 24 * 60 * 60

    private var memory: [String: [Game]] = [:]
    private var pools: [String: [Game]] = [:]
    private var steamIndex: [Int: Game]?
    private var inFlight: [String: Task<[Game], Error>] = [:]

    private var directory: URL {
        let base = FileManager.default.urls(for: .cachesDirectory, in: .userDomainMask)[0]
        let dir = base.appendingPathComponent("catalog-v1", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    /// Every game on a platform, most popular first.
    func games(_ platformID: String) async throws -> [Game] {
        if let hit = memory[platformID] { return hit }
        if let task = inFlight[platformID] { return try await task.value }
        let task = Task { try await self.load(platformID) }
        inFlight[platformID] = task
        defer { inFlight[platformID] = nil }
        let games = try await task.value
        memory[platformID] = games
        return games
    }

    /// Every game on any of the platforms, deduped by key, most popular first.
    /// Fails only when nothing at all could be loaded.
    func pool(_ platformIDs: [String]) async throws -> [Game] {
        let key = platformIDs.sorted().joined(separator: ",")
        if let hit = pools[key] { return hit }
        let tasks = platformIDs.map { id in Task { try await self.games(id) } }
        var seen = Set<String>()
        var all: [Game] = []
        var failure: Error?
        for task in tasks {
            do {
                for game in try await task.value where seen.insert(game.id).inserted { all.append(game) }
            } catch {
                failure = failure ?? error
            }
        }
        if all.isEmpty, let failure { throw failure }
        all.sort { $0.popularity > $1.popularity }
        if failure == nil { pools[key] = all }
        return all
    }

    /// Catalog games by Steam app ID, across every platform.
    func bySteamID() async -> [Int: Game] {
        if let steamIndex { return steamIndex }
        let all = (try? await pool(Platform.all.map(\.id))) ?? []
        var index: [Int: Game] = [:]
        for game in all { if let id = game.steamAppID, index[id] == nil { index[id] = game } }
        if !all.isEmpty { steamIndex = index }
        return index
    }

    /// Whatever is already in memory, without touching the network.
    func loaded() -> [String: [Game]] { memory }

    func index() async -> CatalogIndex? {
        guard let data = try? await fetch("index.json") else { return nil }
        return try? JSONDecoder().decode(CatalogIndex.self, from: data)
    }

    /// Forgets the in-memory copies; the next request revalidates against the disk cache.
    func reset() {
        memory = [:]
        pools = [:]
        steamIndex = nil
    }

    private func load(_ platformID: String) async throws -> [Game] {
        let data = try await fetch("\(platformID).json")
        let records = try JSONDecoder().decode([CatalogRecord].self, from: data)
        return Self.decode(records)
    }

    static func decode(_ records: [CatalogRecord]) -> [Game] {
        records.map(\.game).sorted { $0.popularity > $1.popularity }
    }

    /// Fresh disk copy if under a day old, else the network, else the stale disk copy.
    private func fetch(_ file: String) async throws -> Data {
        let local = directory.appendingPathComponent(file)
        let attributes = try? FileManager.default.attributesOfItem(atPath: local.path)
        let modified = attributes?[.modificationDate] as? Date
        if let modified, Date.now.timeIntervalSince(modified) < Self.ttl, let data = try? Data(contentsOf: local) {
            return data
        }
        do {
            let data = try await HTTP.data(URL(string: "\(Self.baseURL)/\(file)")!)
            try? data.write(to: local, options: .atomic)
            return data
        } catch {
            if let stale = try? Data(contentsOf: local) { return stale }
            throw error
        }
    }
}
