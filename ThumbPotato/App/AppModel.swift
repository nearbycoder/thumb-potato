import SwiftUI
import Observation

enum AppTab: String, Hashable, CaseIterable {
    case tonight, browse, backlog, planner, shuffle, settings

    var title: String {
        switch self {
        case .tonight: "Tonight"
        case .browse: "Browse"
        case .backlog: "Backlog"
        case .planner: "Planner"
        case .shuffle: "Shuffle"
        case .settings: "Settings"
        }
    }

    var symbol: String {
        switch self {
        case .tonight: "sparkles.tv"
        case .browse: "magnifyingglass"
        case .backlog: "square.stack"
        case .planner: "calendar"
        case .shuffle: "shuffle"
        case .settings: "gearshape"
        }
    }
}

/// Navigation destinations shared by every tab's stack.
enum Route: Hashable {
    case game(GameRef, preview: Game?)
    case mood(Mood)
    case genre(Genre)
    case platform(Platform)
    case list(String, [Game])

    static func detail(_ game: Game) -> Route { .game(game.ref, preview: game) }
}

/// Selected tab, per-tab navigation paths and cross-tab requests (deep links,
/// "plan a session for this game", Handoff).
@MainActor
@Observable
final class AppModel {
    var selection: TabSelection = .tab(.tonight)
    var paths: [TabSelection: NavigationPath] = [:]
    var planningGame: Game?
    var isPlanning = false
    var showOnboarding = false
    var showSettings = false

    static let gameActivity = "com.nearbycoder.thumbpotato.game"

    func path(_ tab: TabSelection) -> Binding<NavigationPath> {
        Binding(get: { self.paths[tab] ?? NavigationPath() }, set: { self.paths[tab] = $0 })
    }

    func push(_ route: Route) {
        if selection == .tab(.settings) { selection = .tab(.tonight) }
        paths[selection, default: NavigationPath()].append(route)
    }

    func open(_ ref: GameRef) {
        push(.game(ref, preview: nil))
    }

    func handle(url: URL) {
        guard url.scheme == "thumbpotato" else { return }
        switch url.host {
        case "game":
            if let ref = GameRef(key: url.lastPathComponent) { open(ref) }
        case "shuffle": selection = .tab(.shuffle)
        case "planner": selection = .tab(.planner)
        case "backlog": selection = .tab(.backlog)
        case "platform":
            if let platform = Platform.with(id: url.lastPathComponent) {
                selection = .tab(.browse)
                // After the tab switch lands; the macOS sidebar resets the stack while swapping roots.
                DispatchQueue.main.async { self.paths[.tab(.browse)] = NavigationPath([Route.platform(platform)]) }
            }
        default: break
        }
    }

    func plan(_ game: Game?) {
        planningGame = game
        isPlanning = true
    }
}

enum TabSelection: Hashable {
    case tab(AppTab)
    case platform(String)
}
