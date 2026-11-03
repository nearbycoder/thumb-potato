import SwiftUI
import SwiftData

struct GameDetailView: View {
    var ref: GameRef
    var preview: Game?

    @Environment(Catalog.self) private var catalog
    @Environment(Preferences.self) private var prefs
    @Environment(AppModel.self) private var app
    @Environment(\.layout) private var layout
    @Environment(\.modelContext) private var context
    @Environment(\.openURL) private var openURL
    @Query private var allSaved: [SavedGame]

    @State private var detail: GameDetail?
    @State private var error: Error?

    private var game: Game? { detail?.game ?? preview ?? saved?.asGame }
    /// A Steam game can resolve to its catalog record, so look under both keys.
    private var saved: SavedGame? {
        let keys = Set([ref.key, detail?.game.ref.key].compactMap { $0 })
        return allSaved.first { keys.contains($0.key) }
    }

    var body: some View {
        ScrollView {
            if let game {
                VStack(alignment: .leading, spacing: 28) {
                    DetailHeader(game: game, backdrop: game.heroURL ?? detail?.screenshots.first)
                    actions(game).padding(.horizontal, layout.margin)
                    if let saved { BacklogEditor(saved: saved).padding(.horizontal, layout.margin) }
                    if !game.summary.isEmpty { SummarySection(text: game.summary).padding(.horizontal, layout.margin) }
                    if let shots = detail?.screenshots, !shots.isEmpty { ScreenshotsRow(urls: shots) }
                    WhereToPlay(game: game, detail: detail, isLoading: detail == nil && error == nil)
                        .padding(.horizontal, layout.margin)
                    DetailsSection(game: game, detail: detail).padding(.horizontal, layout.margin)
                    if let similar = detail?.similar, !similar.isEmpty {
                        CoverRow(title: "More Like This", games: similar, seeAll: .list("More Like This", similar))
                    }
                    if let error, detail == nil {
                        EmptyState(symbol: "exclamationmark.triangle", title: "Some details couldn't load", message: error.localizedDescription)
                    }
                    AttributionFooter()
                }
                .padding(.bottom, 32)
            } else if let error {
                EmptyState(symbol: "exclamationmark.triangle", title: "Couldn't load this game", message: error.localizedDescription)
            } else {
                LoadingView()
            }
        }
        .appBackground()
        .navigationTitle(game?.name ?? "")
        .inlineTitle()
        .toolbar {
            if let game {
                ToolbarItem(placement: .primaryAction) {
                    ShareLink(item: game.ref.webURL ?? game.ref.deepLink, subject: Text(game.name),
                              message: Text("\(game.name) on Thumb Potato"))
                }
            }
        }
        .userActivity(AppModel.gameActivity) { activity in
            let current = detail?.game.ref ?? ref
            activity.title = game?.name
            activity.userInfo = ["key": current.key]
            activity.webpageURL = current.webURL
            activity.isEligibleForHandoff = true
            activity.isEligibleForSearch = true
        }
        .task(id: ref) {
            do {
                let loaded = try await catalog.detail(ref)
                withAnimation(.easeOut(duration: 0.25)) { detail = loaded }
                if let saved {
                    saved.update(from: loaded.game)
                }
            } catch is CancellationError {
            } catch {
                self.error = error
            }
        }
    }

    @ViewBuilder
    private func actions(_ game: Game) -> some View {
        // The primary action on its own when the row can't fit; secondary actions share one row.
        ViewThatFits(in: .horizontal) {
            HStack(spacing: 10) {
                primaryAction(game)
                secondaryActions(game)
            }
            VStack(alignment: .leading, spacing: 10) {
                primaryAction(game)
                HStack(spacing: 10) { secondaryActions(game) }
            }
        }
    }

