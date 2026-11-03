import Foundation

/// Steam's public store endpoints (no key): featured lists, art, details and search.
struct SteamClient {
    var region: String

    static let assetBase = "https://shared.akamai.steamstatic.com/store_item_assets/"

    // MARK: Featured

    /// Top sellers on Steam, deduped, without hardware, demos and soundtracks.
    func topSellers() async throws -> [SteamFeatured.Item] {
        let url = HTTP.url("https://store.steampowered.com", "/api/featuredcategories", ["cc": region, "l": "english"])
        let featured: SteamFeatured = try await HTTP.get(url)
        return Self.cleaned(featured.top_sellers?.items ?? [])
    }

    static func cleaned(_ items: [SteamFeatured.Item]) -> [SteamFeatured.Item] {
        var seen = Set<Int>()
        return items.filter { item in
            let lower = item.name.lowercased()
            let junk = lower.contains("demo") || lower.contains("soundtrack") || lower.hasPrefix("steam ")
            return !junk && seen.insert(item.id).inserted
        }
    }

    // MARK: Store items (art, Deck, reviews)

    /// Art, Steam Deck status, reviews and release dates for up to 50 apps per request.
    func items(_ appIDs: [Int]) async throws -> [Int: SteamItem] {
        var result: [Int: SteamItem] = [:]
        for batch in stride(from: 0, to: appIDs.count, by: 50).map({ Array(appIDs[$0..<min($0 + 50, appIDs.count)]) }) {
            let response: SteamItemsResponse = try await HTTP.get(Self.itemsURL(batch, country: region))
            for item in response.response?.store_items ?? [] where item.success == 1 {
                if let id = item.appid ?? item.id { result[id] = item }
            }
        }
        return result
    }

    static func itemsURL(_ appIDs: [Int], country: String) -> URL {
        let ids = appIDs.map { "{\"appid\":\($0)}" }.joined(separator: ",")
        let json = """
        {"ids":[\(ids)],"context":{"language":"english","country_code":"\(country)"},"data_request":{"include_assets":true,"include_platforms":true,"include_basic_info":true,"include_reviews":true,"include_release":true}}
        """
        return HTTP.url("https://api.steampowered.com", "/IStoreBrowseService/GetItems/v1/", ["input_json": json])
    }

    /// `steam/apps/N/${FILENAME}?t=…` + a file name → a full image URL.
    static func assetURL(format: String?, file: String?) -> URL? {
        guard let format, let file, !file.isEmpty else { return nil }
        return URL(string: assetBase + format.replacingOccurrences(of: "${FILENAME}", with: file))
    }

    // MARK: Details

    func appDetails(_ appID: Int) async throws -> SteamAppDetails {
        let url = HTTP.url("https://store.steampowered.com", "/api/appdetails", ["appids": String(appID), "cc": region, "l": "english"])
        let result: [String: SteamAppDetailsEnvelope] = try await HTTP.get(url)
        guard let data = result[String(appID)]?.data else { throw HTTPError.notFound }
        return data
    }

    func search(_ term: String) async throws -> [SteamSearch.Item] {
        let url = HTTP.url("https://store.steampowered.com", "/api/storesearch/", ["term": term, "cc": region, "l": "english"])
        let result: SteamSearch = try await HTTP.get(url)
        return result.items ?? []
    }
}

// MARK: - DTOs

struct SteamFeatured: Decodable {
    struct List: Decodable { var items: [Item]? }
    struct Item: Decodable {
        var id: Int
        var name: String
        var discount_percent: Int?
        var final_price: Int?
        var header_image: String?
    }
    var top_sellers: List?
    var new_releases: List?
    var specials: List?
    var coming_soon: List?
}

struct SteamItemsResponse: Decodable {
    struct Response: Decodable { var store_items: [SteamItem]? }
    var response: Response?
}

