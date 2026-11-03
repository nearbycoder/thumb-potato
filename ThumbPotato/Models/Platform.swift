import SwiftUI

/// A console family, PC, Mac or mobile. Ids are shared with the catalog files,
/// backups, settings and the Android app (see SPEC.md).
struct Platform: Identifiable, Hashable, Codable {
    var id: String
    var name: String
    var shortName: String
    var monogram: String
    var colorHex: String
    var rawgIDs: [Int]

    var color: Color { Color(hexString: colorHex) }

    static func == (lhs: Platform, rhs: Platform) -> Bool { lhs.id == rhs.id }
    func hash(into hasher: inout Hasher) { hasher.combine(id) }
}

extension Platform {
    static let switch2 = Platform(id: "switch2", name: "Nintendo Switch 2", shortName: "Switch 2", monogram: "NS2", colorHex: "E60012", rawgIDs: [])
    static let nswitch = Platform(id: "switch", name: "Nintendo Switch", shortName: "Switch", monogram: "NS", colorHex: "E60012", rawgIDs: [7])
    static let ps5 = Platform(id: "ps5", name: "PlayStation 5", shortName: "PS5", monogram: "PS5", colorHex: "0070D1", rawgIDs: [187])
    static let ps4 = Platform(id: "ps4", name: "PlayStation 4", shortName: "PS4", monogram: "PS4", colorHex: "003791", rawgIDs: [18])
    static let xboxSeries = Platform(id: "xbox-series", name: "Xbox Series X|S", shortName: "Xbox Series", monogram: "XS", colorHex: "107C10", rawgIDs: [186])
    static let xboxOne = Platform(id: "xbox-one", name: "Xbox One", shortName: "Xbox One", monogram: "XB1", colorHex: "0E6A0E", rawgIDs: [1])
    static let pc = Platform(id: "pc", name: "PC", shortName: "PC", monogram: "PC", colorHex: "2A475E", rawgIDs: [4])
    static let mac = Platform(id: "mac", name: "Mac", shortName: "Mac", monogram: "MAC", colorHex: "A2AAAD", rawgIDs: [5])
    static let mobile = Platform(id: "mobile", name: "iOS & Android", shortName: "Mobile", monogram: "MOB", colorHex: "5E5CE6", rawgIDs: [3, 21])

    /// Every platform, in display order.
    static let all: [Platform] = [.switch2, .nswitch, .ps5, .ps4, .xboxSeries, .xboxOne, .pc, .mac, .mobile]

    static func with(id: String) -> Platform? { all.first { $0.id == id } }

    /// Known platforms for a list of ids, in display order.
    static func list(_ ids: [String]) -> [Platform] {
        let set = Set(ids)
        return all.filter { set.contains($0.id) }
    }

    /// Wikidata platform items, as scripts/build_catalog.py maps them.
    static let wikidataItems: [String: String] = [
        "Q122761124": "switch2", "Q19610114": "switch", "Q63184502": "ps5", "Q5014725": "ps4",
        "Q98973368": "xbox-series", "Q13361286": "xbox-one", "Q1406": "pc", "Q14116": "mac",
        "Q48493": "mobile", "Q94": "mobile",
    ]

    /// Maps a free-form platform name (RAWG, Steam) to a platform id.
    static func matching(name: String) -> Platform? {
        let n = name.lowercased()
        if n.contains("switch 2") { return .switch2 }
        if n.contains("switch") { return .nswitch }
        if n.contains("playstation 5") || n == "ps5" { return .ps5 }
        if n.contains("playstation 4") || n == "ps4" { return .ps4 }
        if n.contains("series") && n.contains("xbox") { return .xboxSeries }
        if n.contains("xbox one") { return .xboxOne }
        if n == "pc" || n.contains("windows") { return .pc }
        if n.contains("macos") || n == "mac" || n.contains("macintosh") { return .mac }
        if n == "ios" || n == "android" { return .mobile }
        return nil
    }
}