    @ViewBuilder
    private func primaryAction(_ game: Game) -> some View {
        if let saved {
            Menu {
                ForEach(BacklogStatus.allCases) { status in
                    Button {
                        setStatus(status, game)
                    } label: {
                        Label(status.label, systemImage: status.symbol)
                    }
                }
                Divider()
                Button("Remove from Backlog", systemImage: "trash", role: .destructive) {
                    Library.remove(saved.key, in: context)
                }
            } label: {
                HStack(spacing: 6) {
                    Image(systemName: saved.status.symbol).symbolVariant(.fill)
                    Text(saved.status.label)
                    Image(systemName: "chevron.down").font(.caption.weight(.semibold))
                }
            }
            .menuStyle(.button)
            .buttonStyle(.primary)
            .fixedSize()
            .accessibilityLabel("Backlog status")
            .accessibilityValue(saved.status.label)
        } else {
            Button {
                setStatus(.queued, game)
            } label: {
                Label(game.isUpcoming ? "Add and Follow" : "Add to Backlog", systemImage: "plus")
            }
            .buttonStyle(.primary)
            .fixedSize()
        }
    }

    @ViewBuilder
    private func secondaryActions(_ game: Game) -> some View {
        Button {
            app.plan(game)
        } label: {
            Label("Plan a Session", systemImage: "calendar.badge.plus")
        }
        .buttonStyle(.secondary)
        .fixedSize()
        if let trailer = detail?.trailerURL {
            Button {
                openURL(trailer)
            } label: {
                Label("Trailer", systemImage: "play.fill")
            }
            .buttonStyle(.secondary)
            .fixedSize()
        }
    }

    private func setStatus(_ status: BacklogStatus, _ game: Game) {
        let item = Library.save(detail?.game ?? game, status: status, in: context)
        if status == .playing, item.playingOn == nil {
            item.playingOn = item.platformIDs.first(where: prefs.isMine) ?? item.platformIDs.first
        }
    }
}

struct DetailHeader: View {
    var game: Game
    var backdrop: URL?

    @Environment(\.layout) private var layout

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            // The image is an overlay with a fixed height so a 16:9 backdrop can't widen
            // the scroll content. Capped by the window height so short windows (an
            // unfolded iPhone Duo) still show the actions above the fold.
            Color.clear
                .containerRelativeFrame(.vertical) { height, _ in
                    backdrop != nil
                        ? min(layout.isCompact ? 280 : 380, height * 0.42)
                        : min(layout.isCompact ? 170 : 230, height * 0.28)
                }
                .frame(maxWidth: .infinity)
                .overlay {
                    if let backdrop {
                        RemoteImage(url: backdrop, maxPixel: 1600, placeholderTitle: nil)
                    } else {
                        RemoteImage(url: game.boxArtURL, maxPixel: 300, placeholderTitle: nil)
                            .blur(radius: 50)
                            .scaleEffect(1.6)
                            .opacity(0.7)
                    }
                }
                .clipped()
                .overlay(alignment: .bottom) {
                    LinearGradient(stops: [
                        .init(color: Theme.bg.opacity(0), location: 0.3),
                        .init(color: Theme.bg.opacity(0.85), location: 0.8),
                        .init(color: Theme.bg, location: 1),
                    ], startPoint: .top, endPoint: .bottom)
                }
                .accessibilityHidden(true)

            HStack(alignment: .bottom, spacing: 18) {
                CoverArt(game: game, maxPixel: 600)
                    .frame(width: layout.detailCoverWidth, height: layout.detailCoverWidth / Theme.coverAspect)
                    .artwork(radius: 14)
                    .softShadow(radius: 18, y: 10)
                    .accessibilityHidden(true)

                VStack(alignment: .leading, spacing: 8) {
                    Text(game.name)
                        .font(layout.isCompact ? .title2.bold() : .largeTitle.bold())
                        .foregroundStyle(Theme.textPrimary)
                        .lineLimit(3)
                        .minimumScaleFactor(0.75)
                        .accessibilityAddTraits(.isHeader)
                    if !metaLine.isEmpty {
                        Text(metaLine).font(.footnote).foregroundStyle(Theme.textSecondary).lineLimit(2)
                    }
                    if game.metacritic != nil || (game.steamDeck ?? 0) >= 2 {
                        HStack(spacing: 8) {
                            if let score = game.metacritic { CriticBadge(score: score, size: 28) }
                            if let deck = game.steamDeck { DeckBadge(category: deck) }
                        }
                    }
                    if !game.platformList.isEmpty {
                        PlatformMonogramRow(platforms: game.platformList, size: layout.isCompact ? 24 : 28)
                    }
                }
                .padding(.bottom, 4)
                Spacer(minLength: 0)
            }
            .padding(.horizontal, layout.margin)
            .offset(y: -56)
            .padding(.bottom, -56)
        }
    }

    /// "2023 · Action, Adventure · Nintendo EPD" (or the release date when upcoming).
    private var metaLine: String {
        var bits: [String] = []
        if game.isUpcoming, let label = game.releaseLabel { bits.append(label) } else if let year = game.year { bits.append(String(year)) }
        if !game.genres.isEmpty { bits.append(game.genres.prefix(2).joined(separator: ", ")) }
        if let dev = game.developer { bits.append(dev) }
        return bits.joined(separator: " · ")
    }
}

