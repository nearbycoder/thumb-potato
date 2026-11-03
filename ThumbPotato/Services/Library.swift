import Foundation
import SwiftData

/// Helpers for reading and writing the synced backlog.
@MainActor
enum Library {
    static func saved(_ key: String, in context: ModelContext) -> SavedGame? {
        var descriptor = FetchDescriptor<SavedGame>(predicate: #Predicate { $0.key == key })
        descriptor.fetchLimit = 1
        return try? context.fetch(descriptor).first
    }

    @discardableResult
    static func save(_ game: Game, status: BacklogStatus = .queued, in context: ModelContext) -> SavedGame {
        if let existing = saved(game.ref.key, in: context) {
            existing.update(from: game)
            existing.status = status
            return existing
        }
        let saved = SavedGame(from: game, status: status)
        context.insert(saved)
        return saved
    }

    static func remove(_ key: String, in context: ModelContext) {
        let descriptor = FetchDescriptor<SavedGame>(predicate: #Predicate { $0.key == key })
        for item in (try? context.fetch(descriptor)) ?? [] {
            Reminders.cancelReleaseAlert(key: item.key)
            context.delete(item)
        }
    }

    /// CloudKit can't enforce uniqueness, so two devices adding the same game
    /// offline produce twins. Keep the most recently updated one, folding in the rest.
    static func dedupe(in context: ModelContext) {
        guard let all = try? context.fetch(FetchDescriptor<SavedGame>()) else { return }
        for (_, twins) in Dictionary(grouping: all, by: \.key) where twins.count > 1 {
            let sorted = twins.sorted { $0.updatedAt > $1.updatedAt }
            let keeper = sorted[0]
            for twin in sorted.dropFirst() {
                merge(twin, into: keeper)
                context.delete(twin)
            }
        }
        try? context.save()
    }

    static func merge(_ twin: SavedGame, into keeper: SavedGame) {
        keeper.rating = max(keeper.rating, twin.rating)
        if keeper.notes.isEmpty { keeper.notes = twin.notes }
        keeper.hoursPlayed = max(keeper.hoursPlayed, twin.hoursPlayed)
        keeper.progress = max(keeper.progress, twin.progress)
        keeper.owned = keeper.owned || twin.owned
        keeper.playingOn = keeper.playingOn ?? twin.playingOn
        keeper.addedAt = min(keeper.addedAt, twin.addedAt)
        keeper.finishedAt = keeper.finishedAt ?? twin.finishedAt
    }
}

/// Backlog numbers for the stats card.
struct BacklogStats {
    let beatenThisYear: Int
    let hoursLogged: Double
    let backlogSize: Int
    let playing: Int

    init(games: [SavedGame], now: Date = .now) {
        let cal = Calendar.current
        let year = cal.component(.year, from: now)
        beatenThisYear = games.filter {
            $0.status == .beaten && cal.component(.year, from: $0.finishedAt ?? $0.updatedAt) == year
        }.count
        hoursLogged = games.reduce(0) { $0 + $1.hoursPlayed }
        backlogSize = games.filter { $0.status == .queued }.count
        playing = games.filter { $0.status == .playing }.count
    }

    static func hours(_ value: Double) -> String {
        value >= 100 || value == value.rounded() ? "\(Int(value.rounded()))h" : String(format: "%.1fh", value)
    }
}
