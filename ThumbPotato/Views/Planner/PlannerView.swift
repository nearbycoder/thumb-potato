import SwiftUI
import SwiftData

/// A week of planned play sessions plus the Release Radar for followed games.
struct PlannerView: View {
    @Environment(\.modelContext) private var context
    @Environment(AppModel.self) private var app
    @Environment(\.layout) private var layout
    @Query(sort: \PlaySession.startsAt) private var sessions: [PlaySession]
    @Query(filter: #Predicate<SavedGame> { $0.isFollowing }) private var followed: [SavedGame]

    @State private var weekStart = Calendar.current.dateInterval(of: .weekOfYear, for: .now)?.start ?? .now
    @State private var selectedDay = Calendar.current.startOfDay(for: .now)

    private var days: [Date] {
        (0..<7).compactMap { Calendar.current.date(byAdding: .day, value: $0, to: weekStart) }
    }

    /// Followed games that aren't out yet, soonest first.
    private var radar: [SavedGame] {
        followed.filter(\.isUpcoming).sorted { ($0.releaseDate ?? .distantFuture) < ($1.releaseDate ?? .distantFuture) }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 24) {
                weekStrip.padding(.horizontal, layout.margin)
                dayAgenda.padding(.horizontal, layout.margin)
                radarSection.padding(.horizontal, layout.margin)
            }
            .padding(.vertical)
        }
        .appBackground()
        .navigationTitle("Planner")
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button { app.plan(nil) } label: { Label("Plan a Session", systemImage: "plus") }
            }
        }
    }

    private var weekStrip: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                Text(weekTitle).font(.sectionTitle).foregroundStyle(Theme.textPrimary)
                if !Calendar.current.isDate(weekStart, equalTo: .now, toGranularity: .weekOfYear) {
                    Button("Today") {
                        weekStart = Calendar.current.dateInterval(of: .weekOfYear, for: .now)?.start ?? .now
                        selectedDay = Calendar.current.startOfDay(for: .now)
                    }
                    .font(.seeAll)
                    .buttonStyle(.plain)
                    .foregroundStyle(Theme.accent)
                }
                Spacer()
                HStack(spacing: 4) {
                    Button { shiftWeek(-1) } label: { Image(systemName: "chevron.left") }
                        .accessibilityLabel("Previous week")
                    Button { shiftWeek(1) } label: { Image(systemName: "chevron.right") }
                        .accessibilityLabel("Next week")
                }
                .buttonStyle(.secondary)
            }

            HStack(spacing: 4) {
                ForEach(days, id: \.self) { day in
                    let isSelected = Calendar.current.isDate(day, inSameDayAs: selectedDay)
                    let isToday = Calendar.current.isDateInToday(day)
                    let count = sessions(on: day).count
                    let releaseCount = releases(on: day).count
                    Button {
                        withAnimation(.snappy) { selectedDay = day }
                    } label: {
                        VStack(spacing: 6) {
                            Text(day.formatted(.dateTime.weekday(.narrow)))
                                .font(.caption2.weight(.medium))
                                .foregroundStyle(Theme.textSecondary)
                            Text(day.formatted(.dateTime.day()))
                                .font(.callout.weight(.semibold))
                                .foregroundStyle(isSelected ? Theme.onAccent : (isToday ? Theme.accent : Theme.textPrimary))
                                .frame(width: 34, height: 34)
                                .background(isSelected ? Theme.accent : .clear, in: Circle())
                            HStack(spacing: 3) {
                                ForEach(0..<min(count, 3), id: \.self) { _ in Circle().fill(Theme.accent).frame(width: 4) }
                                ForEach(0..<min(releaseCount, 3), id: \.self) { _ in Circle().fill(Theme.info).frame(width: 4) }
                            }
                            .frame(height: 4)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(day.formatted(date: .complete, time: .omitted))
                    .accessibilityValue("\(count) planned\(releaseCount > 0 ? ", \(releaseCount) releasing" : "")")
                    .accessibilityAddTraits(isSelected ? .isSelected : [])
                }
            }
            .card(padding: 8)
        }
    }

    private var dayAgenda: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: dayTitle, subtitle: selectedDay.formatted(.dateTime.weekday(.wide).month(.wide).day()))
            let todays = sessions(on: selectedDay)
            let releasing = releases(on: selectedDay)
            if todays.isEmpty && releasing.isEmpty {
                HStack(spacing: 14) {
                    Image(systemName: "calendar.badge.plus")
                        .font(.title2)
                        .foregroundStyle(Theme.textTertiary)
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Nothing planned").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.textPrimary)
                        Text("Pick something from your backlog and set a time.").font(.footnote).foregroundStyle(Theme.textSecondary)
                    }
                    Spacer()
                    Button("Plan") { app.plan(nil) }.buttonStyle(.secondary)
                }
                .card()
            } else {
                ForEach(todays) { session in SessionCard(session: session) }
                ForEach(releasing) { game in RadarRow(game: game) }
            }
        }
    }

    private var radarSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            SectionHeader(title: "Release Radar",
                          subtitle: radar.isEmpty ? "Add an upcoming game to your backlog to follow its release" : "Upcoming games you follow")
            if radar.isEmpty {
                Button {
                    app.selection = .tab(.tonight)
                } label: {
                    Label("Find upcoming games", systemImage: "sparkles")
                }
                .buttonStyle(.secondary)
            }
            ForEach(radar) { game in RadarRow(game: game, showsCountdown: true) }
        }
    }

    private func sessions(on day: Date) -> [PlaySession] {
        sessions.filter { Calendar.current.isDate($0.startsAt, inSameDayAs: day) }
    }

    /// Exact-day releases only; "2027" doesn't land on Jan 1.
    private func releases(on day: Date) -> [SavedGame] {
        radar.filter { $0.releaseDatePrecision == .day && $0.releaseDate.map { Calendar.current.isDate($0, inSameDayAs: day) } == true }
    }

    private func shiftWeek(_ delta: Int) {
        weekStart = Calendar.current.date(byAdding: .weekOfYear, value: delta, to: weekStart) ?? weekStart
        selectedDay = weekStart
    }

    private var weekTitle: String {
        guard let end = days.last else { return "" }
        return "\(weekStart.formatted(.dateTime.month(.abbreviated).day())) – \(end.formatted(.dateTime.month(.abbreviated).day()))"
    }

    private var dayTitle: String {
        if Calendar.current.isDateInToday(selectedDay) { return "Today" }
        if Calendar.current.isDateInTomorrow(selectedDay) { return "Tomorrow" }
        return selectedDay.formatted(.dateTime.weekday(.wide))
    }
}