/// Status, platform, hours, progress, ownership, rating, notes and follow for a saved game.
struct BacklogEditor: View {
    @Bindable var saved: SavedGame
    @Environment(Preferences.self) private var prefs

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(alignment: .firstTextBaseline) {
                SectionHeader(title: "Your Backlog", subtitle: "Added \(saved.addedAt.formatted(.relative(presentation: .named)))")
                if saved.isUpcoming {
                    Toggle(isOn: Binding(get: { saved.isFollowing }, set: { saved.isFollowing = $0; saved.updatedAt = .now })) {
                        Label("Follow", systemImage: saved.isFollowing ? "bell.fill" : "bell")
                            .font(.footnote.weight(.medium))
                    }
                    .toggleStyle(.button)
                    .buttonStyle(.secondary)
                    .tint(Theme.accent)
                    .help(saved.releaseDatePrecision == .day ? "Get an alert on release day" : "Release date not set yet")
                }
            }

            Picker("Status", selection: Binding(get: { saved.status }, set: { status in
                saved.status = status
                if status == .playing, saved.playingOn == nil {
                    saved.playingOn = saved.platformIDs.first(where: prefs.isMine) ?? saved.platformIDs.first
                }
            })) {
                ForEach(BacklogStatus.allCases) { Text($0.label).tag($0) }
            }
            .pickerStyle(.segmented)
            .labelsHidden()

            if !saved.platforms.isEmpty {
                LabeledContent("Playing on") {
                    Picker("Playing on", selection: Binding(get: { saved.playingOn }, set: { saved.playingOn = $0; saved.updatedAt = .now })) {
                        Text("Not set").tag(String?.none)
                        ForEach(saved.platforms) { Text($0.name).tag(String?.some($0.id)) }
                    }
                    .labelsHidden()
                    .fixedSize()
                }
            }

            LabeledContent("Hours played") {
                HStack(spacing: 8) {
                    Text(BacklogStats.hours(saved.hoursPlayed)).monospacedDigit().foregroundStyle(Theme.textPrimary)
                    Stepper("Hours played", value: Binding(get: { saved.hoursPlayed },
                                                           set: { saved.hoursPlayed = max(0, $0); saved.updatedAt = .now }),
                            in: 0...10000, step: 0.5)
                        .labelsHidden()
                }
            }

            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Text("Progress").foregroundStyle(Theme.textPrimary)
                    Spacer()
                    Text("\(saved.progress)%").monospacedDigit().foregroundStyle(Theme.textSecondary)
                }
                Slider(value: Binding(get: { Double(saved.progress) },
                                      set: { saved.progress = Int($0.rounded()); saved.updatedAt = .now }),
                       in: 0...100, step: 5)
                    .tint(Theme.accent)
                    .accessibilityLabel("Progress")
                    .accessibilityValue("\(saved.progress) percent")
            }

            Toggle("I own it", isOn: Binding(get: { saved.owned }, set: { saved.owned = $0; saved.updatedAt = .now }))
                .tint(Theme.accent)

            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Text("Your Rating").foregroundStyle(Theme.textPrimary)
                    Spacer()
                    Text(StarRating.label(saved.rating)).font(.footnote).foregroundStyle(Theme.textSecondary)
                }
                StarRating(value: Binding(get: { saved.rating }, set: { saved.rating = $0; saved.updatedAt = .now }))
            }

            TextField("Notes", text: Binding(get: { saved.notes }, set: { saved.notes = $0; saved.updatedAt = .now }),
                      prompt: Text("Add a note"), axis: .vertical)
                .font(.body)
                .lineLimit(1...4)
                .textFieldStyle(.plain)
                .padding(10)
                .background(Theme.fill, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
        }
        .font(.subheadline)
        .card()
    }
}

