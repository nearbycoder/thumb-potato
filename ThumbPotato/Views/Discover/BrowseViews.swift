import SwiftUI

/// Search plus browse by platform, mood and genre.
struct BrowseView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(Preferences.self) private var prefs
    @Environment(\.layout) private var layout
    @State private var query = ""
    @State private var results: [Game] = []
    @State private var isSearching = false

    private let suggestions = ["Zelda", "Hades", "Mario Kart", "Elden Ring", "Stardew Valley", "Hollow Knight", "Balatro", "Final Fantasy"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                if query.trimmingCharacters(in: .whitespaces).count < 2 {
                    idle
                } else if isSearching && results.isEmpty {
                    LoadingView()
                } else if results.isEmpty {
                    EmptyState(symbol: "magnifyingglass", title: "No results",
                               message: "Nothing matched “\(query)”. Check the spelling or try a shorter name.")
                } else {
                    VStack(alignment: .leading, spacing: 16) {
                        if isSearching {
                            HStack(spacing: 8) {
                                ProgressView().controlSize(.small)
                                Text("Searching Wikipedia and Steam…").font(.footnote).foregroundStyle(Theme.textSecondary)
                            }
                            .padding(.horizontal, layout.margin)
                        }
                        CoverGrid(games: results)
                    }
                }
            }
            .padding(.vertical)
        }
        .appBackground()
        .navigationTitle("Browse")
        .searchable(text: $query, placement: .automatic, prompt: "Games")
        .task(id: query) {
            let text = query
            guard text.trimmingCharacters(in: .whitespaces).count >= 2 else { results = []; isSearching = false; return }
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled else { return }
            isSearching = true
            if !catalog.hasRAWG {
                let local = await catalog.localSearch(text)
                guard !Task.isCancelled else { return }
                results = local
            }
            let found = (try? await catalog.search(text)) ?? []
            guard !Task.isCancelled else { return }
            if !found.isEmpty || catalog.hasRAWG { results = found }
            isSearching = false
        }
    }

    private var idle: some View {
        VStack(alignment: .leading, spacing: 28) {
            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Suggestions")
                FlowLayout(spacing: 8) {
                    ForEach(suggestions, id: \.self) { s in
                        Button { query = s } label: { Pill(text: s, systemImage: "magnifyingglass") }
                            .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, layout.margin)

            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Platforms", subtitle: prefs.myPlatformIDs.isEmpty ? nil : "Yours come first")
                // Wide enough minimum that "Nintendo Switch 2" never truncates; drops to one column first.
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 200), spacing: 10)], spacing: 10) {
                    ForEach(prefs.platformsMineFirst) { p in
                        NavigationLink(value: Route.platform(p)) {
                            PlatformChip(platform: p, isSelected: prefs.isMine(p), showsCheck: false)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, layout.margin)

            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Moods")
                LazyVGrid(columns: [GridItem(.adaptive(minimum: layout.isCompact ? 150 : 240), spacing: 12)], spacing: 12) {
                    ForEach(Mood.allCases) { mood in
                        NavigationLink(value: Route.mood(mood)) { MoodTile(mood: mood) }
                            .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, layout.margin)

            VStack(alignment: .leading, spacing: 12) {
                SectionHeader(title: "Genres")
                LazyVGrid(columns: [GridItem(.adaptive(minimum: layout.isCompact ? 150 : 180), spacing: 10)], spacing: 10) {
                    ForEach(Genre.allCases) { genre in
                        NavigationLink(value: Route.genre(genre)) { GenreChip(genre: genre) }
                            .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, layout.margin)

            Text(catalog.hasRAWG
                 ? "Search is powered by RAWG."
                 : "Search covers the Thumb Potato catalog, every game on Wikipedia and the Steam store. Add a free RAWG key in Settings for server-side browsing.")
                .font(.caption)
                .foregroundStyle(Theme.textTertiary)
                .padding(.horizontal, layout.margin)
        }
        .focusEffectDisabled()
    }
}

struct GenreChip: View {
    var genre: Genre

    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: genre.symbol)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Theme.accent)
                .frame(width: 22)
            Text(genre.label)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Theme.textPrimary)
                .lineLimit(1)
            Spacer(minLength: 0)
            Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(Theme.textTertiary)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 12)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(Theme.hairline))
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(genre.label)
    }
}

