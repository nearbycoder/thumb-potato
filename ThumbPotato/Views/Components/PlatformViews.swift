import SwiftUI

/// A rounded-square monogram on a brand color. No third-party logos are bundled.
struct MonogramTile: View {
    var monogram: String
    var color: Color
    var size: CGFloat = 36

    var body: some View {
        ZStack {
            color
            Text(monogram)
                .font(.system(size: size * (monogram.count > 2 ? 0.28 : 0.36), weight: .bold, design: .rounded))
                .foregroundStyle(color.luminance > 0.6 ? Color.black.opacity(0.85) : .white)
                .minimumScaleFactor(0.6)
                .padding(2)
        }
        .frame(width: size, height: size)
        .artwork(radius: size * 0.24)
    }
}

struct PlatformTile: View {
    var platform: Platform
    var size: CGFloat = 36

    var body: some View {
        MonogramTile(monogram: platform.monogram, color: platform.color, size: size)
            .accessibilityLabel(platform.name)
    }
}

/// A selectable platform row/chip for Settings, onboarding and Browse.
struct PlatformChip: View {
    var platform: Platform
    var isSelected = false
    var showsCheck = true

    var body: some View {
        HStack(spacing: 10) {
            PlatformTile(platform: platform, size: 30)
            Text(platform.name)
                .font(.subheadline.weight(.medium))
                .foregroundStyle(Theme.textPrimary)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
            Spacer(minLength: 0)
            if showsCheck {
                Image(systemName: isSelected ? "checkmark.circle.fill" : "circle")
                    .foregroundStyle(isSelected ? Theme.accent : Theme.textTertiary)
            } else {
                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(Theme.textTertiary)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
        .background(Theme.surface, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(isSelected ? Theme.accent.opacity(0.6) : Theme.hairline))
        .contentShape(Rectangle())
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(platform.name)
        .accessibilityValue(showsCheck ? (isSelected ? "Selected" : "Not selected") : "")
    }
}

/// The platforms a game is on as small monograms; mine are full color, the rest dimmed.
struct PlatformMonogramRow: View {
    var platforms: [Platform]
    var size: CGFloat = 28
    @Environment(Preferences.self) private var prefs

    var body: some View {
        HStack(spacing: 6) {
            ForEach(platforms) { p in
                PlatformTile(platform: p, size: size)
                    .opacity(prefs.myPlatformIDs.isEmpty || prefs.isMine(p) ? 1 : 0.45)
                    .overlay(alignment: .bottomTrailing) {
                        if prefs.isMine(p) {
                            Circle().fill(Theme.accent).frame(width: 7, height: 7)
                                .overlay(Circle().strokeBorder(Theme.bg, lineWidth: 1.5))
                                .offset(x: 2, y: 2)
                        }
                    }
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Platforms: " + platforms.map(\.name).joined(separator: ", "))
    }
}

/// A critic score in a small rounded square, colored by band.
struct CriticBadge: View {
    var score: Int
    var size: CGFloat = 30

    var color: Color {
        score >= 75 ? Theme.accent : (score >= 50 ? Theme.warning : Theme.danger)
    }

    var body: some View {
        Text("\(score)")
            .font(.system(size: size * 0.46, weight: .bold, design: .rounded))
            .foregroundStyle(score >= 75 ? Theme.onAccent : .black.opacity(0.85))
            .frame(width: size, height: size)
            .background(color, in: RoundedRectangle(cornerRadius: size * 0.22, style: .continuous))
            .accessibilityLabel("Metacritic \(score)")
    }
}

/// Steam Deck Verified / Playable.
struct DeckBadge: View {
    var category: Int

    var body: some View {
        if category >= 2 {
            Pill(text: category == 3 ? "Deck Verified" : "Deck Playable",
                 systemImage: category == 3 ? "checkmark.seal.fill" : "info.circle.fill",
                 tint: (category == 3 ? Theme.accent : Theme.warning).opacity(0.16),
                 foreground: category == 3 ? Theme.accent : Theme.warning)
                .accessibilityLabel(category == 3 ? "Steam Deck Verified" : "Steam Deck Playable")
        }
    }
}

extension Color {
    /// Approximate relative luminance, used to pick legible monogram text.
    var luminance: Double {
        #if os(iOS)
        var r: CGFloat = 0, g: CGFloat = 0, b: CGFloat = 0, a: CGFloat = 0
        UIColor(self).getRed(&r, green: &g, blue: &b, alpha: &a)
        #else
        let c = NSColor(self).usingColorSpace(.sRGB) ?? .gray
        let r = c.redComponent, g = c.greenComponent, b = c.blueComponent
        #endif
        return 0.2126 * Double(r) + 0.7152 * Double(g) + 0.0722 * Double(b)
    }
}

/// A 1–5 star rating control.
struct StarRating: View {
    @Binding var value: Int
    var size: CGFloat = 24
    var editable = true

    var body: some View {
        HStack(spacing: 6) {
            ForEach(1...5, id: \.self) { i in
                Image(systemName: i <= value ? "star.fill" : "star")
                    .font(.system(size: size))
                    .foregroundStyle(i <= value ? Theme.accent : Theme.textTertiary)
                    .contentTransition(.symbolEffect(.replace))
                    .onTapGesture {
                        guard editable else { return }
                        withAnimation(.snappy) { value = value == i ? 0 : i }
                    }
                    .accessibilityHidden(true)
            }
        }
        .sensoryFeedback(.selection, trigger: value)
        .accessibilityElement()
        .accessibilityLabel("Rating")
        .accessibilityValue("\(value) of 5 stars")
        .accessibilityAdjustableAction { direction in
            guard editable else { return }
            switch direction {
            case .increment: value = min(5, value + 1)
            case .decrement: value = max(0, value - 1)
            @unknown default: break
            }
        }
    }

    static func label(_ value: Int) -> String {
        switch value {
        case 1: "Not for me"
        case 2: "It was fine"
        case 3: "Good"
        case 4: "Great"
        case 5: "All-time favorite"
        default: "Tap to rate"
        }
    }
}

/// Loads async content with standard loading and error states.
struct Loadable<Value, Content: View>: View {
    var id: String
    var load: () async throws -> Value
    @ViewBuilder var content: (Value) -> Content

    @State private var value: Value?
    @State private var error: Error?
    @State private var attempt = 0

    var body: some View {
        Group {
            if let value {
                content(value)
            } else if let error {
                EmptyState(symbol: "wifi.exclamationmark", title: "Couldn't load",
                           message: error.localizedDescription, actionTitle: "Try Again") { attempt += 1 }
            } else {
                LoadingView()
            }
        }
        .task(id: "\(id)#\(attempt)") {
            error = nil
            do {
                let loaded = try await load()
                value = loaded
            } catch is CancellationError {
            } catch {
                if value == nil { self.error = error }
            }
        }
    }
}

struct LoadingView: View {
    var body: some View {
        ProgressView()
            .controlSize(.large)
            .tint(Theme.textSecondary)
            .frame(maxWidth: .infinity, minHeight: 160)
    }
}

struct EmptyState: View {
    var symbol: String
    var title: String
    var message: String
    var actionTitle: String?
    var action: (() -> Void)?

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: symbol)
                .font(.system(size: 40, weight: .light))
                .foregroundStyle(Theme.textTertiary)
                .padding(.bottom, 4)
            Text(title).font(.headline).foregroundStyle(Theme.textPrimary).multilineTextAlignment(.center)
            Text(message).font(.footnote).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
            if let actionTitle, let action {
                Button(actionTitle, action: action).buttonStyle(.primary).padding(.top, 8)
            }
        }
        .frame(maxWidth: 320)
        .padding(32)
        .frame(maxWidth: .infinity)
    }
}

/// A simple wrapping layout for chips.
struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var x: CGFloat = 0, y: CGFloat = 0, rowHeight: CGFloat = 0, widest: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > 0 && x + size.width > maxWidth {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            x += size.width + spacing
            widest = max(widest, x - spacing)
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: min(widest, maxWidth), height: y + rowHeight)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX, y = bounds.minY, rowHeight: CGFloat = 0
        for view in subviews {
            let size = view.sizeThatFits(.unspecified)
            if x > bounds.minX && x + size.width > bounds.maxX {
                x = bounds.minX
                y += rowHeight + spacing
                rowHeight = 0
            }
            view.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