struct SteamItem: Decodable {
    struct Assets: Decodable {
        var asset_url_format: String?
        var library_capsule: String?
        var library_capsule_2x: String?
        var library_hero: String?
        var library_hero_2x: String?
        var header: String?
    }
    struct Platforms: Decodable {
        var windows: Bool?
        var mac: Bool?
        var steam_deck_compat_category: Int?
    }
    struct Reviews: Decodable {
        struct Summary: Decodable { var percent_positive: Int?; var review_score_label: String? }
        var summary_filtered: Summary?
    }
    struct Release: Decodable { var steam_release_date: Double?; var is_coming_soon: Bool? }
    struct BasicInfo: Decodable {
        struct Named: Decodable { var name: String }
        var short_description: String?
        var developers: [Named]?
        var publishers: [Named]?
    }

    var id: Int?
    var appid: Int?
    var success: Int?
    var name: String?
    var assets: Assets?
    var platforms: Platforms?
    var reviews: Reviews?
    var release: Release?
    var basic_info: BasicInfo?

    var capsuleURL: URL? {
        SteamClient.assetURL(format: assets?.asset_url_format, file: assets?.library_capsule_2x ?? assets?.library_capsule)
    }

    var heroURL: URL? {
        SteamClient.assetURL(format: assets?.asset_url_format, file: assets?.library_hero_2x ?? assets?.library_hero)
    }

    var reviewLine: String? {
        guard let s = reviews?.summary_filtered, let label = s.review_score_label, !label.isEmpty else { return nil }
        return s.percent_positive.map { "\(label) · \($0)%" } ?? label
    }

    var platformIDs: [String] {
        var ids: [String] = []
        if platforms?.windows == true { ids.append("pc") }
        if platforms?.mac == true { ids.append("mac") }
        return ids
    }

    /// A catalog value for a Steam-only game.
    var game: Game? {
        guard let appID = appid ?? id, let name else { return nil }
        let released = release?.steam_release_date.map { Date(timeIntervalSince1970: $0) }
        return Game(ref: GameRef(source: .steam, id: String(appID)), name: name,
                    summary: basic_info?.short_description?.strippingHTML ?? "",
                    artURL: capsuleURL, heroURL: heroURL, releaseDate: released,
                    year: released.map { Calendar.current.component(.year, from: $0) },
                    developer: basic_info?.developers?.first?.name, publisher: basic_info?.publishers?.first?.name,
                    platforms: platformIDs.isEmpty ? ["pc"] : platformIDs, steamAppID: appID,
                    steamDeck: platforms?.steam_deck_compat_category)
    }
}

struct SteamAppDetailsEnvelope: Decodable {
    var success: Bool
    var data: SteamAppDetails?
}

struct SteamAppDetails: Decodable {
    struct Screenshot: Decodable { var path_full: String? }
    struct Movie: Decodable {
        var hls_h264: String?
        var dash_h264: String?
        var mp4: [String: String]?
    }
    struct Genre: Decodable { var description: String }
    struct Metacritic: Decodable { var score: Int? }
    struct ReleaseDate: Decodable { var coming_soon: Bool?; var date: String? }
    struct Price: Decodable { var final_formatted: String?; var initial_formatted: String?; var discount_percent: Int? }
    struct Platforms: Decodable { var windows: Bool?; var mac: Bool? }

    var type: String?
    var name: String?
    var short_description: String?
    var header_image: String?
    var website: String?
    var developers: [String]?
    var publishers: [String]?
    var screenshots: [Screenshot]?
    var movies: [Movie]?
    var genres: [Genre]?
    var metacritic: Metacritic?
    var release_date: ReleaseDate?
    var price_overview: Price?
    var platforms: Platforms?
    var is_free: Bool?

    var trailerURL: URL? {
        guard let movie = movies?.first else { return nil }
        let candidate = movie.mp4?["max"] ?? movie.mp4?["480"] ?? movie.hls_h264
        return candidate.flatMap(URL.init(string:))
    }
}

struct SteamSearch: Decodable {
    struct Item: Decodable {
        var id: Int
        var name: String
        var type: String?
        var metascore: String?
    }
    var items: [Item]?
}