struct MoodView: View {
    var mood: Mood
    @Environment(Catalog.self) private var catalog
    @Environment(Preferences.self) private var prefs
    @Environment(\.layout) private var layout
    @State private var filter = GameFilter()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                FilterBar(filter: $filter, showsGenre: false).padding(.horizontal, layout.margin)

                PagedGrid(id: "mood-\(mood.id)-\(filter.key)-\(prefs.myPlatformIDs.sorted())-\(catalog.generation)",
                          load: { try await catalog.mood(mood, filter: filter, page: $0) }) {
                    if filter.isDefault {
                        EmptyState(symbol: "rectangle.on.rectangle.slash", title: "Nothing here yet",
                                   message: "Try another mood, or add more platforms in Settings.")
                    } else {
                        FilterEmptyState { filter = GameFilter() }
                    }
                }
            }
            .padding(.vertical)
        }
        .appBackground()
        .navigationTitle(mood.title)
        .navigationSubtitle(mood.blurb)
        .largeTitle()
    }
}

struct GenreView: View {
    var genre: Genre
    @Environment(Catalog.self) private var catalog
    @Environment(Preferences.self) private var prefs
    @Environment(\.layout) private var layout
    @State private var filter = GameFilter()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                FilterBar(filter: $filter, showsGenre: false).padding(.horizontal, layout.margin)

                PagedGrid(id: "genre-\(genre.id)-\(filter.key)-\(prefs.myPlatformIDs.sorted())-\(catalog.hasRAWG)-\(catalog.generation)",
                          load: { try await catalog.genre(genre, filter: filter, page: $0) }) {
                    if filter.isDefault {
                        EmptyState(symbol: genre.symbol, title: "Nothing here yet",
                                   message: "No \(genre.label.lowercased()) games on your platforms yet.")
                    } else {
                        FilterEmptyState { filter = GameFilter() }
                    }
                }
            }
            .padding(.vertical)
        }
        .appBackground()
        .navigationTitle(genre.label)
        .navigationSubtitle(prefs.myPlatformIDs.isEmpty ? "All platforms" : "On your platforms")
        .largeTitle()
    }
}

struct PlatformView: View {
    var platform: Platform
    @Environment(Catalog.self) private var catalog
    @Environment(Preferences.self) private var prefs
    @Environment(\.layout) private var layout
    @State private var filter = GameFilter()

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                FilterBar(filter: $filter).padding(.horizontal, layout.margin)

                PagedGrid(id: "platform-\(platform.id)-\(filter.key)-\(catalog.hasRAWG)-\(catalog.generation)",
                          load: { try await catalog.platform(platform, filter: filter, page: $0) }) {
                    if filter.isDefault {
                        EmptyState(symbol: "gamecontroller", title: "Nothing to show",
                                   message: "No games found for \(platform.name).")
                    } else {
                        FilterEmptyState { filter = GameFilter() }
                    }
                }
            }
            .padding(.vertical)
        }
        .appBackground()
        .navigationTitle(platform.name)
        .navigationSubtitle(prefs.isMine(platform) ? "One of your platforms" : "")
        .largeTitle()
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    withAnimation(.snappy) { prefs.toggle(platform) }
                    catalog.reset()
                } label: {
                    Label(prefs.isMine(platform) ? "Remove from My Platforms" : "Add to My Platforms",
                          systemImage: prefs.isMine(platform) ? "checkmark.circle.fill" : "plus.circle")
                }
                .help(prefs.isMine(platform) ? "Remove from My Platforms" : "Add to My Platforms")
            }
        }
    }
}

struct GameListView: View {
    var title: String
    var games: [Game]

    @Environment(\.layout) private var layout
    @State private var filter = GameFilter()

