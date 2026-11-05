import SwiftUI
import SwiftData

struct SettingsView: View {
    @Environment(Preferences.self) private var prefs
    @Environment(Catalog.self) private var catalog
    @Environment(SyncMonitor.self) private var sync
    @Environment(\.modelContext) private var context
    @Query private var saved: [SavedGame]
    @Query private var sessions: [PlaySession]

    @State private var keyDraft = ""
    @State private var keyStatus: KeyStatus = .idle
    @State private var catalogUpdated: Date?
    @State private var exportDocument: BackupDocument?
    @State private var isExporting = false
    @State private var isImporting = false
    @State private var transferMessage: String?

    enum KeyStatus: Equatable { case idle, checking, ok, failed(String) }

    var body: some View {
        @Bindable var prefs = prefs
        Form {
            Section {
                BacklogStatsCard(stats: BacklogStats(games: saved))
                    .listRowInsets(EdgeInsets())
                    .listRowBackground(Color.clear)
            }

            Section {
                // Wide minimum so "Nintendo Switch 2" never truncates; drops to one column first.
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 200), spacing: 8)], spacing: 8) {
                    ForEach(Platform.all) { platform in
                        Button {
                            withAnimation(.snappy) { prefs.toggle(platform) }
                            catalog.reset()
                        } label: {
                            PlatformChip(platform: platform, isSelected: prefs.isMine(platform))
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(prefs.isMine(platform) ? .isSelected : [])
                    }
                }
                .padding(.vertical, 4)
                .listRowInsets(EdgeInsets(top: 8, leading: 8, bottom: 8, trailing: 8))
            } header: {
                Text("My Platforms")
            } footer: {
                Text("Tonight, Moods and Shuffle are built around your platforms, and their stores come first in Where to Play.")
            }

            Section {
                Picker("Region", selection: $prefs.region) {
                    ForEach(Preferences.regions, id: \.code) { Text($0.name).tag($0.code) }
                }
                .onChange(of: prefs.region) { catalog.reset() }
            } header: {
                Text("Region")
            } footer: {
                Text("Used for Steam prices and availability. PC prices from CheapShark are in US dollars.")
            }

            Section {
                HStack {
                    Image(systemName: catalog.hasRAWG ? "checkmark.seal.fill" : "info.circle")
                        .foregroundStyle(catalog.hasRAWG ? Theme.accent : Theme.textSecondary)
                    Text(catalog.hasRAWG ? "RAWG connected: server-side browsing and search" : "Key-less: Thumb Potato catalog, Wikipedia, Steam and CheapShark")
                }
                LabeledContent("Catalog updated", value: catalogUpdated.map { $0.formatted(date: .abbreviated, time: .omitted) } ?? "–")
                SecureField("RAWG API key", text: $keyDraft)
                    .textContentType(.password)
                    .onSubmit { Task { await testKey() } }
                HStack {
                    Button("Save and Test") { Task { await testKey() } }
                        .disabled(keyDraft.trimmingCharacters(in: .whitespaces).isEmpty || keyStatus == .checking)
                    if !prefs.rawgKey.isEmpty {
                        Button("Remove Key", role: .destructive) {
                            prefs.rawgKey = ""
                            keyDraft = ""
                            keyStatus = .idle
                            catalog.reset()
                        }
                    }
                    Spacer(minLength: 0)
                    switch keyStatus {
                    case .idle, .failed: EmptyView()
                    case .checking: ProgressView().controlSize(.small)
                    case .ok: Image(systemName: "checkmark.circle.fill").foregroundStyle(Theme.accent).accessibilityLabel("Connected")
                    }
                }
                .buttonStyle(.borderless)
                if case .failed(let message) = keyStatus {
                    Text(message).foregroundStyle(Theme.danger).font(.footnote)
                }
                Link("Get a free RAWG API key", destination: URL(string: "https://rawg.io/apidocs")!)
            } header: {
                Text("Data Sources")
            } footer: {
                Text("Optional. The key syncs to your other devices through iCloud. Without one, Thumb Potato uses its daily catalog built from Wikidata, Wikipedia and Steam.")
            }

            Section {
                Toggle("Release-day alerts", isOn: $prefs.releaseAlerts)
                    .onChange(of: prefs.releaseAlerts) { _, on in if on { Task { await Reminders.requestPermission() } } }
                Toggle("Session reminders", isOn: $prefs.sessionReminders)
                    .onChange(of: prefs.sessionReminders) { _, on in if on { Task { await Reminders.requestPermission() } } }
                Picker("Remind me", selection: $prefs.remindMinutesBefore) {
                    Text("At start time").tag(0)
                    Text("5 minutes before").tag(5)
                    Text("15 minutes before").tag(15)
                    Text("30 minutes before").tag(30)
                    Text("1 hour before").tag(60)
                }
                .disabled(!prefs.sessionReminders)
                Toggle("Add sessions to Calendar", isOn: $prefs.addSessionsToCalendar)
            } header: {
                Text("Notifications")
            } footer: {
                Text("Release-day alerts arrive at 9 AM for followed games with an exact release date.")
            }

            Section {
                let status = sync.status
                HStack(alignment: .top, spacing: 12) {
                    Image(systemName: status.symbol)
                        .font(.title3)
                        .foregroundStyle(status.healthy ? Theme.accent : Theme.textSecondary)
                        .symbolEffect(.pulse, isActive: sync.isSyncing)
                        .frame(width: 28)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(status.title).font(.body.weight(.medium))
                        if let detail = status.detail {
                            Text(detail).font(.footnote).foregroundStyle(Theme.textSecondary)
                        }
                    }
                }
                .accessibilityElement(children: .combine)
                LabeledContent("Games", value: "\(saved.count)")
                LabeledContent("Planned sessions", value: "\(sessions.count)")
                Button("Merge Duplicates") { Library.dedupe(in: context) }
            } header: {
                Text("iCloud Sync")
            } footer: {
                Text("Your backlog, ratings, hours and sessions sync automatically between every iPhone, iPad and Mac signed in to the same iCloud account.")
            }
            .task { await sync.refreshAccount() }

            Section {
                Button("Export Backlog…", systemImage: "square.and.arrow.up") {
                    do {
                        exportDocument = BackupDocument(data: try Backup.export(games: saved, sessions: sessions))
                        isExporting = true
                    } catch {
                        transferMessage = "Couldn't export: \(error.localizedDescription)"
                    }
                }
                Button("Import Backlog…", systemImage: "square.and.arrow.down") { isImporting = true }
                if let transferMessage {
                    Text(transferMessage).font(.footnote).foregroundStyle(Theme.textSecondary)
                }
            } header: {
                Text("Android & Backups")
            } footer: {
                Text("Export a backup file to move your backlog to the Thumb Potato Android app, or import one exported from Android. Importing merges: nothing is deleted, and newer changes win.")
            }
            .fileExporter(isPresented: $isExporting, document: exportDocument, contentType: .json,
                          defaultFilename: "Thumb Potato Backlog") { result in
                if case .success = result { transferMessage = "Exported \(saved.count) games and \(sessions.count) sessions." }
            }
            .fileImporter(isPresented: $isImporting, allowedContentTypes: [.json]) { result in
                importBackup(result)
            }

            Section("Appearance") {
                Picker("Appearance", selection: Binding(get: { prefs.appearance }, set: { prefs.appearance = $0 })) {
                    ForEach(Appearance.allCases) { Text($0.label).tag($0) }
                }
                .pickerStyle(.segmented)
                .labelsHidden()
            }

            Section {
                NavigationLink {
                    AboutView()
                } label: {
                    HStack(spacing: 12) {
                        BrandMark(size: 40)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("About Thumb Potato").font(.headline)
                            Text("Version \(Bundle.main.object(forInfoDictionaryKey: "CFBundleShortVersionString") as? String ?? "1.0")")
                                .font(.caption).foregroundStyle(.secondary)
                        }
                    }
                }
                Link("Website", destination: URL(string: "https://nearbycoder.github.io/thumb-potato/")!)
                Link("Privacy", destination: URL(string: "https://nearbycoder.github.io/thumb-potato/privacy.html")!)
            }
        }
        .formStyle(.grouped)
        .tint(Theme.accent)
        .scrollContentBackground(.hidden)
        .appBackground()
        .navigationTitle("Settings")
        .onAppear { keyDraft = prefs.rawgKey }
        .task {
            if let stamp = await catalog.catalogIndex()?.updated { catalogUpdated = Backup.parseISO(stamp) }
        }
    }

    private func importBackup(_ result: Result<URL, Error>) {
        do {
            let url = try result.get()
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            let summary = try Backup.merge(try Data(contentsOf: url), into: context)
            Library.dedupe(in: context)
            transferMessage = summary.message
        } catch {
            transferMessage = "Couldn't import that file. Choose a backup exported from Thumb Potato."
        }
    }

    private func testKey() async {
        let key = keyDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        keyStatus = .checking
        do {
            try await RAWGClient(key: key).validate()
            prefs.rawgKey = key
            catalog.reset()
            keyStatus = .ok
        } catch {
            keyStatus = .failed(error.localizedDescription)
        }
    }
}

