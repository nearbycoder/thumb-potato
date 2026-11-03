import Foundation

/// Filters and sort order for browse pages (platforms, moods, genres, See All lists).
/// Runs locally on the catalog and is translated to RAWG query parameters when a key is set.
struct GameFilter: Hashable {
    enum Sort: String, CaseIterable, Identifiable {
        case popular, newest, rating, title
        var id: String { rawValue }
        var label: String {
            switch self {
            case .popular: "Popular"
            case .newest: "Newest"
            case .rating: "Top Rated"
            case .title: "A–Z"
            }
        }
    }

    enum Released: String, CaseIterable, Identifiable {
        case any, upcoming, thisYear, last5, y2020s, y2010s, y2000s, older
        var id: String { rawValue }
        var label: String {
            switch self {
            case .any: "Any Time"
            case .upcoming: "Upcoming"
            case .thisYear: "This Year"
            case .last5: "Last 5 Years"
            case .y2020s: "2020s"
            case .y2010s: "2010s"
            case .y2000s: "2000s"
            case .older: "Before 2000"
            }
        }

        /// Inclusive year bounds; nil means open-ended. Upcoming is handled by date instead.
        var bounds: (from: Int?, to: Int?) {
            let now = Calendar.current.component(.year, from: .now)
            switch self {
            case .any, .upcoming: return (nil, nil)
            case .thisYear: return (now, now)
            case .last5: return (now - 4, now)
            case .y2020s: return (2020, 2029)
            case .y2010s: return (2010, 2019)
            case .y2000s: return (2000, 2009)
            case .older: return (nil, 1999)
            }
        }
    }

    static let scoreSteps: [Int] = [70, 80, 90]

    var sort: Sort = .popular
    var released: Released = .any
    var genre: Genre?
    var minScore: Int?

    var isDefault: Bool { self == GameFilter() }

    /// Stable key for caches and view identity.
    var key: String {
        "\(sort.rawValue)|\(released.rawValue)|\(genre?.rawValue ?? "-")|\(minScore.map(String.init) ?? "-")"
    }

    func matches(_ game: Game, now: Date = .now) -> Bool {
        if released == .upcoming {
            guard game.isUpcoming else { return false }
        } else {
            let (from, to) = released.bounds
            if from != nil || to != nil {
                guard let year = game.year else { return false }
                if let from, year < from { return false }
                if let to, year > to { return false }
            }
        }
        if let genre, !game.genres.contains(genre.rawValue) { return false }
        if let minScore, (game.metacritic ?? 0) < minScore { return false }
        return true
    }

    /// Filters, then sorts. `.popular` orders by the catalog's popularity, keeping ties stable.
    func apply(_ games: [Game]) -> [Game] {
        let kept = games.filter { matches($0) }
        switch sort {
        case .popular:
            // Swift's sort is stable, so equal popularity keeps the incoming order.
            return kept.sorted { $0.popularity > $1.popularity }
        case .newest:
            // Upcoming lists read soonest first; everything else is newest released first.
            if released == .upcoming {
                return kept.sorted { ($0.releaseDate ?? .distantFuture) < ($1.releaseDate ?? .distantFuture) }
            }
            return kept
                .filter { !$0.isUpcoming }
                .sorted { ($0.releaseDate ?? .distantPast) > ($1.releaseDate ?? .distantPast) }
        case .rating:
            return kept.sorted { a, b in
                let sa = a.metacritic ?? -1, sb = b.metacritic ?? -1
                return sa != sb ? sa > sb : a.popularity > b.popularity
            }
        case .title:
            return kept.sorted { $0.name.localizedCaseInsensitiveCompare($1.name) == .orderedAscending }
        }
    }

    // MARK: RAWG

    /// `/games` query parameters for this filter.
    func rawgQuery(now: Date = .now) -> [String: String?] {
        var query: [String: String?] = [:]
        switch sort {
        case .popular: query["ordering"] = "-added"
        case .newest: query["ordering"] = released == .upcoming ? "released" : "-released"
        case .rating: query["ordering"] = "-metacritic"
        case .title: query["ordering"] = "name"
        }
        let today = DateParsing.dayString(now)
        if released == .upcoming {
            let tomorrow = Calendar.current.date(byAdding: .day, value: 1, to: now) ?? now
            query["dates"] = "\(DateParsing.dayString(tomorrow)),2099-12-31"
        } else {
            let (from, to) = released.bounds
            if from != nil || to != nil {
                let start = from.map { "\($0)-01-01" } ?? "1970-01-01"
                var end = to.map { "\($0)-12-31" } ?? today
                if end > today { end = today }
                query["dates"] = "\(start),\(end)"
            } else if sort == .newest {
                query["dates"] = "1970-01-01,\(today)"
            }
        }
        if let genre { query["genres"] = genre.rawgSlug }
        if let minScore { query["metacritic"] = "\(minScore),100" }
        return query
    }
}

extension Genre {
    /// RAWG genre slugs; genres RAWG lacks map to their nearest tag-like genre.
    var rawgSlug: String {
        switch self {
        case .action: "action"
        case .adventure: "adventure"
        case .rpg: "role-playing-games-rpg"
        case .shooter: "shooter"
        case .platformer: "platformer"
        case .puzzle: "puzzle"
        case .strategy: "strategy"
        case .simulation: "simulation"
        case .sports: "sports"
        case .racing: "racing"
        case .fighting: "fighting"
        case .horror: "action"
        case .roguelike: "action"
        case .metroidvania: "platformer"
        case .party: "family"
        case .music: "casual"
        case .visualNovel: "adventure"
        case .survival: "adventure"
        case .sandbox: "simulation"
        }
    }
}