struct SessionCard: View {
    @Bindable var session: PlaySession
    @Environment(\.modelContext) private var context
    @Environment(AppModel.self) private var app

    var body: some View {
        HStack(spacing: 14) {
            RemoteImage(url: session.coverURL, maxPixel: 240, placeholderTitle: nil)
                .frame(width: 60, height: 80)
                .artwork(radius: 8)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text(session.gameName).font(.headline).foregroundStyle(Theme.textPrimary)
                    .strikethrough(session.isDone)
                    .lineLimit(2)
                Text("\(session.startsAt.formatted(date: .omitted, time: .shortened)) – \(session.endsAt.formatted(date: .omitted, time: .shortened))")
                    .font(.footnote).foregroundStyle(Theme.textSecondary)
                if !session.note.isEmpty {
                    Text(session.note).font(.caption).foregroundStyle(Theme.textSecondary).lineLimit(2)
                }
                HStack(spacing: 6) {
                    if let platform = session.platform { Pill(text: platform.shortName, dot: platform.color) }
                    if session.hasReminder { Pill(text: "Reminder", systemImage: "bell") }
                    if session.calendarEventID != nil { Pill(text: "Calendar", systemImage: "calendar") }
                }
            }
            Spacer(minLength: 0)
            Button {
                session.isDone.toggle()
                if session.isDone { Reminders.cancel(session) }
            } label: {
                Image(systemName: session.isDone ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 26))
                    .foregroundStyle(session.isDone ? Theme.accent : Theme.textTertiary)
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Done")
            .accessibilityValue(session.isDone ? "Done" : "Not done")
            .sensoryFeedback(.success, trigger: session.isDone)
        }
        .card()
        .contextMenu {
            if let ref = session.ref {
                Button("Open Game", systemImage: "info.circle") { app.open(ref) }
            }
            Button("Delete", systemImage: "trash", role: .destructive) {
                Reminders.cancel(session)
                context.delete(session)
            }
        }
    }
}

