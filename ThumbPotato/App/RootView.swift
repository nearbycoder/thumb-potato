import SwiftUI
import SwiftData

struct RootView: View {
    @Environment(AppModel.self) private var app
    @Environment(Preferences.self) private var prefs
    @Environment(SyncMonitor.self) private var sync
    @Environment(\.modelContext) private var context
    @Environment(\.horizontalSizeClass) private var sizeClass
    @Query(filter: #Predicate<SavedGame> { $0.statusRaw == "playing" }) private var playing: [SavedGame]
    @Query(filter: #Predicate<SavedGame> { $0.isFollowing }) private var followed: [SavedGame]

    var body: some View {
        @Bindable var app = app
        TabView(selection: $app.selection) {
            Tab(AppTab.tonight.title, systemImage: AppTab.tonight.symbol, value: TabSelection.tab(.tonight)) {
                stack(.tab(.tonight)) { TonightView() }
            }
            Tab(AppTab.backlog.title, systemImage: AppTab.backlog.symbol, value: TabSelection.tab(.backlog)) {
                stack(.tab(.backlog)) { BacklogView() }
            }
            .badge(playing.count)
            Tab(AppTab.planner.title, systemImage: AppTab.planner.symbol, value: TabSelection.tab(.planner)) {
                stack(.tab(.planner)) { PlannerView() }
            }
            Tab(AppTab.shuffle.title, systemImage: AppTab.shuffle.symbol, value: TabSelection.tab(.shuffle)) {
                stack(.tab(.shuffle)) { ShuffleView() }
            }
            Tab(AppTab.browse.title, systemImage: AppTab.browse.symbol, value: TabSelection.tab(.browse), role: .search) {
                stack(.tab(.browse)) { BrowseView() }
            }

            // Sidebar-only entries. Phones get Settings from the toolbar instead, and
            // platforms from Browse, so their tab bar stays at five items (no "More").
            if Self.hasSidebar {
                #if os(iOS)
                Tab(AppTab.settings.title, systemImage: AppTab.settings.symbol, value: TabSelection.tab(.settings)) {
                    stack(.tab(.settings)) { SettingsView() }
                }
                .defaultVisibility(.hidden, for: .tabBar)
                #endif

                TabSection("Platforms") {
                    ForEach(sidebarPlatforms) { platform in
                        Tab(platform.shortName, systemImage: "gamecontroller", value: TabSelection.platform(platform.id)) {
                            stack(.platform(platform.id)) { PlatformView(platform: platform) }
                        }
                    }
                }
                .defaultVisibility(.hidden, for: .tabBar)
            }
        }
        .modifier(AdaptiveTabStyle())
        #if os(iOS)
        .tabBarMinimizeBehavior(.onScrollDown)
        #endif
        .tint(Theme.accent)
        .environment(\.layout, LayoutMetrics(isCompact: sizeClass == .compact))
        .preferredColorScheme(prefs.appearance.colorScheme)
        .sheet(isPresented: $app.isPlanning) {
            PlanSessionSheet(initialGame: app.planningGame)
        }
        .sheet(isPresented: $app.showOnboarding) {
            OnboardingView()
        }
        #if os(iOS)
        .sheet(isPresented: $app.showSettings) {
            NavigationStack {
                SettingsView()
                    .withRoutes()
                    .toolbar {
                        ToolbarItem(placement: .confirmationAction) { Button("Done") { app.showSettings = false } }
                    }
            }
        }
        #endif
        .onOpenURL { app.handle(url: $0) }
        #if os(macOS)
        // Deep links and Handoff go to the open window instead of spawning a new one.
        .handlesExternalEvents(preferring: ["*"], allowing: ["*"])
        #endif
        .onContinueUserActivity(AppModel.gameActivity) { activity in
            if let key = activity.userInfo?["key"] as? String, let ref = GameRef(key: key) { app.open(ref) }
        }
        .onAppear {
            Library.dedupe(in: context)
            if !prefs.hasOnboarded { app.showOnboarding = true }
            clearKeyboardFocus()
        }
        .onChange(of: app.selection) { clearKeyboardFocus() }
        // Another device's edits just arrived: fold any games both devices added.
        .onChange(of: sync.lastImport) { Library.dedupe(in: context) }
        // Keep release-day alerts in step with what's followed (and its dates).
        .task(id: releaseAlertSignature) {
            await Reminders.syncReleaseAlerts(followed, enabled: prefs.releaseAlerts)
        }
    }

    private var releaseAlertSignature: String {
        let games = followed.map { "\($0.key)@\($0.releaseDate?.timeIntervalSince1970 ?? 0)\($0.releaseDatePrecisionRaw ?? "")" }
        return "\(prefs.releaseAlerts)|" + games.sorted().joined(separator: ",")
    }

    /// AppKit hands keyboard focus to the first cover when a window opens, which
    /// draws a focus outline around it. Covers don't need keyboard focus.
    private func clearKeyboardFocus() {
        #if os(macOS)
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
            NSApp.keyWindow?.makeFirstResponder(nil)
        }
        #endif
    }

    static var hasSidebar: Bool {
        #if os(iOS)
        UIDevice.current.userInterfaceIdiom == .pad
        #else
        true
        #endif
    }

    private var sidebarPlatforms: [Platform] {
        let mine = prefs.myPlatforms
        return mine.isEmpty ? Platform.all : mine
    }

    private func stack<Content: View>(_ tab: TabSelection, @ViewBuilder content: () -> Content) -> some View {
        TabStack(tab: tab, content: content())
    }
}

/// One tab's navigation stack. It reads its path in its own body so pushes from
/// outside the stack (deep links, Handoff, Shuffle) redraw it; tab content is built
/// lazily, outside RootView's observation.
private struct TabStack<Content: View>: View {
    var tab: TabSelection
    var content: Content
    @Environment(AppModel.self) private var app

    var body: some View {
        let key = pathKey
        let path = app.paths[key] ?? NavigationPath()
        NavigationStack(path: Binding(get: { path }, set: { app.paths[key] = $0 })) {
            content.withRoutes()
        }
    }

    /// The macOS sidebar shows every tab through the first tab's stack and only swaps
    /// its root view, so there the visible stack follows the selected tab's path.
    private var pathKey: TabSelection {
        #if os(macOS)
        app.selection
        #else
        tab
        #endif
    }
}

/// A sidebar on iPad and Mac; the standard tab bar on phone-class devices
/// (the iPhone Duo shows its side-mounted tab bar in both poses).
private struct AdaptiveTabStyle: ViewModifier {
    func body(content: Content) -> some View {
        if RootView.hasSidebar {
            content.tabViewStyle(.sidebarAdaptable)
        } else {
            content.tabViewStyle(.tabBarOnly)
        }
    }
}
