import SwiftUI
import SwiftData

/// Shuffle: can't decide? Filter by mood, time and platform, then let the deck pick.
struct ShuffleView: View {
    enum PoolSource: String, CaseIterable, Identifiable {
        case backlog = "Backlog", discover = "Discover"
        var id: String { rawValue }
    }

    @Environment(Catalog.self) private var catalog
    @Environment(Preferences.self) private var prefs
    @Environment(AppModel.self) private var app
    @Environment(\.layout) private var layout
    @Environment(\.modelContext) private var context
    @Query(sort: \SavedGame.addedAt, order: .reverse) private var saved: [SavedGame]

    @State private var source: PoolSource = .backlog
    @State private var mood: Mood?
    @State private var length: SessionLength?
    @State private var platformID: String?
    @State private var discoverPool: [Game] = []
    @State private var loadingDiscover = false
    @State private var deck: [Game] = []
    @State private var front = 0
    @State private var shuffling = false
    @State private var winner: Game?
    @State private var ticks = 0
    @State private var viewHeight: CGFloat = 800

    private var backlogGames: [SavedGame] { saved.filter { $0.status == .queued || $0.status == .playing } }

    private var pool: [Game] {
        let base: [Game] = source == .backlog ? backlogGames.map(\.asGame) : discoverPool
        return base.filter { game in
            (mood == nil || mood!.matches(game))
                && (length == nil || length!.matches(game))
                && (platformID == nil || game.platforms.contains(platformID!))
        }
    }

    /// Leaves room above and below the deck for the controls and the Shuffle button,
    /// so short screens (an unfolded iPhone Duo, a small Mac window) keep it in view.
    private var cardWidth: CGFloat {
        let fits = (viewHeight - 330) * Theme.coverAspect
        return max(120, min(layout.isCompact ? 210 : 250, fits))
    }

    private var platformChoices: [Platform] { prefs.myPlatforms.isEmpty ? Platform.all : prefs.myPlatforms }

    var body: some View {
        ScrollView {
            VStack(spacing: 22) {
                controls

                ZStack {
                    if deck.count >= 2 {
                        Deck(deck: deck, front: front, width: cardWidth, shuffling: shuffling)
                    } else if source == .discover && loadingDiscover {
                        ProgressView()
                    } else {
                        EmptyState(symbol: "shuffle",
                                   title: source == .backlog ? "Not enough games" : "No matches",
                                   message: source == .backlog
                                       ? "Add at least two games to your backlog, or shuffle from Discover."
                                       : "Nothing matches these filters. Try another mood or session length.",
                                   actionTitle: source == .backlog ? "Use Discover" : "Clear Filters") {
                            if source == .backlog { source = .discover } else { mood = nil; length = nil; platformID = nil }
                        }
                    }
                }
                .frame(height: cardWidth / Theme.coverAspect + 40)
                .frame(maxWidth: .infinity)
                .sensoryFeedback(.impact(weight: .light), trigger: ticks)

                Button {
                    shuffle()
                } label: {
                    Label(shuffling ? "Shuffling" : "Shuffle", systemImage: "shuffle")
                        .frame(minWidth: 140)
                }
                .buttonStyle(.primary)
                .disabled(shuffling || deck.count < 2)
                .keyboardShortcut("r", modifiers: .command)

                if let winner, !shuffling {
                    WinnerCard(game: winner) {
                        let item = Library.save(winner, status: .playing, in: context)
                        if item.playingOn == nil { item.playingOn = platformID ?? winner.platforms.first(where: prefs.isMine) }
                        app.push(.detail(winner))
                    } plan: {
                        app.plan(winner)
                    } again: {
                        shuffle()
                    }
                    .transition(.scale(scale: 0.95).combined(with: .opacity))
                    .padding(.horizontal, layout.margin)
                }
            }
            .padding(.vertical)
            .frame(maxWidth: .infinity)
        }
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { viewHeight = $0 }
        .appBackground()
        .navigationTitle("Shuffle")
        .task(id: "\(source.rawValue)-\(prefs.myPlatformIDs.sorted())-\(catalog.generation)") {
            if source == .discover && discoverPool.isEmpty {
                loadingDiscover = true
                let all = (try? await catalog.pool(catalog.feedPlatforms)) ?? []
                discoverPool = Array(all.filter { $0.boxArtURL != nil && !$0.isUpcoming }.prefix(400))
                loadingDiscover = false
            }
            reloadDeck()
        }
        .onChange(of: mood) { reloadDeck() }
        .onChange(of: length) { reloadDeck() }
        .onChange(of: platformID) { reloadDeck() }
        .onChange(of: saved.count) { if source == .backlog { reloadDeck() } }
        .onAppear {
            if backlogGames.count < 2 { source = .discover }
        }
    }