/// A followed game's release: date ("TBA 2027" when coarse) and countdown.
struct RadarRow: View {
    var game: SavedGame
    var showsCountdown = false

    var body: some View {
        NavigationLink(value: Route.game(game.ref, preview: game.asGame)) {
            HStack(spacing: 12) {
                RemoteImage(url: game.coverURL, maxPixel: 200, placeholderTitle: nil)
                    .frame(width: 48, height: 64)
                    .artwork(radius: 6)
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 3) {
                    Text(game.name).font(.subheadline.weight(.semibold)).foregroundStyle(Theme.textPrimary).lineLimit(2)
                    if let date = game.releaseDate {
                        Text(ReleaseDateFormat.radarLabel(date, precision: game.releaseDatePrecision))
                            .font(.footnote).foregroundStyle(Theme.textSecondary)
                    }
                    PlatformDots(platforms: game.platforms)
                }
                Spacer()
                if showsCountdown, let date = game.releaseDate,
                   let countdown = ReleaseDateFormat.countdown(date, precision: game.releaseDatePrecision) {
                    Text(countdown)
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.info)
                        .padding(.horizontal, 8).padding(.vertical, 4)
                        .background(Theme.info.opacity(0.14), in: Capsule())
                } else if !showsCountdown {
                    Pill(text: "Out today", systemImage: "sparkles", tint: Theme.info.opacity(0.14), foreground: Theme.info)
                }
                Image(systemName: "chevron.right").font(.caption.weight(.semibold)).foregroundStyle(Theme.textTertiary)
            }
            .card(padding: 12)
        }
        .buttonStyle(.plain)
    }
}

/// Sheet for scheduling a play session.
struct PlanSessionSheet: View {
    var initialGame: Game?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var context
    @Environment(Preferences.self) private var prefs
    @Query(sort: \SavedGame.updatedAt, order: .reverse) private var saved: [SavedGame]

    @State private var pickedKey: String?
    @State private var startsAt = PlanSessionSheet.defaultStart()
    @State private var duration = 120
    @State private var platformID: String?
    @State private var note = ""
    @State private var remind = true
    @State private var addToCalendar = false
    @State private var saving = false

    static let durations = [30, 60, 120, 180]

    private var candidates: [Game] {
        // Playing first, then Want to Play.
        var list = saved.filter { $0.status == .playing }.map(\.asGame)
            + saved.filter { $0.status == .queued }.map(\.asGame)
        if let initialGame, !list.contains(initialGame) { list.insert(initialGame, at: 0) }
        return list
    }

    private var picked: Game? { candidates.first { $0.ref.key == pickedKey } }

