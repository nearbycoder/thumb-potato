import SwiftUI
import SwiftData

struct BacklogView: View {
    @Environment(\.modelContext) private var context
    @Environment(Preferences.self) private var prefs
    @Environment(AppModel.self) private var app
    @Environment(\.layout) private var layout
    @Query(sort: \SavedGame.addedAt, order: .reverse) private var saved: [SavedGame]

    @State private var status: BacklogStatus = .queued
    @State private var sort: Sort = .recent
    @State private var platformFilter: String?
    @State private var genreFilter: Genre?
    @State private var searchText = ""

    enum Sort: String, CaseIterable, Identifiable {
        case recent = "Date Added", name = "Title", rating = "Rating", release = "Release Date"
        var id: String { rawValue }
    }

    private var filtered: [SavedGame] {
        var items = saved.filter { $0.status == status }
        if let platformFilter {
            items = items.filter { ($0.playingOn ?? "") == platformFilter || $0.platformIDs.contains(platformFilter) }
        }
        if let genreFilter { items = items.filter { $0.genres.contains(genreFilter.rawValue) } }
        if !searchText.isEmpty { items = items.filter { $0.name.localizedCaseInsensitiveContains(searchText) } }
        switch sort {
        case .recent: break
        case .name: items.sort { $0.name.localizedCompare($1.name) == .orderedAscending }
        case .rating: items.sort { $0.rating > $1.rating }
        case .release: items.sort { ($0.releaseDate ?? .distantPast) > ($1.releaseDate ?? .distantPast) }
        }
        return items
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 16) {
                BacklogStatsCard(stats: BacklogStats(games: saved)).padding(.horizontal, layout.margin)
                statusTabs.padding(.horizontal, layout.margin)
                filters

                if filtered.isEmpty {
                    emptyState
                } else {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: layout.gridMinWidth), spacing: layout.isCompact ? 12 : 16, alignment: .top)], spacing: 20) {
                        ForEach(filtered) { item in
                            NavigationLink(value: Route.game(item.ref, preview: item.asGame)) {
                                BacklogCard(item: item)
                            }
                            .buttonStyle(.plain)
                            .focusable(false)
                            .contextMenu { menu(for: item) }
                        }
                    }
                    .padding(.horizontal, layout.margin)
                    .focusEffectDisabled()
                }
            }
            .padding(.vertical)
        }
        .appBackground()
        .navigationTitle("Backlog")
        .searchable(text: $searchText, prompt: "Filter")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Menu {
                    Picker("Sort", selection: $sort) {
                        ForEach(Sort.allCases) { Text($0.rawValue).tag($0) }
                    }
                } label: {
                    Label("Sort", systemImage: "arrow.up.arrow.down")
                }
            }
        }
    }

    private var statusTabs: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(BacklogStatus.allCases) { s in
                    let count = saved.filter { $0.status == s }.count
                    let selected = status == s
                    Button {
                        withAnimation(.snappy) { status = s }
                    } label: {
                        HStack(spacing: 6) {
                            Circle().fill(s.color).frame(width: 7, height: 7)
                            Text(s.label)
                            Text("\(count)").foregroundStyle(selected ? Theme.bg.opacity(0.7) : Theme.textTertiary)
                        }
                        .font(.subheadline.weight(.medium))
                        .foregroundStyle(selected ? Theme.bg : Theme.textPrimary)
                        .padding(.horizontal, 14).padding(.vertical, 8)
                        .background(selected ? Theme.textPrimary : Theme.fill, in: Capsule())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(s.label)
                    .accessibilityValue("\(count)")
                    .accessibilityAddTraits(selected ? .isSelected : [])
                }
            }
            .padding(.vertical, 2)
        }
    }

    @ViewBuilder
    private var filters: some View {
        let platforms = Platform.all.filter { p in saved.contains { $0.platformIDs.contains(p.id) || $0.playingOn == p.id } }
        let genres = Genre.allCases.filter { g in saved.contains { $0.genres.contains(g.rawValue) } }
        if !platforms.isEmpty || !genres.isEmpty {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    if !genres.isEmpty {
                        Menu {
                            Picker("Genre", selection: $genreFilter) {
                                Text("Any Genre").tag(Genre?.none)
                                ForEach(genres) { Text($0.label).tag(Genre?.some($0)) }
                            }
                        } label: {
                            HStack(spacing: 5) {
                                Text(genreFilter?.label ?? "Any Genre").font(.caption.weight(.medium))
                                Image(systemName: "chevron.down").font(.caption2.weight(.bold)).opacity(0.7)
                            }
                            .padding(.horizontal, 10).frame(height: 30)
                            .foregroundStyle(genreFilter != nil ? Theme.onAccent : Theme.textPrimary)
                            .background(genreFilter != nil ? Theme.accent : Theme.fill, in: Capsule())
                        }
                        .menuStyle(.button)
                        .buttonStyle(.plain)
                        .menuIndicator(.hidden)
                        .fixedSize()
                        .accessibilityLabel("Genre")
                        .accessibilityValue(genreFilter?.label ?? "Any")
                    }
                    ForEach(platforms) { p in
                        Button {
                            platformFilter = platformFilter == p.id ? nil : p.id
                        } label: {
                            HStack(spacing: 6) {
                                PlatformTile(platform: p, size: 20)
                                Text(p.shortName).font(.caption.weight(.medium)).foregroundStyle(Theme.textPrimary)
                            }
                            .padding(.horizontal, 8).frame(height: 30)
                            .background(Theme.fill, in: Capsule())
                            .overlay(Capsule().strokeBorder(platformFilter == p.id ? Theme.accent : .clear, lineWidth: 1.5))
                            .opacity(platformFilter == nil || platformFilter == p.id ? 1 : 0.5)
                        }
                        .buttonStyle(.plain)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(p.name)
                        .accessibilityAddTraits(platformFilter == p.id ? .isSelected : [])
                    }
                }
                .padding(.horizontal, layout.margin)
            }
        }
    }

    @ViewBuilder
    private var emptyState: some View {
        if platformFilter != nil || genreFilter != nil || !searchText.isEmpty {
            EmptyState(symbol: "line.3.horizontal.decrease.circle", title: "No matches",
                       message: "Nothing in \(status.label) matches these filters.", actionTitle: "Clear Filters") {
                platformFilter = nil
                genreFilter = nil
                searchText = ""
            }
        } else {
            switch status {
            case .queued:
                EmptyState(symbol: "square.stack", title: "Your backlog is empty",
                           message: "Find something on Tonight or Browse and add it here.",
                           actionTitle: "Browse") { app.selection = .tab(.browse) }
            case .playing:
                EmptyState(symbol: "gamecontroller", title: "Nothing in progress",
                           message: "Start something from your backlog and track hours and progress here.")
            case .beaten:
                EmptyState(symbol: "trophy", title: "Nothing beaten yet",
                           message: "Mark games as beaten to build your history.")
            case .shelved:
                EmptyState(symbol: "archivebox", title: "Nothing shelved",
                           message: "Games you set aside will appear here.")
            }
        }
    }

    @ViewBuilder
    private func menu(for item: SavedGame) -> some View {
        ForEach(BacklogStatus.allCases) { s in
            Button { item.status = s } label: { Label(s.label, systemImage: s.symbol) }
        }
        Button { app.plan(item.asGame) } label: { Label("Plan a Session…", systemImage: "calendar.badge.plus") }
        Divider()
        Button("Remove", systemImage: "trash", role: .destructive) { Library.remove(item.key, in: context) }
    }
}

