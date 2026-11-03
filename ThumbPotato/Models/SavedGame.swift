import SwiftUI
import SwiftData

enum BacklogStatus: String, CaseIterable, Identifiable, Codable {
    case queued, playing, beaten, shelved

    var id: String { rawValue }

    var label: String {
        switch self {
        case .queued: "Want to Play"
        case .playing: "Playing"
        case .beaten: "Beaten"
        case .shelved: "Shelved"
        }
    }

    var symbol: String {
        switch self {
        case .queued: "bookmark"
        case .playing: "gamecontroller"
        case .beaten: "trophy"
        case .shelved: "archivebox"
        }
    }

    var color: Color {
        switch self {
        case .queued: Theme.info
        case .playing: Theme.accent
        case .beaten: Theme.success
        case .shelved: Theme.textTertiary
        }
    }
}

/// A game in your backlog. Synced through CloudKit, so every property has a default
/// and nothing is unique-constrained (duplicates from concurrent devices are merged
/// by `Library.dedupe`).
@Model
final class SavedGame {
    var key: String = ""
    var sourceRaw: String = GameSource.wd.rawValue
    var externalID: String = ""
    var name: String = ""
    var summary: String = ""
    var coverURLString: String?
    var heroURLString: String?
    var releaseDate: Date?
    /// "month" or "year" when the release date is coarse; nil for an exact day.
    var releaseDatePrecisionRaw: String?
    var year: Int?
    var genresCSV: String = ""
    var platformIDsCSV: String = ""
    var playingOn: String?
    var statusRaw: String = BacklogStatus.queued.rawValue
    var rating: Int = 0
    var notes: String = ""
    var hoursPlayed: Double = 0
    var progress: Int = 0
    var owned: Bool = false
    var isFollowing: Bool = false
    var steamAppID: Int?
    var wikiTitle: String?
    var metacritic: Int?
    var addedAt: Date = Date.now
    var updatedAt: Date = Date.now
    var finishedAt: Date?

    init(from game: Game, status: BacklogStatus = .queued) {
        key = game.ref.key
        sourceRaw = game.ref.source.rawValue
        externalID = game.ref.id
        statusRaw = status.rawValue
        // Adding an unreleased game follows it for a release-day alert.
        isFollowing = game.isUpcoming && status == .queued
        update(from: game)
    }

    func update(from game: Game) {
        name = game.name
        if !game.summary.isEmpty { summary = game.summary }
        coverURLString = game.boxArtURL?.absoluteString ?? coverURLString
        heroURLString = game.heroURL?.absoluteString ?? heroURLString
        if let date = game.releaseDate {
            releaseDate = date
            releaseDatePrecisionRaw = game.releaseDatePrecision == .day ? nil : game.releaseDatePrecision.rawValue
        }
        year = game.year ?? year
        if !game.genres.isEmpty { genresCSV = game.genres.joined(separator: ",") }
        if !game.platforms.isEmpty { platformIDsCSV = game.platforms.joined(separator: ",") }
        steamAppID = game.steamAppID ?? steamAppID
        wikiTitle = game.wikiTitle ?? wikiTitle
        metacritic = game.metacritic ?? metacritic
    }

    var ref: GameRef { GameRef(source: GameSource(rawValue: sourceRaw) ?? .wd, id: externalID) }

    var status: BacklogStatus {
        get { BacklogStatus(rawValue: statusRaw) ?? .queued }
        set {
            statusRaw = newValue.rawValue
            updatedAt = .now
            if newValue == .beaten {
                finishedAt = finishedAt ?? .now
                progress = 100
            }
        }
    }

    var releaseDatePrecision: DatePrecision {
        releaseDatePrecisionRaw.flatMap(DatePrecision.init(rawValue:)) ?? .day
    }

    var coverURL: URL? { coverURLString.flatMap(URL.init(string:)) }
    var heroURL: URL? { heroURLString.flatMap(URL.init(string:)) }
    var genres: [String] { genresCSV.split(separator: ",").map(String.init) }

    var platformIDs: [String] {
        get { platformIDsCSV.split(separator: ",").map(String.init) }
        set { platformIDsCSV = newValue.joined(separator: ",") }
    }

    var platforms: [Platform] { Platform.list(platformIDs) }
    var playingOnPlatform: Platform? { playingOn.flatMap(Platform.with(id:)) }

    var isUpcoming: Bool {
        releaseDate.map { ReleaseDateFormat.isUpcoming($0, precision: releaseDatePrecision) } ?? false
    }

    /// Exact-day future releases get a 9 AM alert; coarse ones never do.
    var wantsReleaseAlert: Bool {
        isFollowing && releaseDatePrecision == .day && isUpcoming
    }

    /// A lightweight catalog value so saved games can reuse catalog views.
    var asGame: Game {
        Game(ref: ref, name: name, summary: summary, coverURL: coverURL, heroURL: heroURL, releaseDate: releaseDate,
             releaseDatePrecision: releaseDatePrecision, year: year, genres: genres, platforms: platformIDs,
             metacritic: metacritic, steamAppID: steamAppID, wikiTitle: wikiTitle)
    }
}

/// A planned play session on the Planner.
@Model
final class PlaySession {
    var id: UUID = UUID()
    var gameKey: String = ""
    var gameName: String = ""
    var coverURLString: String?
    var platformID: String?
    var startsAt: Date = Date.now
    var durationMinutes: Int = 60
    var note: String = ""
    var calendarEventID: String?
    var hasReminder: Bool = true
    var isDone: Bool = false
    var createdAt: Date = Date.now

    init(gameKey: String, gameName: String, coverURL: URL?, platformID: String?, startsAt: Date,
         durationMinutes: Int, note: String = "", hasReminder: Bool = true) {
        self.gameKey = gameKey
        self.gameName = gameName
        self.coverURLString = coverURL?.absoluteString
        self.platformID = platformID
        self.startsAt = startsAt
        self.durationMinutes = durationMinutes
        self.note = note
        self.hasReminder = hasReminder
    }

    var coverURL: URL? { coverURLString.flatMap(URL.init(string:)) }
    var endsAt: Date { startsAt.addingTimeInterval(TimeInterval(durationMinutes * 60)) }
    var ref: GameRef? { GameRef(key: gameKey) }
    var platform: Platform? { platformID.flatMap(Platform.with(id:)) }
}
