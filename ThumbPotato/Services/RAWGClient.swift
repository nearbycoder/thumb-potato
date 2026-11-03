import Foundation

/// RAWG: optional, keyed catalog with server-side paging and filters. Untested during
/// development (no key was available), so everything RAWG lives in this one file and
/// the app falls back to the key-less sources when it fails.
struct RAWGClient {
    let key: String
    private static let base = "https://api.rawg.io/api"

    private func get<T: Decodable>(_ path: String, _ query: [String: String?] = [:]) async throws -> T {
        guard !key.isEmpty else { throw HTTPError.missingKey }
        var query = query
        query["key"] = key
        return try await HTTP.get(HTTP.url(Self.base, path, query))
    }

    func validate() async throws {
        let _: RAWGPage<RAWGGame> = try await get("/games", ["page_size": "1"])
    }

    func games(platforms: [Platform], filter: GameFilter, search: String? = nil, page: Int, pageSize: Int = 40) async throws -> [Game] {
        var query = filter.rawgQuery()
        let ids = platforms.flatMap(\.rawgIDs)
        if !ids.isEmpty { query["platforms"] = ids.map(String.init).joined(separator: ",") }
        if let search {
            query["search"] = search
            query["ordering"] = nil
        }
        query["page"] = String(page)
        query["page_size"] = String(pageSize)
        do {
            let result: RAWGPage<RAWGGame> = try await get("/games", query)
            return result.results.map(\.game)
        } catch HTTPError.status(404) {
            return [] // RAWG answers 404 past the last page.
        }
    }

    func search(_ text: String) async throws -> [Game] {
        try await games(platforms: [], filter: GameFilter(), search: text, page: 1, pageSize: 30)
    }

    func detail(_ id: String) async throws -> GameDetail {
        async let game: RAWGGame = get("/games/\(id)")
        async let shots: RAWGPage<RAWGScreenshot> = get("/games/\(id)/screenshots")
        let raw = try await game
        let screenshots = ((try? await shots)?.results ?? []).compactMap { URL(string: $0.image) }
        return GameDetail(game: raw.game, screenshots: screenshots,
                          website: raw.website.flatMap { $0.isEmpty ? nil : URL(string: $0) })
    }
}

// MARK: - DTOs

struct RAWGPage<T: Decodable>: Decodable {
    var results: [T]
    var next: String?
}

struct RAWGScreenshot: Decodable { var image: String }

struct RAWGGame: Decodable {
    struct Named: Decodable { var name: String }
    struct PlatformEntry: Decodable { var platform: Named }

    var id: Int
    var name: String
    var released: String?
    var tba: Bool?
    var background_image: String?
    var metacritic: Int?
    var added: Double?
    var genres: [Named]?
    var platforms: [PlatformEntry]?
    var description_raw: String?
    var website: String?
    var developers: [Named]?
    var publishers: [Named]?

    var game: Game {
        let platformIDs = Platform.all.map(\.id).filter { id in
            (platforms ?? []).contains { Platform.matching(name: $0.platform.name)?.id == id }
        }
        let backdrop = background_image.flatMap(URL.init(string:))
        return Game(ref: GameRef(source: .rawg, id: String(id)), name: name, summary: description_raw ?? "",
                    artURL: backdrop, heroURL: backdrop, releaseDate: DateParsing.date(released),
                    year: DateParsing.year(released), genres: Genre.map((genres ?? []).map(\.name)),
                    developer: developers?.first?.name, publisher: publishers?.first?.name, platforms: platformIDs,
                    metacritic: metacritic, popularity: added ?? 0)
    }
}
