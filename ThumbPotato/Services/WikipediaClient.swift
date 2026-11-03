import Foundation

/// Wikipedia (summaries, box art, search over every game with an infobox) and
/// Wikidata (platforms, dates and credits for games outside the catalog). Key-less.
struct WikipediaClient {
    private static let wiki = "https://en.wikipedia.org"

    struct Summary {
        var extract: String
        var thumbnail: URL?
    }

    func summary(_ title: String) async throws -> Summary {
        let encoded = title.replacingOccurrences(of: " ", with: "_")
            .addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/?#"))) ?? title
        let page: WikiSummary = try await HTTP.get(URL(string: "\(Self.wiki)/api/rest_v1/page/summary/\(encoded)")!)
        return Summary(extract: page.extract ?? "", thumbnail: (page.originalimage?.source ?? page.thumbnail?.source).flatMap(URL.init(string:)))
    }

    /// Video game articles matching a query, in relevance order.
    func search(_ query: String) async throws -> [Game] {
        let url = HTTP.url(Self.wiki, "/w/api.php", [
            "action": "query", "format": "json", "formatversion": "2",
            "generator": "search", "gsrsearch": "\(query) hastemplate:\"Infobox video game\"", "gsrlimit": "20",
            "prop": "pageimages|pageprops|extracts", "piprop": "thumbnail", "pithumbsize": "600", "pilicense": "any",
            "ppprop": "wikibase_item", "exintro": "1", "explaintext": "1", "exsentences": "2", "exlimit": "max",
        ])
        let result: WikiSearch = try await HTTP.get(url)
        return (result.query?.pages ?? [])
            .sorted { ($0.index ?? 0) < ($1.index ?? 0) }
            .compactMap { page in
                guard let qid = page.pageprops?.wikibase_item else { return nil }
                return Game(ref: GameRef(source: .wd, id: qid), name: Self.cleanTitle(page.title),
                            summary: page.extract ?? "", coverURL: page.thumbnail.flatMap { URL(string: $0.source) },
                            wikiTitle: page.title.replacingOccurrences(of: " ", with: "_"))
            }
    }

    /// "Hades (video game)" → "Hades".
    static func cleanTitle(_ title: String) -> String {
        title.replacingOccurrences(of: #" \((\d{4} )?video game\)$"#, with: "", options: .regularExpression)
    }

    /// Builds a game from its Wikidata item: platforms, release date, genres, credits, Steam ID.
    func wikidataGame(_ qid: String) async throws -> Game {
        let url = URL(string: "https://www.wikidata.org/wiki/Special:EntityData/\(qid).json")!
        let file: WDEntityFile = try await HTTP.get(url)
        guard let entity = file.entities.values.first else { throw HTTPError.notFound }
        let claims = entity.claims ?? [:]
        func items(_ property: String) -> [String] { (claims[property] ?? []).compactMap { $0.mainsnak.datavalue?.itemID } }

        let platforms = Platform.all.map(\.id).filter { id in
            items("P400").contains { Platform.wikidataItems[$0] == id }
        }
        // Earliest release date, with the precision Wikidata records (11 day, 10 month, 9 year).
        let times = (claims["P577"] ?? []).compactMap { $0.mainsnak.datavalue?.time }
        let earliest = times.compactMap { t -> (Date, DatePrecision)? in
            guard !t.time.hasPrefix("-"), let date = DateParsing.date(String(t.time.dropFirst().prefix(10)).replacingOccurrences(of: "-00", with: "-01")) else { return nil }
            let precision: DatePrecision = t.precision >= 11 ? .day : (t.precision == 10 ? .month : .year)
            return t.precision >= 9 ? (date, precision) : nil
        }.min { $0.0 < $1.0 }
        let steam = (claims["P1733"] ?? []).compactMap { $0.mainsnak.datavalue?.string }.first.flatMap(Int.init)

        // One batched lookup for the labels of genres, developer and publisher.
        let genreIDs = items("P136"), devIDs = items("P178"), pubIDs = items("P123")
        let labels = (try? await self.labels(Array(Set(genreIDs + devIDs.prefix(1) + pubIDs.prefix(1))))) ?? [:]

        let title = entity.sitelinks?["enwiki"]?.title
        return Game(ref: GameRef(source: .wd, id: qid),
                    name: entity.labels?["en"]?.value ?? title.map(Self.cleanTitle) ?? qid,
                    releaseDate: earliest?.0, releaseDatePrecision: earliest?.1 ?? .day,
                    year: earliest.map { Calendar.current.component(.year, from: $0.0) },
                    genres: Genre.map(genreIDs.compactMap { labels[$0] }),
                    developer: devIDs.first.flatMap { labels[$0] }, publisher: pubIDs.first.flatMap { labels[$0] },
                    platforms: platforms, steamAppID: steam,
                    wikiTitle: title?.replacingOccurrences(of: " ", with: "_"))
    }

    private func labels(_ ids: [String]) async throws -> [String: String] {
        guard !ids.isEmpty else { return [:] }
        let url = HTTP.url("https://www.wikidata.org", "/w/api.php", [
            "action": "wbgetentities", "ids": ids.prefix(50).joined(separator: "|"), "props": "labels",
            "languages": "en", "format": "json",
        ])
        let result: WDLabels = try await HTTP.get(url)
        return result.entities.compactMapValues { $0.labels?["en"]?.value }
    }
}

// MARK: - DTOs

struct WikiSummary: Decodable {
    struct Image: Decodable { var source: String }
    var extract: String?
    var thumbnail: Image?
    var originalimage: Image?
}

struct WikiSearch: Decodable {
    struct Query: Decodable { var pages: [Page]? }
    struct Page: Decodable {
        var title: String
        var index: Int?
        var extract: String?
        var thumbnail: Thumb?
        var pageprops: Props?
    }
    struct Thumb: Decodable { var source: String }
    struct Props: Decodable { var wikibase_item: String? }
    var query: Query?
}

struct WDEntityFile: Decodable {
    var entities: [String: WDEntity]
}

struct WDEntity: Decodable {
    struct Label: Decodable { var value: String }
    struct Sitelink: Decodable { var title: String }
    struct Claim: Decodable { var mainsnak: Snak }
    struct Snak: Decodable { var datavalue: DataValue? }

    var labels: [String: Label]?
    var sitelinks: [String: Sitelink]?
    var claims: [String: [Claim]]?
}

struct WDTime: Decodable {
    var time: String
    var precision: Int
}

/// Wikidata's datavalue is polymorphic: an item, a time or a plain string.
struct DataValue: Decodable {
    var itemID: String?
    var time: WDTime?
    var string: String?

    private enum Keys: String, CodingKey { case type, value }
    private struct Item: Decodable { var id: String }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: Keys.self)
        switch try c.decodeIfPresent(String.self, forKey: .type) {
        case "wikibase-entityid": itemID = (try? c.decode(Item.self, forKey: .value))?.id
        case "time": time = try? c.decode(WDTime.self, forKey: .value)
        case "string": string = try? c.decode(String.self, forKey: .value)
        default: break
        }
    }
}

struct WDLabels: Decodable {
    struct Entity: Decodable { var labels: [String: WDEntity.Label]? }
    var entities: [String: Entity]
}
