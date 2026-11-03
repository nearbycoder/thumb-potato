import SwiftUI

/// The fixed genre list the catalog, filters, moods and Shuffle all share (SPEC.md).
enum Genre: String, CaseIterable, Identifiable, Hashable, Codable {
    case action = "Action", adventure = "Adventure", rpg = "RPG", shooter = "Shooter", platformer = "Platformer"
    case puzzle = "Puzzle", strategy = "Strategy", simulation = "Simulation", sports = "Sports", racing = "Racing"
    case fighting = "Fighting", horror = "Horror", roguelike = "Roguelike", metroidvania = "Metroidvania"
    case party = "Party", music = "Music", visualNovel = "Visual Novel", survival = "Survival", sandbox = "Sandbox"

    var id: String { rawValue }
    var label: String { rawValue }

    var symbol: String {
        switch self {
        case .action: "bolt"
        case .adventure: "map"
        case .rpg: "shield"
        case .shooter: "scope"
        case .platformer: "figure.run"
        case .puzzle: "puzzlepiece"
        case .strategy: "checkerboard.rectangle"
        case .simulation: "building.2"
        case .sports: "sportscourt"
        case .racing: "flag.checkered"
        case .fighting: "figure.boxing"
        case .horror: "moon.stars"
        case .roguelike: "arrow.triangle.2.circlepath"
        case .metroidvania: "map.fill"
        case .party: "party.popper"
        case .music: "music.note"
        case .visualNovel: "book"
        case .survival: "flame"
        case .sandbox: "cube"
        }
    }

    /// Name fragments from Steam, RAWG and Wikidata genre labels, checked in order
    /// (mirrors scripts/build_catalog.py's GENRES table).
    private static let fragments: [(Genre, [String])] = [
        (.roguelike, ["roguelike", "roguelite", "rogue-like", "rogue-lite"]),
        (.metroidvania, ["metroidvania"]),
        (.platformer, ["platform"]),
        (.shooter, ["shooter", "shoot 'em up", "run and gun", "battle royale", "fps"]),
        (.fighting, ["fighting", "beat 'em up", "hack and slash", "brawler"]),
        (.rpg, ["role-playing", "rpg", "dungeon crawl", "otome"]),
        (.strategy, ["strategy", "tactic", "4x", "tower defense", "moba", "auto battler", "card", "deck-building", "board"]),
        (.simulation, ["simulat", "city-building", "management", "life sim", "dating sim", "farming", "god game"]),
        (.sports, ["sport", "football", "soccer", "basketball", "golf", "tennis", "baseball", "hockey", "skate", "wrestling", "boxing"]),
        (.racing, ["racing", "kart", "driving"]),
        (.horror, ["horror"]),
        (.puzzle, ["puzzle", "match-3", "tile-matching", "logic"]),
        (.party, ["party", "trivia", "minigame", "family"]),
        (.music, ["rhythm", "music"]),
        (.visualNovel, ["visual novel", "interactive fiction", "interactive film"]),
        (.survival, ["survival", "crafting"]),
        (.sandbox, ["sandbox", "open world", "open-world"]),
        (.adventure, ["adventure", "walking simulator", "point-and-click", "stealth"]),
        (.action, ["action", "arcade"]),
    ]

    /// Maps source genre names onto the fixed list, keeping order and dropping unknowns
    /// ("Massively Multiplayer", "Indie", "Free to Play"). "Casual" becomes Puzzle only
    /// when nothing else matched.
    static func map(_ names: [String]) -> [String] {
        var result: [Genre] = []
        for name in names {
            let lower = name.lowercased()
            if let exact = Genre.allCases.first(where: { $0.rawValue.lowercased() == lower }) {
                if !result.contains(exact) { result.append(exact) }
                continue
            }
            if let (genre, _) = fragments.first(where: { _, frags in frags.contains { lower.contains($0) } }),
               !result.contains(genre) {
                result.append(genre)
            }
        }
        if result.isEmpty, names.contains(where: { $0.lowercased() == "casual" }) { result = [.puzzle] }
        return result.map(\.rawValue)
    }
}

/// Moods, each mapped onto genres from the fixed list.
enum Mood: String, CaseIterable, Identifiable, Hashable {
    case cozy, adrenaline, epic, coop, spooky, brainy

    var id: String { rawValue }

    var title: String {
        switch self {
        case .cozy: "Cozy"
        case .adrenaline: "Adrenaline"
        case .epic: "Epic"
        case .coop: "Couch Co-op"
        case .spooky: "Spooky"
        case .brainy: "Brainy"
        }
    }

    var symbol: String {
        switch self {
        case .cozy: "cup.and.saucer"
        case .adrenaline: "bolt"
        case .epic: "crown"
        case .coop: "person.2"
        case .spooky: "moon.stars"
        case .brainy: "puzzlepiece"
        }
    }

    var blurb: String {
        switch self {
        case .cozy: "Unwind with something gentle"
        case .adrenaline: "Fast, loud and competitive"
        case .epic: "Big worlds and long stories"
        case .coop: "Games for the whole couch"
        case .spooky: "Horror and survival"
        case .brainy: "Puzzles, tactics and stories to think about"
        }
    }

    /// Duotone gradient (top-leading to bottom-trailing).
    var gradient: [Color] {
        switch self {
        case .cozy: [Color(hex: 0xE09A62), Color(hex: 0x5A2E1A)]
        case .adrenaline: [Color(hex: 0xE2473F), Color(hex: 0x3B0B0B)]
        case .epic: [Color(hex: 0x5B7FE8), Color(hex: 0x121C4A)]
        case .coop: [Color(hex: 0xF0C94A), Color(hex: 0x5C4300)]
        case .spooky: [Color(hex: 0x8A5CF5), Color(hex: 0x22104D)]
        case .brainy: [Color(hex: 0x3DBB9E), Color(hex: 0x0B3A33)]
        }
    }

    var tint: Color { gradient[0] }

    var genres: [Genre] {
        switch self {
        case .cozy: [.simulation, .sandbox, .puzzle, .party, .music]
        case .adrenaline: [.shooter, .fighting, .racing, .action, .sports]
        case .epic: [.rpg, .adventure, .metroidvania]
        case .coop: [.party, .sports, .racing, .fighting, .platformer]
        case .spooky: [.horror, .survival]
        case .brainy: [.puzzle, .strategy, .visualNovel]
        }
    }

    func matches(_ game: Game) -> Bool { Genre.overlaps(game.genres, genres) }
}

/// How long you have to play, for Shuffle.
enum SessionLength: String, CaseIterable, Identifiable, Hashable {
    case quick, hour, long

    var id: String { rawValue }

    var label: String {
        switch self {
        case .quick: "Quick break"
        case .hour: "An hour"
        case .long: "Long session"
        }
    }

    var genres: [Genre] {
        switch self {
        case .quick: [.puzzle, .party, .racing, .fighting, .sports, .music, .roguelike, .platformer]
        case .hour: [.action, .shooter, .metroidvania, .adventure, .horror, .platformer, .roguelike]
        case .long: [.rpg, .strategy, .simulation, .sandbox, .survival, .visualNovel, .adventure]
        }
    }

    /// Suggested minutes when planning a session.
    var minutes: Int {
        switch self {
        case .quick: 30
        case .hour: 60
        case .long: 180
        }
    }

    func matches(_ game: Game) -> Bool { Genre.overlaps(game.genres, genres) }
}

extension Genre {
    static func overlaps(_ names: [String], _ genres: [Genre]) -> Bool {
        let wanted = Set(genres.map(\.rawValue))
        return names.contains { wanted.contains($0) }
    }
}
