import SwiftUI
import SwiftData

/// The home feed.
struct TonightView: View {
    @Environment(Catalog.self) private var catalog
    @Environment(Preferences.self) private var prefs
    @Environment(AppModel.self) private var app
    @Environment(\.layout) private var layout
    @Query(sort: \SavedGame.updatedAt, order: .reverse) private var saved: [SavedGame]

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading, spacing: 32) {
                Loadable(id: "hero-\(feedKey)", load: { try await catalog.hero() }) { games in
                    HeroCarousel(games: games)
                }

                let playing = saved.filter { $0.status == .playing }.prefix(15).map(\.asGame)
                if !playing.isEmpty {
                    CoverRow(title: "Continue Playing", subtitle: "From your backlog", games: Array(playing), seeAll: nil)
                }

                AsyncRow(id: "new", title: "New Releases", subtitle: "Out in the last few months") {
                    try await catalog.newReleases()
                }

                AsyncRow(id: "soon", title: "Coming Soon", subtitle: "Add one to get a release-day alert") {
                    try await catalog.comingSoon()
                }

                MoodsRow()

                ForEach(featuredPlatforms) { platform in
                    AsyncRow(id: "top-\(platform.id)", title: "Top on \(platform.shortName)",
                             subtitle: prefs.isMine(platform) ? "One of your platforms" : nil,
                             seeAll: .platform(platform)) {
                        try await catalog.top(platform)
                    }
                }

                if prefs.myPlatformIDs.isEmpty || prefs.isMine(.pc) || prefs.isMine(.mac) {
                    AsyncRow(id: "steam-top", title: "Top Sellers on Steam") { try await catalog.steamTopSellers() }
                }

                AsyncRow(id: "deals", title: "Deals", subtitle: "Well-reviewed PC games on sale") { try await catalog.deals() }

                AttributionFooter()
            }
            .padding(.bottom, 24)
        }
        .appBackground()
        .navigationTitle("Tonight")
        .navigationSubtitle(Date.now.formatted(.dateTime.weekday(.wide).month(.wide).day()))
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    app.selection = .tab(.shuffle)
                } label: {
                    Label("Shuffle", systemImage: "shuffle")
                }
            }
            #if os(iOS)
            // Phones (including the iPhone Duo in both poses) have no Settings tab, so it lives here.
            if !RootView.hasSidebar {
                ToolbarItem(placement: .primaryAction) {
                    Button {
                        app.showSettings = true
                    } label: {
                        Label("Settings", systemImage: "gearshape")
                    }
                }
            }
            #endif
        }
        .refreshable { catalog.reset() }
    }

    private var feedKey: String { "\(prefs.myPlatformIDs.sorted())-\(catalog.generation)" }

    private var featuredPlatforms: [Platform] {
        let mine = prefs.myPlatforms
        return mine.isEmpty ? [.nswitch, .ps5, .xboxSeries, .pc] : mine
    }
}

struct AsyncRow: View {
    var id: String
    var title: String
    var subtitle: String?
    var seeAll: Route?
    var load: () async throws -> [Game]

    @Environment(Preferences.self) private var prefs
    @Environment(Catalog.self) private var catalog
    @Environment(\.layout) private var layout
    @State private var games: [Game] = []
    @State private var failed = false

    var body: some View {
        Group {
            if !games.isEmpty {
                CoverRow(title: title, subtitle: subtitle, games: games, seeAll: seeAll ?? .list(title, games))
            } else if !failed {
                VStack(alignment: .leading, spacing: 12) {
                    SectionHeader(title: title, subtitle: subtitle).padding(.horizontal, layout.margin)
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 14) {
                            ForEach(0..<8, id: \.self) { _ in
                                RoundedRectangle(cornerRadius: Theme.coverRadius, style: .continuous)
                                    .fill(Theme.surfaceElevated)
                                    .frame(width: layout.coverWidth, height: layout.coverWidth / Theme.coverAspect)
                            }
                        }
                        .padding(.horizontal, layout.margin)
                    }
                    .scrollDisabled(true)
                    .accessibilityHidden(true)
                }
            }
        }
        .task(id: "\(id)-\(prefs.region)-\(catalog.hasRAWG)-\(prefs.myPlatformIDs.sorted())-\(catalog.generation)") {
            do {
                games = try await load()
                failed = games.isEmpty
            } catch is CancellationError {
            } catch {
                failed = games.isEmpty
            }
        }
    }
}

/// Paging hero carousel with a page indicator.
struct HeroCarousel: View {
    var games: [Game]

    @Environment(\.layout) private var layout
    @State private var current: String?

    var body: some View {
        VStack(spacing: 10) {
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 16) {
                    ForEach(games) { game in
                        HeroCard(game: game)
                            .containerRelativeFrame(.horizontal) { length, _ in cardWidth(in: length) }
                            .id(game.id)
                    }
                }
                .scrollTargetLayout()
            }
            .contentMargins(.horizontal, layout.margin, for: .scrollContent)
            .scrollTargetBehavior(.viewAligned)
            .scrollPosition(id: $current)

            HStack(spacing: 6) {
                ForEach(games) { game in
                    Capsule()
                        .fill(game.id == (current ?? games.first?.id) ? Theme.textPrimary : Theme.textTertiary.opacity(0.5))
                        .frame(width: game.id == (current ?? games.first?.id) ? 18 : 6, height: 6)
                        .animation(.easeOut(duration: 0.25), value: current)
                }
            }
            .accessibilityHidden(true)
        }
    }

    /// One card per page on phones; on wider screens cards stay at least ~760 pt wide
    /// (so text and artwork never crowd) and a single card leaves the next one peeking in.
    private func cardWidth(in length: CGFloat) -> CGFloat {
        if layout.isCompact { return length }
        let pages = max(1, Int((length + 16) / 776))
        if pages == 1 { return length - 64 }
        return (length - 16 * CGFloat(pages - 1)) / CGFloat(pages)
    }
}