    private var controls: some View {
        VStack(spacing: 12) {
            Picker("Pool", selection: $source) {
                ForEach(PoolSource.allCases) { Text($0.rawValue).tag($0) }
            }
            .pickerStyle(.segmented)
            .frame(maxWidth: 320)
            .padding(.horizontal, layout.margin)

            chipRow {
                chip("Any mood", selected: mood == nil) { mood = nil }
                ForEach(Mood.allCases) { m in
                    chip(m.title, symbol: m.symbol, selected: mood == m) { mood = mood == m ? nil : m }
                }
            }
            chipRow {
                chip("Any length", symbol: "clock", selected: length == nil) { length = nil }
                ForEach(SessionLength.allCases) { l in
                    chip(l.label, selected: length == l) { length = length == l ? nil : l }
                }
            }
            chipRow {
                chip("Any platform", symbol: "gamecontroller", selected: platformID == nil) { platformID = nil }
                ForEach(platformChoices) { p in
                    chip(p.shortName, dot: p.color, selected: platformID == p.id) { platformID = platformID == p.id ? nil : p.id }
                }
            }
        }
    }

    private func chipRow<Content: View>(@ViewBuilder _ content: () -> Content) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) { content() }
                .padding(.horizontal, layout.margin)
        }
        .focusEffectDisabled()
    }

    private func chip(_ text: String, symbol: String? = nil, dot: Color? = nil, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Pill(text: text, systemImage: symbol, dot: dot,
                 tint: selected ? Theme.textPrimary : Theme.fill,
                 foreground: selected ? Theme.bg : Theme.textPrimary)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private func reloadDeck() {
        winner = nil
        deck = Array(pool.shuffled().prefix(12))
        front = 0
    }

    private func shuffle() {
        guard deck.count >= 2, !shuffling else { return }
        withAnimation(.easeOut(duration: 0.2)) { winner = nil }
        shuffling = true
        Task {
            var wait = 0.05
            let target = Int.random(in: 0..<deck.count)
            // Cycle quickly, then decelerate; the final tick lands on the target.
            while wait < 0.42 {
                try? await Task.sleep(for: .seconds(wait))
                withAnimation(.easeOut(duration: min(wait, 0.2))) { front = (front + 1) % deck.count }
                ticks += 1
                wait *= 1.17
            }
            try? await Task.sleep(for: .seconds(0.35))
            withAnimation(.spring(response: 0.4, dampingFraction: 0.75)) {
                front = target
                winner = deck[target]
                shuffling = false
            }
        }
    }
}

/// Three stacked covers fanned side by side (no tilt); the front one changes as the deck cycles.
struct Deck: View {
    var deck: [Game]
    var front: Int
    var width: CGFloat
    var shuffling: Bool

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    var body: some View {
        ZStack {
            ForEach((0..<min(3, deck.count)).reversed(), id: \.self) { depth in
                let game = deck[(front + depth) % deck.count]
                CoverArt(game: game, maxPixel: 600)
                    .frame(width: width, height: width / Theme.coverAspect)
                    .artwork(radius: 14)
                    .softShadow(radius: depth == 0 ? 24 : 10, y: depth == 0 ? 14 : 6)
                    .scaleEffect(1 - CGFloat(depth) * 0.08)
                    .offset(x: depth == 1 ? -width * 0.28 : (depth == 2 ? width * 0.28 : 0), y: CGFloat(depth) * 8)
                    .opacity(depth == 0 ? 1 : 0.55)
                    .scaleEffect(depth == 0 && shuffling && !reduceMotion ? (front % 2 == 0 ? 1.02 : 0.98) : 1)
                    .zIndex(Double(3 - depth))
                    .id("\(depth)-\(game.id)")
            }
        }
        .animation(reduceMotion ? nil : .easeOut(duration: 0.18), value: front)
        .accessibilityElement()
        .accessibilityLabel(shuffling ? "Shuffling" : "Front card: \(deck[front % max(deck.count, 1)].name)")
    }
}

struct WinnerCard: View {
    var game: Game
    var play: () -> Void
    var plan: () -> Void
    var again: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .top, spacing: 16) {
                CoverArt(game: game, maxPixel: 400)
                    .frame(width: 90, height: 120)
                    .artwork()
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 6) {
                    Text("Tonight's pick").font(.caption.weight(.medium)).foregroundStyle(Theme.accent)
                    Text(game.name).font(.title2.weight(.bold)).foregroundStyle(.white)
                    Text(game.metaLine).font(.footnote).foregroundStyle(.white.opacity(0.7))
                    if !game.summary.isEmpty {
                        Text(game.summary).font(.footnote).foregroundStyle(.white.opacity(0.8)).lineLimit(3)
                    }
                }
                Spacer(minLength: 0)
            }
            ViewThatFits(in: .horizontal) {
                HStack(spacing: 10) { buttons }
                VStack(alignment: .leading, spacing: 10) { buttons }
            }
        }
        .padding(18)
        .frame(maxWidth: 560)
        .background {
            ZStack {
                RemoteImage(url: game.heroURL ?? game.boxArtURL, maxPixel: 800, placeholderTitle: nil)
                    .blur(radius: 40)
                    .scaleEffect(1.4)
                Color.black.opacity(0.58)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous).strokeBorder(.white.opacity(0.1)))
        .softShadow(radius: 20, y: 10)
        .environment(\.colorScheme, .dark)
    }

    @ViewBuilder private var buttons: some View {
        Button("Play Now", action: play).buttonStyle(.primary)
        Button("Plan a Session", action: plan).buttonStyle(.secondary)
        Button("Shuffle Again", action: again).buttonStyle(.secondary)
    }
}
