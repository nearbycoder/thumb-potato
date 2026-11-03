import Foundation
import SwiftData
import SwiftUI
import UniformTypeIdentifiers

/// Portable JSON for the backlog and sessions, shared with the Android app (SPEC.md
/// "Backup format"): ISO-8601 timestamps, day-only release dates, stable game keys.
/// iCloud keeps Apple devices in sync; this file moves a library to or from Android.
enum Backup {
    static let format = "thumbpotato.backup"
    static let version = 1

    struct File: Codable {
        var format = Backup.format
        var version = Backup.version
        var exportedAt: Date
        var games: [GameEntry]
        var sessions: [SessionEntry]
    }

    struct GameEntry: Codable {
        var key: String
        var source: String
        var externalID: String
        var name: String
        var summary: String?
        var coverURL: String?
        var heroURL: String?
        /// "YYYY-MM-DD".
        var releaseDate: String?
        /// "month" or "year"; absent for an exact day.
        var releaseDatePrecision: String?
        var year: Int?
        var genres: [String]?
        var platformIDs: [String]?
        var playingOn: String?
        var status: String?
        var rating: Int?
        var notes: String?
        var hoursPlayed: Double?
        var progress: Int?
        var owned: Bool?
        var isFollowing: Bool?
        var steamAppID: Int?
        var wikiTitle: String?
        var metacritic: Int?
        var addedAt: Date?
        var updatedAt: Date?
        var finishedAt: Date?

        init(_ g: SavedGame) {
            key = g.key
            source = g.sourceRaw
            externalID = g.externalID
            name = g.name
            summary = g.summary
            coverURL = g.coverURLString
            heroURL = g.heroURLString
            releaseDate = g.releaseDate.map(DateParsing.dayString)
            releaseDatePrecision = g.releaseDatePrecisionRaw
            year = g.year
            genres = g.genres
            platformIDs = g.platformIDs
            playingOn = g.playingOn
            status = g.statusRaw
            rating = g.rating
            notes = g.notes
            hoursPlayed = g.hoursPlayed
            progress = g.progress
            owned = g.owned
            isFollowing = g.isFollowing
            steamAppID = g.steamAppID
            wikiTitle = g.wikiTitle
            metacritic = g.metacritic
            addedAt = g.addedAt
            updatedAt = g.updatedAt
            finishedAt = g.finishedAt
        }

        // Always write optionals as explicit nulls, matching the documented shape.
        func encode(to encoder: Encoder) throws {
            var c = encoder.container(keyedBy: CodingKeys.self)
            try c.encode(key, forKey: .key)
            try c.encode(source, forKey: .source)
            try c.encode(externalID, forKey: .externalID)
            try c.encode(name, forKey: .name)
            try c.encode(summary, forKey: .summary)
            try c.encode(coverURL, forKey: .coverURL)
            try c.encode(heroURL, forKey: .heroURL)
            try c.encode(releaseDate, forKey: .releaseDate)
            try c.encodeIfPresent(releaseDatePrecision, forKey: .releaseDatePrecision)
            try c.encode(year, forKey: .year)
            try c.encode(genres, forKey: .genres)
            try c.encode(platformIDs, forKey: .platformIDs)
            try c.encode(playingOn, forKey: .playingOn)
            try c.encode(status, forKey: .status)
            try c.encode(rating, forKey: .rating)
            try c.encode(notes, forKey: .notes)
            try c.encode(hoursPlayed, forKey: .hoursPlayed)
            try c.encode(progress, forKey: .progress)
            try c.encode(owned, forKey: .owned)
            try c.encode(isFollowing, forKey: .isFollowing)
            try c.encode(steamAppID, forKey: .steamAppID)
            try c.encode(wikiTitle, forKey: .wikiTitle)
            try c.encode(metacritic, forKey: .metacritic)
            try c.encode(addedAt, forKey: .addedAt)
            try c.encode(updatedAt, forKey: .updatedAt)
            try c.encode(finishedAt, forKey: .finishedAt)
        }
    }

    struct SessionEntry: Codable {
        var id: String?
        var gameKey: String
        var gameName: String?
        var coverURL: String?
        var platformID: String?
        var startsAt: Date
        var durationMinutes: Int?
        var note: String?
        var hasReminder: Bool?
        var isDone: Bool?
        var createdAt: Date?

        init(_ s: PlaySession) {
            id = s.id.uuidString
            gameKey = s.gameKey
            gameName = s.gameName
            coverURL = s.coverURLString
            platformID = s.platformID
            startsAt = s.startsAt
            durationMinutes = s.durationMinutes
            note = s.note
            hasReminder = s.hasReminder
            isDone = s.isDone
            createdAt = s.createdAt
        }
    }

    struct Summary {
        var added = 0
        var updated = 0
        var sessions = 0
        var message: String {
            "Added \(added) game\(added == 1 ? "" : "s"), updated \(updated), and imported \(sessions) session\(sessions == 1 ? "" : "s")."
        }
    }

    // MARK: Export

    static func export(games: [SavedGame], sessions: [PlaySession], now: Date = .now) throws -> Data {
        let file = File(exportedAt: now, games: games.map(GameEntry.init), sessions: sessions.map(SessionEntry.init))
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        return try encoder.encode(file)
    }

