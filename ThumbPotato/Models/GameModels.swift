import Foundation

enum GameSource: String, Codable, Hashable {
    /// Wikidata item (the published catalog, Wikipedia search).
    case wd
    case steam
    case rawg

    var displayName: String {
        switch self {
        case .wd: "Wikidata"
        case .steam: "Steam"
        case .rawg: "RAWG"
        }
    }
}

/// A stable pointer to a game in one of the sources, e.g. `wd-Q17185964` or `steam-1145360`.
struct GameRef: Hashable, Codable, Identifiable {
    var source: GameSource
    var id: String

    var key: String { "\(source.rawValue)-\(id)" }

    init(source: GameSource, id: String) {
        self.source = source
        self.id = id
    }

    init?(key: String) {
        let parts = key.split(separator: "-", maxSplits: 1).map(String.init)
        guard parts.count == 2, !parts[1].isEmpty, let source = GameSource(rawValue: parts[0]) else { return nil }
        self.init(source: source, id: parts[1])
    }

    var deepLink: URL { URL(string: "thumbpotato://game/\(key)")! }

    var webURL: URL? {
        switch source {
        case .wd: URL(string: "https://www.wikidata.org/wiki/\(id)")
        case .steam: URL(string: "https://store.steampowered.com/app/\(id)")
        case .rawg: URL(string: "https://rawg.io/games/\(id)")
        }
    }
}

/// How precisely a release date is known. Coarse dates sit on the 1st of their year or month.
enum DatePrecision: String, Codable, Hashable {
    case day, month, year
}

struct Game: Identifiable, Hashable {
    var ref: GameRef
    var name: String
    var summary: String = ""
    /// Box art from Wikipedia.
    var coverURL: URL?
    /// Steam library capsule (portrait), the box-art fallback.
    var artURL: URL?
    /// Steam library hero (wide backdrop).
    var heroURL: URL?
    var releaseDate: Date?
    var releaseDatePrecision: DatePrecision = .day
    var year: Int?
    var genres: [String] = []
    var developer: String?
    var publisher: String?
    /// Platform ids.
    var platforms: [String] = []
    /// 0–100.
    var metacritic: Int?
    var steamAppID: Int?
    var wikiTitle: String?
    var popularity: Double = 0
    /// Steam Deck compatibility: 1 unknown/unsupported, 2 playable, 3 verified.
    var steamDeck: Int?
    /// A transient price line for deal rows, e.g. "$4.99 · −75%".
    var priceNote: String?

    var id: String { ref.key }

    static func == (lhs: Game, rhs: Game) -> Bool { lhs.ref == rhs.ref }
    func hash(into hasher: inout Hasher) { hasher.combine(ref) }

    var boxArtURL: URL? { coverURL ?? artURL }
    var platformList: [Platform] { Platform.list(platforms) }

    /// Not out yet. A coarse date ("2027", "March 2027") counts until its whole period has passed.
    var isUpcoming: Bool {
        guard let releaseDate else { return false }
        return ReleaseDateFormat.isUpcoming(releaseDate, precision: releaseDatePrecision)
    }

    /// "2025 · Action" style caption under a cover.
    var subtitle: String {
        if let priceNote { return priceNote }
        var bits: [String] = []
        if isUpcoming, let label = releaseLabel { bits.append(label) } else if let year { bits.append(String(year)) }
        if let genre = genres.first { bits.append(genre) }
        return bits.joined(separator: " · ")
    }

    /// "2025 · Action, RPG · Metacritic 92".
    var metaLine: String {
        var bits: [String] = []
        if isUpcoming, let label = releaseLabel { bits.append(label) } else if let year { bits.append(String(year)) }
        if !genres.isEmpty { bits.append(genres.prefix(2).joined(separator: ", ")) }
        if let metacritic { bits.append("Metacritic \(metacritic)") }
        return bits.joined(separator: " · ")
    }

    /// "May 12, 2023", "March 2027" or "2027", honoring the precision.
    var releaseLabel: String? {
        releaseDate.map { ReleaseDateFormat.label($0, precision: releaseDatePrecision) }
    }
}

enum ReleaseDateFormat {
    static func isUpcoming(_ date: Date, precision: DatePrecision, now: Date = .now) -> Bool {
        let cal = Calendar.current
        switch precision {
        case .day: return cal.startOfDay(for: date) > cal.startOfDay(for: now)
        case .month: return (cal.date(byAdding: .month, value: 1, to: date) ?? date) > now
        case .year: return (cal.date(byAdding: .year, value: 1, to: date) ?? date) > now
        }
    }

    static func label(_ date: Date, precision: DatePrecision) -> String {
        switch precision {
        case .day: date.formatted(.dateTime.month(.abbreviated).day().year())
        case .month: date.formatted(.dateTime.month(.wide).year())
        case .year: date.formatted(.dateTime.year())
        }
    }

    /// Release Radar wording: coarse dates read "TBA 2027" / "March 2027".
    static func radarLabel(_ date: Date, precision: DatePrecision) -> String {
        switch precision {
        case .year: "TBA \(date.formatted(.dateTime.year()))"
        default: label(date, precision: precision)
        }
    }

    /// "Today", "Tomorrow", "in 12 days" for exact dates; nil for coarse ones.
    static func countdown(_ date: Date, precision: DatePrecision, now: Date = .now) -> String? {
        guard precision == .day else { return nil }
        let cal = Calendar.current
        let days = cal.dateComponents([.day], from: cal.startOfDay(for: now), to: cal.startOfDay(for: date)).day ?? 0
        switch days {
        case ..<0: return "Out now"
        case 0: return "Today"
        case 1: return "Tomorrow"
        default: return "in \(days) days"
        }
    }
}

struct GameDetail {
    var game: Game
    var screenshots: [URL] = []
    var trailerURL: URL?
    var stores: [StoreOffer] = []
    var pcDeals: [Deal] = []
    var similar: [Game] = []
    var website: URL?
    /// "Very Positive · 91%".
    var steamReviews: String?
}

extension String {
    var strippingHTML: String {
        replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
            .replacingOccurrences(of: "&amp;", with: "&")
            .replacingOccurrences(of: "&quot;", with: "\"")
            .replacingOccurrences(of: "&#39;", with: "'")
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    /// Lowercased letters and digits only, for fuzzy name matching across sources.
    var normalizedTitle: String {
        lowercased().folding(options: .diacriticInsensitive, locale: nil).filter { $0.isLetter || $0.isNumber }
    }
}

extension Int {
    var durationLabel: String {
        let h = self / 60, m = self % 60
        if h == 0 { return "\(m)m" }
        return m == 0 ? "\(h)h" : "\(h)h \(m)m"
    }
}