/// Spud, credits and the not-affiliated notice.
struct AboutView: View {
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Image("Spud")
                    .resizable()
                    .frame(width: 112, height: 112)
                    .clipShape(RoundedRectangle(cornerRadius: 26, style: .continuous))
                    .softShadow(radius: 18, y: 8)
                    .accessibilityHidden(true)
                VStack(spacing: 6) {
                    Text("Thumb Potato").font(.title.bold()).foregroundStyle(Theme.textPrimary)
                    Text("Meet Spud, the potato-shaped thumb pad on our controller. Spud keeps your backlog, watches release dates and helps you pick what to play next.")
                        .font(.callout)
                        .foregroundStyle(Theme.textSecondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: 440)
                }

                VStack(alignment: .leading, spacing: 12) {
                    SectionHeader(title: "Credits")
                    credit("Wikipedia", "Box art and descriptions (CC BY-SA for text).")
                    credit("Wikidata", "Platforms, release dates and credits (CC0).")
                    credit("Steam", "Library art, screenshots, trailers, reviews and Steam Deck status.")
                    credit("CheapShark", "PC prices and deals.")
                    credit("RAWG", "Optional catalog, with your own key.")
                }
                .card()
                .frame(maxWidth: 560)

                Text("Thumb Potato isn't affiliated with Nintendo, Sony, Microsoft, Valve or any store; platform and store names appear as text and monograms, not logos.")
                    .font(.footnote)
                    .foregroundStyle(Theme.textTertiary)
                    .multilineTextAlignment(.center)
                    .frame(maxWidth: 480)