struct HeroCard: View {
    var game: Game

    @Environment(\.layout) private var layout
    @Environment(\.modelContext) private var context
    @Environment(AppModel.self) private var app
    @Query private var saved: [SavedGame]

    private var isSaved: Bool { saved.contains { $0.key == game.ref.key } }
    /// Wide layout without a backdrop: the cover sits in its own column beside the text,
    /// so text never lands on box art.
    private var coverMode: Bool { !layout.isCompact && game.heroURL == nil }
    private var coverHeight: CGFloat { layout.heroHeight - 64 }

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            // The artwork is an overlay so a wide backdrop can't stretch the layout.
            Color.clear
                .frame(height: layout.heroHeight)
                .frame(maxWidth: .infinity)
                .overlay { heroImage }
                .clipped()
                .contentShape(Rectangle())
                .onTapGesture { app.push(.detail(game)) }

            if !coverMode {
                LinearGradient(stops: [
                    .init(color: .clear, location: 0.35),
                    .init(color: .black.opacity(0.55), location: 0.68),
                    .init(color: .black.opacity(0.92), location: 1),
                ], startPoint: .top, endPoint: .bottom)
                .allowsHitTesting(false)
            }

            VStack(alignment: .leading, spacing: 10) {
                Text(game.name)
                    .font(layout.isCompact ? .title.bold() : .largeTitle.bold())
                    .foregroundStyle(.white)
                    .lineLimit(2)
                    .minimumScaleFactor(0.8)
                Text(game.metaLine)
                    .font(.footnote.weight(.medium))
                    .foregroundStyle(.white.opacity(0.78))
                    .lineLimit(1)
                PlatformMonogramRow(platforms: game.platformList, size: 24)
                    .environment(\.colorScheme, .dark)

                if coverMode, !game.summary.isEmpty {
                    Text(game.summary)
                        .font(.subheadline)
                        .foregroundStyle(.white.opacity(0.8))
                        .lineLimit(4)
                        .frame(maxWidth: 440, alignment: .leading)
                }

                HStack(spacing: 10) {
                    Button {
                        if !isSaved { Library.save(game, status: .queued, in: context) }
                    } label: {
                        Label(isSaved ? "In Backlog" : "Add to Backlog", systemImage: isSaved ? "checkmark" : "plus")
                    }
                    .buttonStyle(.primary)
                    .disabled(isSaved)
                    Button {
                        app.push(.detail(game))
                    } label: {
                        Text("Details")
                    }
                    .buttonStyle(.secondary)
                    .environment(\.colorScheme, .dark)
                }
                .lineLimit(1)
                .fixedSize()
                .padding(.top, 4)
            }
            .padding(layout.isCompact ? 18 : 28)
            // Keep the text clear of the cover column.
            .padding(.trailing, coverMode ? coverHeight * Theme.coverAspect + 40 : 0)
        }
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 20, style: .continuous).strokeBorder(Theme.hairline))
        .softShadow(radius: 20, y: 10)
        .gameContextMenu(game)
    }

    @ViewBuilder
    private var heroImage: some View {
        if let hero = game.heroURL, !layout.isCompact {
            RemoteImage(url: hero, maxPixel: 1600, placeholderTitle: game.name)
        } else if layout.isCompact {
            RemoteImage(url: game.boxArtURL ?? game.heroURL, maxPixel: 900, placeholderTitle: game.name)
        } else {
            // Cover-only games on wide layouts: a dark wash tinted by the art, the cover at the trailing edge.
            ZStack(alignment: .trailing) {
                RemoteImage(url: game.boxArtURL, maxPixel: 300, placeholderTitle: nil)
                    .blur(radius: 48)
                    .scaleEffect(1.5)
                LinearGradient(colors: [.black.opacity(0.85), .black.opacity(0.6), .black.opacity(0.4)],
                               startPoint: .leading, endPoint: .trailing)
                RemoteImage(url: game.boxArtURL, maxPixel: 700, placeholderTitle: game.name)
                    .frame(width: coverHeight * Theme.coverAspect, height: coverHeight)
                    .artwork(radius: 14)
                    .softShadow(radius: 16, y: 8)
                    .padding(.trailing, 28)
            }
        }
    }
}

struct MoodsRow: View {
    @Environment(\.layout) private var layout

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Moods", subtitle: "Browse by how you feel").padding(.horizontal, layout.margin)
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(Mood.allCases) { mood in
                        NavigationLink(value: Route.mood(mood)) {
                            MoodTile(mood: mood).frame(width: layout.isCompact ? 220 : 240)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, layout.margin)
                .padding(.vertical, 4)
            }
            .focusEffectDisabled()
        }
    }
}

struct AttributionFooter: View {
    @Environment(Catalog.self) private var catalog
    @Environment(\.layout) private var layout

    var body: some View {
        Text(catalog.hasRAWG
             ? "Data from RAWG, Wikipedia, Wikidata, Steam and CheapShark. Box art and descriptions: Wikipedia (CC BY-SA for text)."
             : "Data from Wikipedia, Wikidata, Steam and CheapShark. Box art and descriptions: Wikipedia (CC BY-SA for text). Add a free RAWG key in Settings for server-side browsing.")
            .font(.caption)
            .foregroundStyle(Theme.textTertiary)
            .multilineTextAlignment(.leading)
            .padding(.horizontal, layout.margin)
            .padding(.top, 8)
    }
}