struct BacklogCard: View {
    var item: SavedGame

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Color.clear
                .aspectRatio(Theme.coverAspect, contentMode: .fit)
                .overlay { CoverArt(game: item.asGame, maxPixel: 500) }
                .overlay(alignment: .bottom) {
                    if item.status == .playing, item.progress > 0 {
                        // Progress along the bottom edge.
                        GeometryReader { geo in
                            ZStack(alignment: .leading) {
                                Rectangle().fill(.black.opacity(0.45))
                                Rectangle().fill(Theme.accent).frame(width: geo.size.width * CGFloat(item.progress) / 100)
                            }
                        }
                        .frame(height: 4)
                    }
                }
                .artwork()
                .overlay(alignment: .topLeading) {
                    if let p = item.playingOnPlatform ?? item.platforms.first {
                        PlatformTile(platform: p, size: 22).padding(6)
                    }
                }
                .overlay(alignment: .topTrailing) {
                    if item.isFollowing && item.isUpcoming {
                        Image(systemName: "bell.fill").font(.caption2).foregroundStyle(Theme.textPrimary)
                            .padding(6).background(.ultraThinMaterial, in: Circle()).padding(6)
                            .accessibilityLabel("Following")
                    } else if item.status == .beaten {
                        StatusMark(status: .beaten).padding(6)
                    }
                }
                .softShadow(radius: 8, y: 4)
            Text(item.name).font(.posterTitle).foregroundStyle(Theme.textPrimary).lineLimit(1)
            Group {
                if item.rating > 0 {
                    HStack(spacing: 2) {
                        ForEach(1...5, id: \.self) { i in
                            Image(systemName: i <= item.rating ? "star.fill" : "star")
                                .font(.system(size: 9))
                                .foregroundStyle(i <= item.rating ? Theme.accent : Theme.textTertiary)
                        }
                    }
                    .accessibilityElement()
                    .accessibilityLabel("\(item.rating) of 5 stars")
                } else if item.status == .playing, item.hoursPlayed > 0 || item.progress > 0 {
                    Text([item.hoursPlayed > 0 ? BacklogStats.hours(item.hoursPlayed) : nil,
                          item.progress > 0 ? "\(item.progress)%" : nil].compactMap { $0 }.joined(separator: " · "))
                        .font(.caption).foregroundStyle(Theme.accent)
                } else if item.isUpcoming, let date = item.releaseDate {
                    Text(ReleaseDateFormat.radarLabel(date, precision: item.releaseDatePrecision))
                        .font(.caption).foregroundStyle(Theme.info)
                } else {
                    Text([item.year.map(String.init), item.genres.first].compactMap { $0 }.joined(separator: " · "))
                        .font(.caption).foregroundStyle(Theme.textSecondary)
                }
            }
            .lineLimit(1)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

struct BacklogStatsCard: View {
    var stats: BacklogStats

    var body: some View {
        HStack(spacing: 0) {
            stat("\(stats.beatenThisYear)", "Beaten this year", color: Theme.success)
            divider
            stat(BacklogStats.hours(stats.hoursLogged), "Hours logged", color: Theme.accent)
            divider
            stat("\(stats.backlogSize)", "In backlog", color: Theme.info)
        }
        .card(padding: 14)
    }

    private var divider: some View {
        Rectangle().fill(Theme.hairline).frame(width: 1, height: 32)
    }

    private func stat(_ value: String, _ label: String, color: Color) -> some View {
        VStack(spacing: 2) {
            Text(value).font(.title3.weight(.bold)).foregroundStyle(color).monospacedDigit()
            Text(label).font(.caption).foregroundStyle(Theme.textSecondary).lineLimit(1).minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }
}