                HStack(spacing: 16) {
                    Link("GitHub", destination: URL(string: "https://github.com/nearbycoder/thumb-potato")!)
                    Link("Couch Potato", destination: URL(string: "https://github.com/nearbycoder/couch-potato")!)
                }
                .font(.subheadline.weight(.medium))
                .tint(Theme.accent)
            }
            .padding(24)
            .frame(maxWidth: .infinity)
        }
        .appBackground()
        .navigationTitle("About")
        .inlineTitle()
    }

    private func credit(_ name: String, _ detail: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(name).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.textPrimary)
            Text(detail).font(.footnote).foregroundStyle(Theme.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityElement(children: .combine)
    }
}

/// First launch: welcome, pick your platforms, notifications. Skippable.
struct OnboardingView: View {
    @Environment(Preferences.self) private var prefs
    @Environment(Catalog.self) private var catalog
    @Environment(\.dismiss) private var dismiss
    @State private var page = 0

    var body: some View {
        VStack(spacing: 24) {
            HStack {
                Spacer()
                if page < 2 {
                    Button("Skip") { finish() }
                        .buttonStyle(.plain)
                        .foregroundStyle(Theme.textSecondary)
                }
            }
            Spacer(minLength: 0)
            switch page {
            case 0:
                BrandMark(size: 96)
                VStack(spacing: 10) {
                    Text("Welcome to Thumb Potato").font(.title.bold()).foregroundStyle(Theme.textPrimary).multilineTextAlignment(.center)
                    Text("Find games across Switch, PlayStation, Xbox, PC, Mac and mobile, keep one backlog, track release dates and plan your play sessions.")
                        .font(.body).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
                        .frame(maxWidth: 420)
                }
                Button("Continue") { withAnimation(.snappy) { page = 1 } }.buttonStyle(.primary)
            case 1:
                VStack(spacing: 8) {
                    Text("Your platforms").font(.title.bold()).foregroundStyle(Theme.textPrimary).multilineTextAlignment(.center)
                    Text("Pick what you play on. You can change this any time in Settings.")
                        .font(.body).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
                        .frame(maxWidth: 420)
                }
                ScrollView {
                    LazyVGrid(columns: [GridItem(.adaptive(minimum: 200), spacing: 8)], spacing: 8) {
                        ForEach(Platform.all) { platform in
                            Button { withAnimation(.snappy) { prefs.toggle(platform) } } label: {
                                PlatformChip(platform: platform, isSelected: prefs.isMine(platform))
                            }
                            .buttonStyle(.plain)
                            .accessibilityAddTraits(prefs.isMine(platform) ? .isSelected : [])
                        }
                    }
                    .frame(maxWidth: 560)
                }
                .scrollBounceBehavior(.basedOnSize)
                Button(prefs.myPlatformIDs.isEmpty ? "Skip for now" : "Continue") {
                    catalog.reset()
                    withAnimation(.snappy) { page = 2 }
                }
                .buttonStyle(.primary)
            default:
                Image(systemName: "bell.badge")
                    .font(.system(size: 56, weight: .light))
                    .foregroundStyle(Theme.accent)
                VStack(spacing: 10) {
                    Text("Never miss a launch").font(.title.bold()).foregroundStyle(Theme.textPrimary).multilineTextAlignment(.center)
                    Text("Get an alert at 9 AM on release day for games you follow, and a reminder before each planned session.")
                        .font(.body).foregroundStyle(Theme.textSecondary).multilineTextAlignment(.center)
                        .frame(maxWidth: 420)
                }
                VStack(spacing: 10) {
                    Button("Turn On Notifications") {
                        // Ask once the sheet is gone; the system prompt can stall behind a dismissing sheet.
                        finish()
                        Task {
                            try? await Task.sleep(for: .milliseconds(600))
                            await Reminders.requestPermission()
                        }
                    }
                    .buttonStyle(.primary)
                    Button("Not Now") {
                        prefs.releaseAlerts = false
                        prefs.sessionReminders = false
                        finish()
                    }
                    .buttonStyle(.plain)
                    .foregroundStyle(Theme.textSecondary)
                }
            }
            Spacer(minLength: 0)
            HStack(spacing: 6) {
                ForEach(0..<3, id: \.self) { i in
                    Capsule().fill(i == page ? Theme.accent : Theme.textTertiary.opacity(0.5))
                        .frame(width: i == page ? 18 : 6, height: 6)
                }
            }
            .accessibilityHidden(true)
        }
        .padding(28)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .appBackground()
        #if os(macOS)
        .frame(width: 580, height: 640)
        #endif
        .interactiveDismissDisabled()
    }

    private func finish() {
        prefs.hasOnboarded = true
        dismiss()
    }
}
