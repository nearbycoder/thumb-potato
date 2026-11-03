import SwiftUI
import SwiftData

/// Box art (3:4) with caption and an optional backlog status mark.
struct CoverCard: View {
    var game: Game
    var width: CGFloat
    var showsCaption = true
    var status: BacklogStatus?

    @State private var hovering = false

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            CoverArt(game: game, maxPixel: width * 3)
                .frame(width: width, height: width / Theme.coverAspect)
                .artwork()
                .overlay(alignment: .topTrailing) {
                    if let status { StatusMark(status: status).padding(6) }
                }
                .softShadow(radius: hovering ? 16 : 8, y: hovering ? 10 : 4)
                .scaleEffect(hovering ? 1.02 : 1)

            if showsCaption {
                CoverCaption(game: game)
            }
        }
        .frame(width: width, alignment: .leading)
        .contentShape(Rectangle())
        .onHover { h in withAnimation(.easeOut(duration: 0.2)) { hovering = h } }
        .accessibilityElement(children: .combine)
    }
}

/// Box art, falling back to a blurred hero when a game only has wide art.
struct CoverArt: View {
    var game: Game
    var maxPixel: CGFloat = 600

    var body: some View {
        if game.boxArtURL == nil, let hero = game.heroURL {
            ZStack {
                RemoteImage(url: hero, maxPixel: maxPixel, placeholderTitle: nil)
                LinearGradient(colors: [.clear, .black.opacity(0.7)], startPoint: .top, endPoint: .bottom)
                Text(game.name)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .lineLimit(3)
                    .padding(8)
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }
        } else {
            RemoteImage(url: game.boxArtURL, maxPixel: maxPixel, placeholderTitle: game.name)
        }
    }
}

/// Title, "2025 · Action" and up to three platform dots.
struct CoverCaption: View {
    var game: Game

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(game.name)
                .font(.posterTitle)
                .foregroundStyle(Theme.textPrimary)
                .lineLimit(1)
            HStack(spacing: 6) {
                Text(game.subtitle)
                    .font(.caption)
                    .foregroundStyle(game.priceNote != nil ? Theme.accent : Theme.textSecondary)
                    .lineLimit(1)
                Spacer(minLength: 0)
                PlatformDots(platforms: game.platformList)
            }
        }
    }
}

struct PlatformDots: View {
    var platforms: [Platform]
    @Environment(Preferences.self) private var prefs

    var body: some View {
        let shown = Array((platforms.filter(prefs.isMine) + platforms.filter { !prefs.isMine($0) }).prefix(3))
        HStack(spacing: 3) {
            ForEach(shown) { p in
                Circle().fill(p.color).frame(width: 6, height: 6)
                    .overlay(Circle().strokeBorder(Theme.hairline))
            }
        }
        .accessibilityHidden(true)
    }
}

struct StatusMark: View {
    var status: BacklogStatus

    var body: some View {
        Image(systemName: status.symbol)
            .symbolVariant(.fill)
            .font(.caption.weight(.semibold))
            .foregroundStyle(status.color)
            .padding(6)
            .background(.ultraThinMaterial, in: Circle())
            .accessibilityLabel(status.label)
    }
}

/// A horizontally scrolling row of covers with a header and "See All".
struct CoverRow: View {
    var title: String
    var subtitle: String?
    var games: [Game]
    var seeAll: Route?

    @Environment(\.layout) private var layout
    @Query private var saved: [SavedGame]

    var body: some View {
        let statusByKey = Dictionary(saved.map { ($0.key, $0.status) }, uniquingKeysWith: { a, _ in a })
        VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .firstTextBaseline) {
                SectionHeader(title: title, subtitle: subtitle)
                if let seeAll {
                    NavigationLink(value: seeAll) {
                        HStack(spacing: 2) {
                            Text("See All")
                            Image(systemName: "chevron.right").font(.caption.weight(.semibold))
                        }
                        .font(.seeAll)
                        .foregroundStyle(Theme.accent)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("See all \(title)")
                }
            }
            .padding(.horizontal, layout.margin)

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(alignment: .top, spacing: 14) {
                    ForEach(games) { game in
                        CoverLink(route: .detail(game)) {
                            CoverCard(game: game, width: layout.coverWidth, status: statusByKey[game.ref.key])
                        }
                        .gameContextMenu(game)
                    }
                }
                .padding(.horizontal, layout.margin)
                .padding(.vertical, 8)
            }
            .focusEffectDisabled()
        }
    }
}

/// An adaptive grid of covers with equal columns.
struct CoverGrid: View {
    var games: [Game]
    /// Called as the last few covers scroll into view, to fetch the next page.
    var onNearEnd: (() -> Void)?

    @Environment(\.layout) private var layout
    @Query private var saved: [SavedGame]

