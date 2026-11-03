import SwiftUI
import Observation

enum Appearance: String, CaseIterable, Identifiable {
    case system, dark, light
    var id: String { rawValue }

    var label: String {
        switch self {
        case .system: "System"
        case .dark: "Dark"
        case .light: "Light"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: nil
        case .dark: .dark
        case .light: .light
        }
    }
}

/// User settings, mirrored to iCloud key-value storage so your platforms, region
/// and RAWG key follow you between iPhone, iPad and Mac.
@MainActor
@Observable
final class Preferences {
    var myPlatformIDs: Set<String> { didSet { save("myPlatforms", Array(myPlatformIDs).sorted()) } }
    var region: String { didSet { save("region", region) } }
    var rawgKey: String { didSet { save("rawgKey", rawgKey) } }
    var appearanceRaw: String { didSet { save("appearance", appearanceRaw) } }
    var remindMinutesBefore: Int { didSet { save("remindMinutesBefore", remindMinutesBefore) } }
    var addSessionsToCalendar: Bool { didSet { save("addSessionsToCalendar", addSessionsToCalendar) } }
    var releaseAlerts: Bool { didSet { save("releaseAlerts", releaseAlerts) } }
    var sessionReminders: Bool { didSet { save("sessionReminders", sessionReminders) } }

    /// Device-local: whether the welcome sheet has been shown here.
    var hasOnboarded: Bool { didSet { UserDefaults.standard.set(hasOnboarded, forKey: "hasOnboarded") } }

    @ObservationIgnored private var isLoading = false
    @ObservationIgnored private let cloud = NSUbiquitousKeyValueStore.default
    @ObservationIgnored private var observer: NSObjectProtocol?

    /// Steam store regions (prices and availability). CheapShark prices are always USD.
    static let regions: [(code: String, name: String)] = [
        ("US", "United States"), ("CA", "Canada"), ("GB", "United Kingdom"), ("AU", "Australia"),
        ("NZ", "New Zealand"), ("IE", "Ireland"), ("DE", "Germany"), ("FR", "France"), ("ES", "Spain"),
        ("IT", "Italy"), ("NL", "Netherlands"), ("SE", "Sweden"), ("PL", "Poland"), ("BR", "Brazil"),
        ("MX", "Mexico"), ("IN", "India"), ("JP", "Japan"), ("KR", "South Korea"),
    ]

    /// A key baked in at build time via `Config/Secrets.xcconfig`, if any.
    static var bundledRAWGKey: String {
        let key = (Bundle.main.object(forInfoDictionaryKey: "RAWGAPIKey") as? String) ?? ""
        return key.hasPrefix("$(") ? "" : key
    }

    init() {
        let defaults = UserDefaults.standard
        myPlatformIDs = Set(defaults.stringArray(forKey: "myPlatforms") ?? [])
        region = defaults.string(forKey: "region") ?? (Locale.current.region?.identifier ?? "US")
        rawgKey = defaults.string(forKey: "rawgKey") ?? ""
        appearanceRaw = defaults.string(forKey: "appearance") ?? Appearance.system.rawValue
        remindMinutesBefore = defaults.object(forKey: "remindMinutesBefore") as? Int ?? 15
        addSessionsToCalendar = defaults.bool(forKey: "addSessionsToCalendar")
        releaseAlerts = defaults.object(forKey: "releaseAlerts") as? Bool ?? true
        sessionReminders = defaults.object(forKey: "sessionReminders") as? Bool ?? true
        hasOnboarded = defaults.bool(forKey: "hasOnboarded")
        if !Self.regions.contains(where: { $0.code == region }) { region = "US" }

        observer = NotificationCenter.default.addObserver(
            forName: NSUbiquitousKeyValueStore.didChangeExternallyNotification, object: cloud, queue: .main
        ) { [weak self] _ in
            MainActor.assumeIsolated { self?.pullFromCloud() }
        }
        cloud.synchronize()
        pullFromCloud()
    }

    var effectiveRAWGKey: String {
        let typed = rawgKey.trimmingCharacters(in: .whitespacesAndNewlines)
        return typed.isEmpty ? Self.bundledRAWGKey : typed
    }

    var appearance: Appearance {
        get { Appearance(rawValue: appearanceRaw) ?? .system }
        set { appearanceRaw = newValue.rawValue }
    }

    var myPlatforms: [Platform] { Platform.all.filter { myPlatformIDs.contains($0.id) } }

    /// My platforms first, then the rest, each in display order.
    var platformsMineFirst: [Platform] { myPlatforms + Platform.all.filter { !myPlatformIDs.contains($0.id) } }

    func toggle(_ platform: Platform) {
        if myPlatformIDs.contains(platform.id) { myPlatformIDs.remove(platform.id) } else { myPlatformIDs.insert(platform.id) }
    }

    func isMine(_ platform: Platform) -> Bool { myPlatformIDs.contains(platform.id) }
    func isMine(_ platformID: String) -> Bool { myPlatformIDs.contains(platformID) }

    private func save(_ key: String, _ value: Any) {
        guard !isLoading else { return }
        UserDefaults.standard.set(value, forKey: key)
        cloud.set(value, forKey: key)
    }

    private func pullFromCloud() {
        isLoading = true
        defer { isLoading = false }
        if let platforms = cloud.array(forKey: "myPlatforms") as? [String] { myPlatformIDs = Set(platforms) }
        if let region = cloud.string(forKey: "region"), !region.isEmpty { self.region = region }
        if let key = cloud.string(forKey: "rawgKey") { rawgKey = key }
        if let appearance = cloud.string(forKey: "appearance") { appearanceRaw = appearance }
        if let minutes = cloud.object(forKey: "remindMinutesBefore") as? Int { remindMinutesBefore = minutes }
        if cloud.object(forKey: "addSessionsToCalendar") != nil { addSessionsToCalendar = cloud.bool(forKey: "addSessionsToCalendar") }
        if cloud.object(forKey: "releaseAlerts") != nil { releaseAlerts = cloud.bool(forKey: "releaseAlerts") }
        if cloud.object(forKey: "sessionReminders") != nil { sessionReminders = cloud.bool(forKey: "sessionReminders") }
        // Mirror whatever we accepted locally so a later launch without iCloud matches.
        let defaults = UserDefaults.standard
        defaults.set(Array(myPlatformIDs).sorted(), forKey: "myPlatforms")
        defaults.set(region, forKey: "region")
        defaults.set(rawgKey, forKey: "rawgKey")
        defaults.set(appearanceRaw, forKey: "appearance")
    }
}