    var body: some View {
        NavigationStack {
            Form {
                Section("Game") {
                    if candidates.isEmpty {
                        Text("Add something to your backlog first, then plan it here.").foregroundStyle(.secondary)
                    } else {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 12) {
                                ForEach(candidates) { game in
                                    Button {
                                        pick(game)
                                    } label: {
                                        CoverCard(game: game, width: 78, showsCaption: false)
                                            .overlay(RoundedRectangle(cornerRadius: Theme.coverRadius, style: .continuous)
                                                .strokeBorder(Theme.accent, lineWidth: pickedKey == game.ref.key ? 3 : 0))
                                            .opacity(pickedKey == nil || pickedKey == game.ref.key ? 1 : 0.5)
                                    }
                                    .buttonStyle(.plain)
                                    .accessibilityLabel(game.name)
                                    .accessibilityAddTraits(pickedKey == game.ref.key ? .isSelected : [])
                                }
                            }
                            .padding(.vertical, 6)
                        }
                        if let picked {
                            Text(picked.name).font(.headline)
                            if !picked.platformList.isEmpty {
                                Picker("Platform", selection: $platformID) {
                                    Text("Not set").tag(String?.none)
                                    ForEach(picked.platformList) { Text($0.name).tag(String?.some($0.id)) }
                                }
                            }
                        }
                    }
                }
                Section("When") {
                    // Wraps instead of scrolling so every shortcut is visible on narrow screens.
                    FlowLayout(spacing: 8) {
                        ForEach(quickTimes, id: \.0) { label, date in
                            Button(label) { startsAt = date }.buttonStyle(.bordered).tint(Theme.accent)
                        }
                    }
                    DatePicker("Starts", selection: $startsAt, in: Date.now.addingTimeInterval(-3600)...)
                    FlowLayout(spacing: 8) {
                        ForEach(Self.durations, id: \.self) { minutes in
                            Button(minutes.durationLabel) { duration = minutes }
                                .buttonStyle(.bordered)
                                .tint(duration == minutes ? Theme.accent : .secondary)
                                .accessibilityAddTraits(duration == minutes ? .isSelected : [])
                        }
                    }
                    Stepper(value: $duration, in: 15...600, step: 15) {
                        LabeledContent("Length", value: duration.durationLabel)
                    }
                }
                Section("Options") {
                    TextField("Note", text: $note, prompt: Text("Add a note"), axis: .vertical)
                    Toggle("Remind me \(prefs.remindMinutesBefore) minutes before", isOn: $remind)
                    Toggle("Add to Calendar", isOn: $addToCalendar)
                }
            }
            .formStyle(.grouped)
            .navigationTitle("Plan a Session")
            .inlineTitle()
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") { Task { await save() } }
                        .disabled(picked == nil || saving)
                }
            }
            .onAppear {
                if let first = initialGame ?? candidates.first { pick(first) }
                addToCalendar = prefs.addSessionsToCalendar
                remind = prefs.sessionReminders
            }
        }
        #if os(macOS)
        .frame(minWidth: 500, minHeight: 600)
        #endif
    }

    private func pick(_ game: Game) {
        pickedKey = game.ref.key
        let savedGame = saved.first { $0.key == game.ref.key }
        platformID = savedGame?.playingOn ?? game.platforms.first(where: prefs.isMine) ?? game.platforms.first
    }

    private var quickTimes: [(String, Date)] {
        let cal = Calendar.current
        let tonight = cal.date(bySettingHour: 20, minute: 0, second: 0, of: .now)!
        func next(weekday: Int, hour: Int) -> Date {
            cal.nextDate(after: .now, matching: DateComponents(hour: hour, minute: 0, weekday: weekday), matchingPolicy: .nextTime) ?? tonight
        }
        var list: [(String, Date)] = []
        if tonight > .now { list.append(("Tonight 8 PM", tonight)) }
        list.append(("Saturday 2 PM", next(weekday: 7, hour: 14)))
        list.append(("Sunday 7 PM", next(weekday: 1, hour: 19)))
        return list
    }

    static func defaultStart() -> Date {
        let cal = Calendar.current
        let tonight = cal.date(bySettingHour: 20, minute: 0, second: 0, of: .now)!
        return tonight > .now ? tonight : cal.date(byAdding: .hour, value: 1, to: cal.date(bySetting: .minute, value: 0, of: .now)!)!
    }

    private func save() async {
        guard let picked else { return }
        saving = true
        if Library.saved(picked.ref.key, in: context) == nil { Library.save(picked, status: .queued, in: context) }
        let session = PlaySession(gameKey: picked.ref.key, gameName: picked.name, coverURL: picked.boxArtURL,
                                  platformID: platformID, startsAt: startsAt, durationMinutes: duration,
                                  note: note, hasReminder: remind)
        context.insert(session)
        try? context.save()
        if remind { await Reminders.schedule(session, minutesBefore: prefs.remindMinutesBefore) }
        if addToCalendar { session.calendarEventID = await CalendarBridge.add(session) }
        saving = false
        dismiss()
    }
}