    static func decode(_ data: Data) throws -> File {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .custom { decoder in
            let container = try decoder.singleValueContainer()
            if let seconds = try? container.decode(Double.self) {
                // Epoch seconds or milliseconds.
                return Date(timeIntervalSince1970: seconds > 100_000_000_000 ? seconds / 1000 : seconds)
            }
            let string = try container.decode(String.self)
            if let date = parseISO(string) ?? DateParsing.day.date(from: string) { return date }
            throw DecodingError.dataCorruptedError(in: container, debugDescription: "Unrecognized date \(string)")
        }
        let file = try decoder.decode(File.self, from: data)
        guard file.format == format else {
            throw DecodingError.dataCorrupted(.init(codingPath: [], debugDescription: "Not a Thumb Potato backup"))
        }
        return file
    }

    // MARK: Import

    /// Merges a backup into the library. A game already here is only overwritten when
    /// the file's copy is newer; sessions are matched by id, then by game and start time.
    @discardableResult
    static func merge(_ data: Data, into context: ModelContext) throws -> Summary {
        let file = try decode(data)
        var summary = Summary()

        let existing = (try? context.fetch(FetchDescriptor<SavedGame>())) ?? []
        var byKey: [String: SavedGame] = [:]
        for item in existing where (byKey[item.key]?.updatedAt ?? .distantPast) <= item.updatedAt { byKey[item.key] = item }
        for item in file.games {
            guard let ref = GameRef(key: item.key) else { continue }
            let incomingUpdated = item.updatedAt ?? item.addedAt ?? .now
            if let current = byKey[item.key] {
                guard incomingUpdated > current.updatedAt else { continue }
                apply(item, to: current)
                summary.updated += 1
            } else {
                let saved = SavedGame(from: Game(ref: ref, name: item.name))
                apply(item, to: saved)
                context.insert(saved)
                byKey[item.key] = saved
                summary.added += 1
            }
        }

        let sessions = (try? context.fetch(FetchDescriptor<PlaySession>())) ?? []
        var ids = Set(sessions.map(\.id))
        var slots = Set(sessions.map { "\($0.gameKey)|\(Int($0.startsAt.timeIntervalSince1970 / 60))" })
        for item in file.sessions {
            let id = item.id.flatMap(UUID.init(uuidString:))
            let slot = "\(item.gameKey)|\(Int(item.startsAt.timeIntervalSince1970 / 60))"
            if let id, ids.contains(id) { continue }
            if slots.contains(slot) { continue }
            let session = PlaySession(gameKey: item.gameKey, gameName: item.gameName ?? "Play session",
                                      coverURL: item.coverURL.flatMap(URL.init(string:)), platformID: item.platformID,
                                      startsAt: item.startsAt, durationMinutes: item.durationMinutes ?? 60,
                                      note: item.note ?? "", hasReminder: item.hasReminder ?? true)
            if let id { session.id = id }
            session.isDone = item.isDone ?? false
            session.createdAt = item.createdAt ?? .now
            context.insert(session)
            if let id { ids.insert(id) }
            slots.insert(slot)
            summary.sessions += 1
        }
        try context.save()
        return summary
    }

    private static func apply(_ item: GameEntry, to saved: SavedGame) {
        saved.key = item.key
        saved.sourceRaw = item.source
        saved.externalID = item.externalID
        saved.name = item.name
        saved.summary = item.summary ?? saved.summary
        saved.coverURLString = item.coverURL ?? saved.coverURLString
        saved.heroURLString = item.heroURL ?? saved.heroURLString
        if let date = item.releaseDate.flatMap(DateParsing.date) {
            saved.releaseDate = date
            saved.releaseDatePrecisionRaw = item.releaseDatePrecision.flatMap(DatePrecision.init(rawValue:))
                .flatMap { $0 == .day ? nil : $0.rawValue }
        }
        saved.year = item.year ?? saved.year
        if let genres = item.genres { saved.genresCSV = genres.joined(separator: ",") }
        if let platforms = item.platformIDs { saved.platformIDsCSV = platforms.joined(separator: ",") }
        saved.playingOn = item.playingOn ?? saved.playingOn
        if let status = item.status, BacklogStatus(rawValue: status) != nil { saved.statusRaw = status }
        saved.rating = min(5, max(0, item.rating ?? saved.rating))
        saved.notes = item.notes ?? saved.notes
        saved.hoursPlayed = max(0, item.hoursPlayed ?? saved.hoursPlayed)
        saved.progress = min(100, max(0, item.progress ?? saved.progress))
        saved.owned = item.owned ?? saved.owned
        saved.isFollowing = item.isFollowing ?? saved.isFollowing
        saved.steamAppID = item.steamAppID ?? saved.steamAppID
        saved.wikiTitle = item.wikiTitle ?? saved.wikiTitle
        saved.metacritic = item.metacritic ?? saved.metacritic
        saved.addedAt = item.addedAt ?? saved.addedAt
        saved.updatedAt = item.updatedAt ?? saved.updatedAt
        saved.finishedAt = item.finishedAt ?? saved.finishedAt
    }

    /// Android writes fractional seconds ("…:33.123Z"); Apple's default formatter rejects them.
    static func parseISO(_ string: String) -> Date? {
        let plain = ISO8601DateFormatter()
        if let date = plain.date(from: string) { return date }
        let fractional = ISO8601DateFormatter()
        fractional.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return fractional.date(from: string)
    }
}

/// Wraps backup data for `fileExporter`.
struct BackupDocument: FileDocument {
    static var readableContentTypes: [UTType] { [.json] }
    var data: Data

    init(data: Data) { self.data = data }

    init(configuration: ReadConfiguration) throws {
        data = configuration.file.regularFileContents ?? Data()
    }

    func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}
