import SwiftUI

/// A storefront that sells games for some platforms ("Where to Play").
struct Store: Identifiable, Hashable {
    var id: String
    var name: String
    var monogram: String
    var colorHex: String
    var platformIDs: [String]
    /// Search URL template; `{q}` is replaced by the URL-encoded game name.
    var searchTemplate: String

    var color: Color { Color(hexString: colorHex) }

    static func == (lhs: Store, rhs: Store) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }

    func link(for game: Game) -> URL? {
        if id == "steam", let appID = game.steamAppID {
            return URL(string: "https://store.steampowered.com/app/\(appID)")
        }
        guard let q = game.name.addingPercentEncoding(withAllowedCharacters: .urlQueryValueAllowed) else { return nil }
        return URL(string: searchTemplate.replacingOccurrences(of: "{q}", with: q))
    }
}

extension Store {
    static let steam = Store(id: "steam", name: "Steam", monogram: "ST", colorHex: "1B2838", platformIDs: ["pc", "mac"],
                             searchTemplate: "https://store.steampowered.com/search/?term={q}")
    static let epic = Store(id: "epic", name: "Epic Games Store", monogram: "EG", colorHex: "2F2F2F", platformIDs: ["pc", "mac"],
                            searchTemplate: "https://store.epicgames.com/en-US/browse?q={q}")
    static let gog = Store(id: "gog", name: "GOG", monogram: "GOG", colorHex: "86328A", platformIDs: ["pc", "mac"],
                           searchTemplate: "https://www.gog.com/en/games?query={q}")
    static let playstation = Store(id: "playstation", name: "PlayStation Store", monogram: "PS", colorHex: "0070D1",
                                   platformIDs: ["ps5", "ps4"], searchTemplate: "https://store.playstation.com/en-us/search/{q}")
    static let xbox = Store(id: "xbox", name: "Xbox Store", monogram: "XB", colorHex: "107C10", platformIDs: ["xbox-series", "xbox-one", "pc"],
                            searchTemplate: "https://www.xbox.com/en-US/search/results/games?q={q}")
    static let nintendo = Store(id: "nintendo", name: "Nintendo eShop", monogram: "N", colorHex: "E60012", platformIDs: ["switch2", "switch"],
                                searchTemplate: "https://www.nintendo.com/us/search/#q={q}&p=1&cat=gme&sort=df")
    static let appStore = Store(id: "appstore", name: "App Store", monogram: "AS", colorHex: "0A84FF", platformIDs: ["mobile", "mac"],
                                searchTemplate: "https://apps.apple.com/us/search?term={q}")
    static let googlePlay = Store(id: "googleplay", name: "Google Play", monogram: "GP", colorHex: "01875F", platformIDs: ["mobile"],
                                  searchTemplate: "https://play.google.com/store/search?q={q}&c=apps")

    static let all: [Store] = [.steam, .epic, .gog, .playstation, .xbox, .nintendo, .appStore, .googlePlay]

    /// Stores serving any of a game's platforms; those serving my platforms come first.
    static func offers(for game: Game, myPlatforms: Set<String>) -> [StoreOffer] {
        let platforms = Set(game.platforms)
        return all
            .filter { !platforms.isDisjoint(with: $0.platformIDs) }
            .compactMap { store in
                store.link(for: game).map { link in
                    StoreOffer(store: store, link: link,
                               isMine: !myPlatforms.isDisjoint(with: Set(store.platformIDs).intersection(platforms)))
                }
            }
            .sorted { $0.isMine && !$1.isMine }
    }
}

/// One store row in Where to Play.
struct StoreOffer: Identifiable, Hashable {
    var store: Store
    var link: URL
    /// Whether the store serves one of my platforms for this game.
    var isMine: Bool
    var price: String?
    var regularPrice: String?
    var id: String { store.id }
}

/// A PC price from CheapShark.
struct Deal: Identifiable, Hashable {
    var id: String
    var storeName: String
    var price: Double
    var retailPrice: Double
    var savings: Double
    var link: URL

    var isDiscounted: Bool { savings >= 1 && price < retailPrice }
}

extension CharacterSet {
    /// Query-value safe: unlike `.urlQueryAllowed` it also escapes `&`, `=`, `+`, `#` and `?`.
    static let urlQueryValueAllowed: CharacterSet = {
        var set = CharacterSet.urlQueryAllowed
        set.remove(charactersIn: "&=+#?/")
        return set
    }()
}

extension Double {
    /// "$4.99" in US dollars (CheapShark prices are USD).
    var usd: String { formatted(.currency(code: "USD")) }
}