struct SummarySection: View {
    var text: String
    @State private var expanded = false

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeader(title: "About")
            Text(text)
                .font(.body)
                .foregroundStyle(Theme.textPrimary)
                .lineLimit(expanded ? nil : 5)
                .textSelection(.enabled)
            if text.count > 280 {
                Button(expanded ? "Less" : "More") { withAnimation(.snappy) { expanded.toggle() } }
                    .font(.seeAll)
                    .foregroundStyle(Theme.accent)
                    .buttonStyle(.plain)
            }
        }
    }
}

struct ScreenshotsRow: View {
    var urls: [URL]
    @Environment(\.layout) private var layout
    @Environment(\.openURL) private var openURL

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Screenshots").padding(.horizontal, layout.margin)
            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 12) {
                    ForEach(urls, id: \.self) { url in
                        Button { openURL(url) } label: {
                            RemoteImage(url: url, maxPixel: 900, placeholderTitle: nil)
                                .frame(width: layout.isCompact ? 280 : 400, height: (layout.isCompact ? 280 : 400) * 9 / 16)
                                .artwork(radius: 12)
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("Screenshot")
                    }
                }
                .padding(.horizontal, layout.margin)
            }
            .focusEffectDisabled()
        }
    }
}

/// Store rows for the game's platforms (mine first) and PC prices from CheapShark.
struct WhereToPlay: View {
    var game: Game
    var detail: GameDetail?
    var isLoading: Bool

    @Environment(Preferences.self) private var prefs
    @Environment(\.openURL) private var openURL

    private var offers: [StoreOffer] {
        detail?.stores ?? Store.offers(for: game, myPlatforms: prefs.myPlatformIDs)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            SectionHeader(title: "Where to Play")
            let mine = offers.filter(\.isMine)
            let other = offers.filter { !$0.isMine }
            if offers.isEmpty {
                Text("No stores known for this game's platforms.").font(.subheadline).foregroundStyle(Theme.textSecondary)
            }
            if !mine.isEmpty { group("Your Platforms", mine) }
            if !other.isEmpty { group(mine.isEmpty ? "Stores" : "Other Stores", other) }

            if let deals = detail?.pcDeals, !deals.isEmpty {
                VStack(alignment: .leading, spacing: 4) {
                    groupLabel("PC Prices")
                    ForEach(deals) { deal in
                        Button { openURL(deal.link) } label: { DealRow(deal: deal) }
                            .buttonStyle(.plain)
                        if deal.id != deals.last?.id { Divider().overlay(Theme.hairline) }
                    }
                    Text("PC prices from CheapShark, in US dollars.").font(.caption2).foregroundStyle(Theme.textTertiary).padding(.top, 4)
                }
            } else if isLoading, game.platforms.contains(where: { $0 == "pc" || $0 == "mac" }) {
                ProgressView().frame(maxWidth: .infinity)
            }
        }
        .card()
    }

    private func groupLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.caption2.weight(.semibold))
            .tracking(0.6)
            .foregroundStyle(Theme.textTertiary)
    }

    private func group(_ title: String, _ offers: [StoreOffer]) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            groupLabel(title)
            ForEach(offers) { offer in
                Button { openURL(offer.link) } label: { StoreRow(offer: offer) }
                    .buttonStyle(.plain)
                    .help("Open \(game.name) on \(offer.store.name)")
                if offer.id != offers.last?.id { Divider().overlay(Theme.hairline) }
            }
        }
    }
}