    var body: some View {
        let statusByKey = Dictionary(saved.map { ($0.key, $0.status) }, uniquingKeysWith: { a, _ in a })
        let tail = Set(games.suffix(12).map(\.id))
        LazyVGrid(columns: [GridItem(.adaptive(minimum: layout.gridMinWidth), spacing: layout.isCompact ? 12 : 16, alignment: .top)], spacing: 20) {
            ForEach(games) { game in
                CoverLink(route: .detail(game)) {
                    GridCover(game: game, status: statusByKey[game.ref.key])
                }
                .gameContextMenu(game)
                .onAppear { if tail.contains(game.id) { onNearEnd?() } }
            }
        }
        .padding(.horizontal, layout.margin)
        .focusEffectDisabled()
    }
}

/// A cover grid that keeps loading pages as you scroll, until a page comes back empty.
struct PagedGrid<Empty: View>: View {
    var id: String
    var load: (Int) async throws -> [Game]
    @ViewBuilder var empty: () -> Empty

    @State private var games: [Game] = []
    @State private var nextPage = 1
    @State private var exhausted = false
    @State private var loading = false
    @State private var error: Error?
    @State private var attempt = 0

    var body: some View {
        Group {
            if !games.isEmpty {
                VStack(spacing: 20) {
                    CoverGrid(games: games) { Task { await loadMore() } }
                    footer
                }
            } else if let error {
                EmptyState(symbol: "wifi.exclamationmark", title: "Couldn't load",
                           message: error.localizedDescription, actionTitle: "Try Again") { attempt += 1 }
            } else if exhausted {
                empty()
            } else {
                LoadingView()
            }
        }
        .task(id: "\(id)#\(attempt)") {
            games = []
            nextPage = 1
            exhausted = false
            error = nil
            loading = false
            await loadMore()
        }
    }

    @ViewBuilder private var footer: some View {
        if loading {
            ProgressView().frame(height: 44)
        } else if error != nil, !exhausted {
            Button("Load More") { Task { await loadMore() } }.buttonStyle(.secondary)
        }
    }

    private func loadMore() async {
        guard !loading, !exhausted else { return }
        loading = true
        defer { loading = false }
        let page = nextPage
        do {
            let batch = try await load(page)
            guard page == nextPage else { return }
            let known = Set(games.map(\.id))
            let fresh = batch.filter { !known.contains($0.id) }
            games += fresh
            nextPage += 1
            error = nil
            // Stop on an empty page, or after a page that only repeats what we have.
            if batch.isEmpty || page >= 40 || (fresh.isEmpty && page > 1) { exhausted = true }
        } catch is CancellationError {
        } catch {
            self.error = error
        }
    }
}

/// A cover that fills its grid cell at a fixed 3:4, so every column is equal.
struct GridCover: View {
    var game: Game
    var status: BacklogStatus?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Color.clear
                .aspectRatio(Theme.coverAspect, contentMode: .fit)
                .overlay { CoverArt(game: game, maxPixel: 500) }
                .artwork()
                .overlay(alignment: .topTrailing) {
                    if let status { StatusMark(status: status).padding(6) }
                }
                .softShadow(radius: 8, y: 4)
            CoverCaption(game: game)
        }
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

/// Quick actions on any cover.
struct GameContextMenu: ViewModifier {
    var game: Game
    @Environment(\.modelContext) private var context
    @Environment(AppModel.self) private var app

    func body(content: Content) -> some View {
        content.contextMenu {
            ForEach([BacklogStatus.queued, .playing, .beaten]) { status in
                Button {
                    Library.save(game, status: status, in: context)
                } label: {
                    Label(status.label, systemImage: status.symbol)
                }
            }
            Button {
                app.plan(game)
            } label: {
                Label("Plan a Session…", systemImage: "calendar.badge.plus")
            }
            ShareLink(item: game.ref.webURL ?? game.ref.deepLink, subject: Text(game.name),
                      message: Text("\(game.name) on Thumb Potato"))
        }
    }
}

extension View {
    func gameContextMenu(_ game: Game) -> some View { modifier(GameContextMenu(game: game)) }
}

/// A mood tile: duotone gradient, symbol and title.
struct MoodTile: View {
    var mood: Mood
    var height: CGFloat = 112

    var body: some View {
        ZStack(alignment: .bottomLeading) {
            LinearGradient(colors: mood.gradient, startPoint: .topLeading, endPoint: .bottomTrailing)
            Image(systemName: mood.symbol)
                .font(.system(size: 24, weight: .regular))
                .foregroundStyle(.white.opacity(0.9))
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .padding(14)
            VStack(alignment: .leading, spacing: 2) {
                Text(mood.title).font(.title3.weight(.semibold)).foregroundStyle(.white)
                Text(mood.blurb).font(.caption).foregroundStyle(.white.opacity(0.78)).lineLimit(1)
            }
            .padding(14)
        }
        .frame(height: height)
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).strokeBorder(.white.opacity(0.08)))
        .contentShape(Rectangle())
        .accessibilityElement(children: .combine)
    }
}

/// A navigation push that never takes keyboard focus on macOS, so cover rows
/// don't show a focus outline when a window opens.
struct CoverLink<Label: View>: View {
    var route: Route
    @ViewBuilder var label: () -> Label
    @Environment(AppModel.self) private var app

    var body: some View {
        Button {
            app.push(route)
        } label: {
            label()
        }
        .buttonStyle(.plain)
        .focusable(false)
        .focusEffectDisabled()
    }
}