    var body: some View {
        let shown = filter.isDefault ? games : filter.apply(games)
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                FilterBar(filter: $filter).padding(.horizontal, layout.margin)
                if shown.isEmpty {
                    FilterEmptyState { filter = GameFilter() }
                } else {
                    CoverGrid(games: shown)
                }
            }
            .padding(.vertical)
        }
        .appBackground()
        .navigationTitle(title)
        .inlineTitle()
    }
}

/// Sort, release, genre and critic-score menus for browse pages. Wraps on narrow screens.
struct FilterBar: View {
    @Binding var filter: GameFilter
    var showsGenre = true

    var body: some View {
        FlowLayout(spacing: 8) {
            menu("Sort", value: filter.sort.label, systemImage: "arrow.up.arrow.down", active: filter.sort != .popular) {
                Picker("Sort", selection: $filter.sort) {
                    ForEach(GameFilter.Sort.allCases) { Text($0.label).tag($0) }
                }
            }
            menu("Released", value: filter.released.label, systemImage: "calendar", active: filter.released != .any) {
                Picker("Released", selection: $filter.released) {
                    ForEach(GameFilter.Released.allCases) { Text($0.label).tag($0) }
                }
            }
            if showsGenre {
                menu("Genre", value: filter.genre?.label ?? "Any Genre", systemImage: "square.grid.2x2", active: filter.genre != nil) {
                    Picker("Genre", selection: $filter.genre) {
                        Text("Any Genre").tag(Genre?.none)
                        ForEach(Genre.allCases) { Text($0.label).tag(Genre?.some($0)) }
                    }
                }
            }
            menu("Critic Score", value: filter.minScore.map { "Metacritic \($0)+" } ?? "Any Score", systemImage: "star",
                 active: filter.minScore != nil) {
                Picker("Critic Score", selection: $filter.minScore) {
                    Text("Any Score").tag(Int?.none)
                    ForEach(GameFilter.scoreSteps, id: \.self) { Text("\($0)+").tag(Int?.some($0)) }
                }
            }
            if !filter.isDefault {
                Button("Reset") { withAnimation(.snappy) { filter = GameFilter() } }
                    .font(.subheadline.weight(.medium))
                    .foregroundStyle(Theme.accent)
                    .buttonStyle(.plain)
                    .frame(height: 32)
            }
        }
        .animation(.snappy, value: filter)
    }

    private func menu<Content: View>(_ title: String, value: String, systemImage: String, active: Bool,
                                     @ViewBuilder content: () -> Content) -> some View {
        Menu {
            content()
        } label: {
            HStack(spacing: 5) {
                Image(systemName: systemImage).font(.caption.weight(.semibold))
                Text(value).font(.subheadline.weight(.medium)).lineLimit(1)
                Image(systemName: "chevron.down").font(.caption2.weight(.bold)).opacity(0.7)
            }
            .padding(.horizontal, 12)
            .frame(height: 32)
            .foregroundStyle(active ? Theme.onAccent : Theme.textPrimary)
            .background(active ? Theme.accent : Theme.fill, in: Capsule())
            .contentShape(Capsule())
        }
        .menuStyle(.button)
        .buttonStyle(.plain)
        .menuIndicator(.hidden)
        .fixedSize()
        .accessibilityLabel(title)
        .accessibilityValue(value)
    }
}

struct FilterEmptyState: View {
    var reset: () -> Void

    var body: some View {
        EmptyState(symbol: "line.3.horizontal.decrease.circle", title: "No matches",
                   message: "No games match these filters.", actionTitle: "Reset Filters", action: reset)
    }
}

/// Shared destinations for every navigation stack.
struct RouteDestinations: ViewModifier {
    func body(content: Content) -> some View {
        content.navigationDestination(for: Route.self) { route in
            switch route {
            case .game(let ref, let preview): GameDetailView(ref: ref, preview: preview)
            case .mood(let mood): MoodView(mood: mood)
            case .genre(let genre): GenreView(genre: genre)
            case .platform(let platform): PlatformView(platform: platform)
            case .list(let title, let games): GameListView(title: title, games: games)
            }
        }
    }
}

extension View {
    func withRoutes() -> some View { modifier(RouteDestinations()) }
}