struct StoreRow: View {
    var offer: StoreOffer

    var body: some View {
        HStack(spacing: 12) {
            MonogramTile(monogram: offer.store.monogram, color: offer.store.color, size: 32)
            Text(offer.store.name).font(.subheadline.weight(.medium)).foregroundStyle(Theme.textPrimary)
            Spacer(minLength: 8)
            if let regular = offer.regularPrice {
                Text(regular).font(.footnote).strikethrough().foregroundStyle(Theme.textTertiary)
            }
            if let price = offer.price {
                Text(price).font(.subheadline.weight(.semibold))
                    .foregroundStyle(offer.regularPrice != nil ? Theme.accent : Theme.textPrimary)
            }
            Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(Theme.textTertiary)
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel([offer.store.name, offer.price].compactMap { $0 }.joined(separator: ", "))
        .accessibilityAddTraits(.isLink)
    }
}

struct DealRow: View {
    var deal: Deal

    var body: some View {
        HStack(spacing: 12) {
            Text(deal.storeName).font(.subheadline.weight(.medium)).foregroundStyle(Theme.textPrimary).lineLimit(1)
            if deal.isDiscounted {
                Text("−\(Int(deal.savings.rounded()))%")
                    .font(.caption.weight(.bold))
                    .foregroundStyle(Theme.onAccent)
                    .padding(.horizontal, 6).padding(.vertical, 2)
                    .background(Theme.accent, in: Capsule())
            }
            Spacer(minLength: 8)
            if deal.isDiscounted {
                Text(deal.retailPrice.usd).font(.footnote).strikethrough().foregroundStyle(Theme.textTertiary)
            }
            Text(deal.price == 0 ? "Free" : deal.price.usd).font(.subheadline.weight(.semibold))
                .foregroundStyle(deal.isDiscounted ? Theme.accent : Theme.textPrimary)
            Image(systemName: "arrow.up.right").font(.caption.weight(.semibold)).foregroundStyle(Theme.textTertiary)
        }
        .padding(.vertical, 6)
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(deal.storeName), \(deal.price.usd)\(deal.isDiscounted ? ", \(Int(deal.savings.rounded())) percent off" : "")")
        .accessibilityAddTraits(.isLink)
    }
}

struct DetailsSection: View {
    var game: Game
    var detail: GameDetail?

    var body: some View {
        let rows = self.rows
        if !rows.isEmpty || detail?.website != nil {
            VStack(alignment: .leading, spacing: 10) {
                SectionHeader(title: "Details")
                ForEach(rows, id: \.0) { label, value in
                    LabeledContent(label) {
                        Text(value).foregroundStyle(Theme.textPrimary).multilineTextAlignment(.trailing)
                    }
                    .font(.subheadline)
                    if label != rows.last?.0 { Divider().overlay(Theme.hairline) }
                }
                if let website = detail?.website {
                    Link(destination: website) {
                        Label("Official site", systemImage: "arrow.up.right.square").font(.footnote.weight(.medium))
                    }
                    .tint(Theme.accent)
                    .padding(.top, 4)
                }
            }
            .card()
        }
    }

    private var rows: [(String, String)] {
        var rows: [(String, String)] = []
        if let label = game.releaseLabel {
            var value = label
            if game.isUpcoming, let date = game.releaseDate,
               let countdown = ReleaseDateFormat.countdown(date, precision: game.releaseDatePrecision) {
                value += " (\(countdown))"
            }
            rows.append(("Release Date", value))
        }
        if let dev = game.developer { rows.append(("Developer", dev)) }
        if let pub = game.publisher, pub != game.developer { rows.append(("Publisher", pub)) }
        if !game.genres.isEmpty { rows.append(("Genres", game.genres.joined(separator: ", "))) }
        if let reviews = detail?.steamReviews { rows.append(("Steam Reviews", reviews)) }
        if let score = game.metacritic { rows.append(("Metacritic", "\(score)")) }
        return rows
    }
}
