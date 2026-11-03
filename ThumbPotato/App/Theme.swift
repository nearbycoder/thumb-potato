import SwiftUI

extension Color {
    init(hex: UInt32, alpha: Double = 1) {
        self.init(.sRGB,
                  red: Double((hex >> 16) & 0xFF) / 255,
                  green: Double((hex >> 8) & 0xFF) / 255,
                  blue: Double(hex & 0xFF) / 255,
                  opacity: alpha)
    }

    init(hexString: String) {
        let cleaned = hexString.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        self.init(hex: UInt32(cleaned, radix: 16) ?? 0x888888)
    }

    /// A color that adapts to light and dark appearance.
    init(light: UInt32, dark: UInt32) {
        #if os(iOS)
        self.init(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(Color(hex: dark)) : UIColor(Color(hex: light))
        })
        #else
        self.init(nsColor: NSColor(name: nil) { appearance in
            appearance.bestMatch(from: [.darkAqua, .aqua]) == .darkAqua
                ? NSColor(Color(hex: dark)) : NSColor(Color(hex: light))
        })
        #endif
    }
}

/// Design tokens (see DESIGN.md). Dark is the primary appearance.
enum Theme {
    static let bg = Color(light: 0xF4F5F7, dark: 0x0B0C10)
    static let surface = Color(light: 0xFFFFFF, dark: 0x14151B)
    static let surfaceElevated = Color(light: 0xFFFFFF, dark: 0x1C1D25)
    static let hairline = Color.primary.opacity(0.08)
    static let fill = Color.primary.opacity(0.09)
    static let textPrimary = Color(light: 0x111114, dark: 0xF4F5F7)
    static let textSecondary = Color.primary.opacity(0.62)
    static let textTertiary = Color.primary.opacity(0.40)
    /// "Volt".
    static let accent = Color(light: 0x4F8A00, dark: 0xB8F23D)
    static let onAccent = Color(light: 0xFFFFFF, dark: 0x122000)
    /// "Trophy" gold.
    static let success = Color(light: 0xB7800F, dark: 0xFFC54D)
    static let danger = Color(light: 0xD63E45, dark: 0xF2545B)
    static let info = Color(light: 0x2F6FE0, dark: 0x5B8CFF)
    static let warning = Color(hex: 0xF0C94A)

    static let cardRadius: CGFloat = 16
    static let coverRadius: CGFloat = 10
    /// Box art is portrait 3:4 (game cases).
    static let coverAspect: CGFloat = 3 / 4
}

extension Font {
    static let sectionTitle = Font.title3.weight(.semibold)
    static let seeAll = Font.subheadline.weight(.medium)
    static let posterTitle = Font.subheadline.weight(.semibold)
    static let meta = Font.footnote
    static let captionStrong = Font.caption.weight(.medium)
}

/// Responsive sizes derived from the horizontal size class, so iPhone, the unfolded
/// iPhone Duo, iPad and Mac each get appropriate densities.
struct LayoutMetrics {
    var isCompact: Bool

    var coverWidth: CGFloat { isCompact ? 120 : 150 }
    var gridMinWidth: CGFloat { isCompact ? 104 : 160 }
    var heroHeight: CGFloat { isCompact ? 460 : 420 }
    var margin: CGFloat { isCompact ? 16 : 24 }
    var detailCoverWidth: CGFloat { isCompact ? 116 : 168 }
}

private struct LayoutKey: EnvironmentKey {
    static let defaultValue = LayoutMetrics(isCompact: true)
}

extension EnvironmentValues {
    var layout: LayoutMetrics {
        get { self[LayoutKey.self] }
        set { self[LayoutKey.self] = newValue }
    }
}

// MARK: - Surfaces

/// Soft, low shadow that adapts to the appearance.
struct SoftShadow: ViewModifier {
    @Environment(\.colorScheme) private var scheme
    var radius: CGFloat = 12
    var y: CGFloat = 6

    func body(content: Content) -> some View {
        content.shadow(color: .black.opacity(scheme == .dark ? 0.45 : 0.10), radius: radius, y: y)
    }
}

