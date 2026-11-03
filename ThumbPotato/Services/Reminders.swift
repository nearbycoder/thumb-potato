import Foundation
import UserNotifications
import EventKit

/// Local notifications for play sessions and release days of followed games.
@MainActor
enum Reminders {
    private static var center: UNUserNotificationCenter { .current() }
    nonisolated static let releaseHour = 9

    @discardableResult
    static func requestPermission() async -> Bool {
        (try? await center.requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    static func schedule(_ session: PlaySession, minutesBefore: Int) async {
        cancel(session)
        guard session.hasReminder, !session.isDone else { return }
        let fireDate = session.startsAt.addingTimeInterval(TimeInterval(-minutesBefore * 60))
        guard fireDate > .now, await requestPermission() else { return }
        let content = UNMutableNotificationContent()
        content.title = "Time to play"
        content.body = minutesBefore > 0
            ? "\(session.gameName) starts in \(minutesBefore) minutes."
            : "\(session.gameName) is starting now."
        content.sound = .default
        content.userInfo = ["gameKey": session.gameKey]
        let trigger = UNCalendarNotificationTrigger(
            dateMatching: Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fireDate), repeats: false)
        try? await center.add(UNNotificationRequest(identifier: "session-\(session.id.uuidString)", content: content, trigger: trigger))
    }

    static func cancel(_ session: PlaySession) {
        center.removePendingNotificationRequests(withIdentifiers: ["session-\(session.id.uuidString)"])
    }

    /// When a release-day alert fires: 9 AM local on the release date. Nil for coarse
    /// ("2027", "March 2027") or past dates.
    nonisolated static func releaseFireDate(_ date: Date?, precision: DatePrecision, now: Date = .now) -> Date? {
        guard let date, precision == .day else { return nil }
        let fire = Calendar.current.date(bySettingHour: releaseHour, minute: 0, second: 0, of: date) ?? date
        return fire > now ? fire : nil
    }

    /// Makes pending release-day alerts match the followed games (adds, moves, removes).
    static func syncReleaseAlerts(_ games: [SavedGame], enabled: Bool) async {
        let pending = await center.pendingNotificationRequests().map(\.identifier).filter { $0.hasPrefix("release-") }
        var wanted: [String: (SavedGame, Date)] = [:]
        if enabled {
            for game in games where game.wantsReleaseAlert {
                if let fire = releaseFireDate(game.releaseDate, precision: game.releaseDatePrecision) {
                    wanted["release-\(game.key)"] = (game, fire)
                }
            }
        }
        let stale = pending.filter { wanted[$0] == nil }
        center.removePendingNotificationRequests(withIdentifiers: stale)
        guard !wanted.isEmpty, await requestPermission() else { return }
        for (id, (game, fire)) in wanted {
            let content = UNMutableNotificationContent()
            content.title = "Out today: \(game.name)"
            content.body = "\(game.name) is released today."
            content.sound = .default
            content.userInfo = ["gameKey": game.key]
            let trigger = UNCalendarNotificationTrigger(
                dateMatching: Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: fire), repeats: false)
            // Adding with the same identifier replaces a pending alert, so a moved date moves it.
            try? await center.add(UNNotificationRequest(identifier: id, content: content, trigger: trigger))
        }
    }

    static func cancelReleaseAlert(key: String) {
        center.removePendingNotificationRequests(withIdentifiers: ["release-\(key)"])
    }
}

/// Adds play sessions to the system calendar (write-only access).
@MainActor
enum CalendarBridge {
    private static let store = EKEventStore()

    static func add(_ session: PlaySession) async -> String? {
        guard (try? await store.requestWriteOnlyAccessToEvents()) == true else { return nil }
        let event = EKEvent(eventStore: store)
        event.title = session.gameName
        event.startDate = session.startsAt
        event.endDate = session.endsAt
        let platform = session.platform.map { "On \($0.name)" }
        event.notes = [platform, session.note.isEmpty ? nil : session.note, "Planned with Thumb Potato"]
            .compactMap { $0 }.joined(separator: "\n\n")
        event.url = GameRef(key: session.gameKey)?.deepLink
        event.calendar = store.defaultCalendarForNewEvents
        do {
            try store.save(event, span: .thisEvent)
            return event.eventIdentifier
        } catch {
            return nil
        }
    }
}
