import Foundation

/// CheapShark: PC prices across stores and a deals feed. Key-less; USD.
actor CheapSharkClient {
    static let shared = CheapSharkClient()
    private static let base = "https://www.cheapshark.com/api/1.0"

    private var storeNames: [String: String]?

    /// The best deals for a game, cheapest first, matched by Steam app ID or else by title.
    func deals(steamAppID: Int?, title: String) async throws -> [Deal] {
        var matches: [CSGameMatch] = []
        if let steamAppID {
            matches = try await HTTP.get(HTTP.url(Self.base, "/games", ["steamAppID": String(steamAppID)]))
        }
        if matches.isEmpty {
            let found: [CSGameMatch] = try await HTTP.get(HTTP.url(Self.base, "/games", ["title": title, "limit": "5"]))
            let wanted = title.normalizedTitle
            matches = found.filter { ($0.external ?? "").normalizedTitle == wanted }
        }
        guard let gameID = matches.first?.gameID else { return [] }
        let info: CSGameInfo = try await HTTP.get(HTTP.url(Self.base, "/games", ["id": gameID]))
        let names = await stores()
        return Self.deals(info.deals ?? [], storeNames: names)
    }

    static func deals(_ raw: [CSGameInfo.RawDeal], storeNames: [String: String]) -> [Deal] {
        let deals: [Deal] = raw.compactMap { d -> Deal? in
            guard let price = Double(d.price), let link = redirect(d.dealID) else { return nil }
            return Deal(id: d.dealID, storeName: storeNames[d.storeID] ?? "Store \(d.storeID)", price: price,
                        retailPrice: Double(d.retailPrice ?? "") ?? price, savings: Double(d.savings ?? "") ?? 0, link: link)
        }
        return Array(deals.sorted { $0.price < $1.price }.prefix(6))
    }

    /// Deal IDs arrive already percent-encoded.
    static func redirect(_ dealID: String) -> URL? {
        URL(string: "https://www.cheapshark.com/redirect?dealID=\(dealID)")
    }

    /// Well-rated games on sale right now.
    func topDeals() async throws -> [CSDeal] {
        try await HTTP.get(HTTP.url(Self.base, "/deals", [
            "sortBy": "Deal Rating", "pageSize": "30", "metacritic": "75", "onSale": "1",
        ]))
    }

    func stores() async -> [String: String] {
        if let storeNames { return storeNames }
        let list: [CSStore] = (try? await HTTP.get(URL(string: "\(Self.base)/stores")!)) ?? []
        let names = Dictionary(list.map { ($0.storeID, $0.storeName) }, uniquingKeysWith: { a, _ in a })
        if !names.isEmpty { storeNames = names }
        return names
    }
}

// MARK: - DTOs

struct CSGameMatch: Decodable {
    var gameID: String
    var steamAppID: String?
    var external: String?
}

struct CSGameInfo: Decodable {
    struct RawDeal: Decodable {
        var storeID: String
        var dealID: String
        var price: String
        var retailPrice: String?
        var savings: String?
    }
    var deals: [RawDeal]?
}

struct CSDeal: Decodable {
    var title: String
    var dealID: String
    var storeID: String
    var steamAppID: String?
    var salePrice: String
    var normalPrice: String
    var savings: String
    var metacriticScore: String?

    var note: String {
        let sale = Double(salePrice) ?? 0
        let off = Int((Double(savings) ?? 0).rounded())
        return off > 0 ? "\(sale == 0 ? "Free" : sale.usd) · −\(off)%" : sale.usd
    }
}

struct CSStore: Decodable {
    var storeID: String
    var storeName: String
}