struct CardModifier: ViewModifier {
    var padding: CGFloat

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(Theme.surface, in: RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: Theme.cardRadius, style: .continuous).strokeBorder(Theme.hairline))
    }
}

extension View {
    func card(padding: CGFloat = 16) -> some View { modifier(CardModifier(padding: padding)) }
    func softShadow(radius: CGFloat = 12, y: CGFloat = 6) -> some View { modifier(SoftShadow(radius: radius, y: y)) }
    func appBackground() -> some View { background(Theme.bg.ignoresSafeArea()) }

    /// Rounded artwork with the hairline border every cover gets.
    func artwork(radius: CGFloat = Theme.coverRadius) -> some View {
        clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: radius, style: .continuous).strokeBorder(Theme.hairline))
    }
}

// MARK: - Buttons

struct PrimaryButtonStyle: ButtonStyle {
    var tint: Color = Theme.accent
    var foreground: Color = Theme.onAccent

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(foreground)
            .padding(.horizontal, 18)
            .frame(minHeight: ButtonMetrics.height)
            .background(tint, in: Capsule())
            .opacity(configuration.isPressed ? 0.8 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

struct SecondaryButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .font(.subheadline.weight(.semibold))
            .foregroundStyle(Theme.textPrimary)
            .padding(.horizontal, 18)
            .frame(minHeight: ButtonMetrics.height)
            .background(Theme.fill, in: Capsule())
            .overlay(Capsule().strokeBorder(Theme.hairline))
            .opacity(configuration.isPressed ? 0.7 : 1)
            .scaleEffect(configuration.isPressed ? 0.98 : 1)
            .animation(.easeOut(duration: 0.15), value: configuration.isPressed)
    }
}

enum ButtonMetrics {
    static var height: CGFloat {
        #if os(macOS)
        36
        #else
        44
        #endif
    }
}

extension ButtonStyle where Self == PrimaryButtonStyle {
    static var primary: PrimaryButtonStyle { PrimaryButtonStyle() }
    static func primary(_ tint: Color, foreground: Color = Theme.onAccent) -> PrimaryButtonStyle {
        PrimaryButtonStyle(tint: tint, foreground: foreground)
    }
}

extension ButtonStyle where Self == SecondaryButtonStyle {
    static var secondary: SecondaryButtonStyle { SecondaryButtonStyle() }
}

// MARK: - Small pieces

struct Pill: View {
    var text: String
    var systemImage: String?
    var dot: Color?
    var tint: Color = Theme.fill
    var foreground: Color = Theme.textPrimary

    var body: some View {
        HStack(spacing: 5) {
            if let dot { Circle().fill(dot).frame(width: 6, height: 6) }
            if let systemImage { Image(systemName: systemImage).font(.caption2.weight(.semibold)) }
            Text(text)
        }
        .font(.captionStrong)
        .foregroundStyle(foreground)
        .padding(.horizontal, 9)
        .padding(.vertical, 5)
        .background(tint, in: Capsule())
    }
}

struct SectionHeader: View {
    var title: String
    var subtitle: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title).font(.sectionTitle).foregroundStyle(Theme.textPrimary)
            if let subtitle {
                Text(subtitle).font(.meta).foregroundStyle(Theme.textSecondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The app mark: Spud's controller on charcoal, used in About and onboarding.
struct BrandMark: View {
    var size: CGFloat = 44

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.22, style: .continuous)
                .fill(LinearGradient(colors: [Color(hex: 0x1E2027), Color(hex: 0x0B0C10)], startPoint: .top, endPoint: .bottom))
            Image(systemName: "gamecontroller.fill")
                .font(.system(size: size * 0.44, weight: .medium))
                .foregroundStyle(LinearGradient(colors: [Color(hex: 0xCDFA62), Color(hex: 0x93D12A)], startPoint: .top, endPoint: .bottom))
        }
        .frame(width: size, height: size)
        .accessibilityElement(children: .ignore)
        .accessibilityHidden(true)
    }
}
