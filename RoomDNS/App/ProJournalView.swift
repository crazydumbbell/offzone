import SwiftUI

enum JournalSummaryPolicy {
    static func shouldShowSummary(planned: Int, reflected: Int, kept: Int) -> Bool {
        planned > 0 || reflected > 0 || kept > 0
    }

    static func shouldShowCharacterCount(_ count: Int) -> Bool {
        count >= 1600
    }
}

struct JournalDraftBaseline {
    var plan: ProJournalData.Plan?
    var reflection: ProJournalData.Reflection?

    func hasChanges(plan currentPlan: ProJournalData.Plan, reflection currentReflection: ProJournalData.Reflection) -> Bool {
        (plan != nil && plan != currentPlan) || (reflection != nil && reflection != currentReflection)
    }
}

struct ProJournalView: View {
    let goal: FocusGoal
    @EnvironmentObject private var account: AccountStore
    @Environment(\.dismiss) private var dismiss
    @State private var store = ProJournalStore()
    @State private var week = ProJournalStore.week(.now)
    @State private var intention = ""
    @State private var context = "personal"
    @State private var weekdays: Set<Int> = [2, 3, 4, 5, 6]
    @State private var reflectionDate = Date.now
    @State private var note = ""
    @State private var outcome = ProJournalStore.outcomes[0]
    @State private var error: String?
    @State private var saved = false
    @State private var didLoad = false
    @State private var confirmDelete = false
    @State private var exportText: String?
    @State private var baseline = JournalDraftBaseline()
    @State private var pendingNavigation: (() -> Void)?
    @State private var confirmDiscard = false
    @State private var planFeedback: String?
    @State private var reflectionFeedback: String?
    private static let mondayFirstWeekdays = [2, 3, 4, 5, 6, 7, 1]
    private var planDraft: ProJournalData.Plan { .init(intention: intention, context: context, weekdays: weekdays) }
    private var reflectionDraft: ProJournalData.Reflection { .init(note: note, outcome: outcome) }
    private var hasUnsavedChanges: Bool {
        baseline.hasChanges(plan: planDraft, reflection: reflectionDraft)
    }
    private var weekKey: String { ProJournalStore.key(week) }
    private var hasAccess: Bool { account.hasProAccess }
    private var weeklyEntries: [(String, ProJournalData.Reflection)] {
        let end = ProJournalStore.key(ProJournalStore.calendar.date(byAdding: .day, value: 7, to: week)!)
        return store.data.reflections.filter { $0.key >= weekKey && $0.key < end }.sorted { $0.key > $1.key }
    }

    var body: some View {
        List {
            Section {
                Text("Stored only on this iPhone, excluded from backup. Export before changing phones or deleting Offzone.")
                    .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
            }
            .listRowBackground(Color.roomCard)
            if store.loadFailed {
                Section {
                    Text("Your journal could not be read. It has not been overwritten. Export a copy or delete the local journal to start again.")
                        .foregroundStyle(Color.roomWarning)
                }
                .listRowBackground(Color.roomCard)
            } else {
                Section {
                    HStack {
                        Button { moveWeek(-1) } label: { Image(systemName: "chevron.left").frame(width: 44, height: 44) }
                            .accessibilityLabel("Previous week")
                        Spacer()
                        Text(week, format: .dateTime.month(.abbreviated).day().year()).font(.suit(.headline))
                        Spacer()
                        Button { moveWeek(1) } label: { Image(systemName: "chevron.right").frame(width: 44, height: 44) }
                            .accessibilityLabel("Next week")
                    }.buttonStyle(.borderless)
                        .alignmentGuide(.listRowSeparatorLeading) { _ in 0 }
                    let summary = store.summary(week: week)
                    if JournalSummaryPolicy.shouldShowSummary(planned: summary.planned, reflected: summary.reflected, kept: summary.kept) {
                        LabeledContent("Planned days", value: String(summary.planned))
                        LabeledContent("Days with a reflection", value: String(summary.reflected))
                        LabeledContent("Intention kept (self-reported)", value: String(summary.kept))
                        Text("Your own reflections, not measured focus time. A weekly plan does not schedule app blocking.")
                            .font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                    }
                } header: { sectionHeader("Weekly overview") }
                .listRowBackground(Color.roomCard)

                if hasAccess {
                Section {
                    TextField("What do you want to make room for?", text: $intention, axis: .vertical)
                        .lineLimit(2...4).onChange(of: intention) { _, value in intention = String(value.prefix(160)); saved = false }
                    Picker("Context", selection: $context) {
                        ForEach(FocusGoal.allCases) { item in Text(roomString(item.title)).tag(item.rawValue) }
                    }
                    ScrollView(.horizontal) {
                        HStack(spacing: 4) {
                            ForEach(Self.mondayFirstWeekdays, id: \.self) { day in
                                Button {
                                    if weekdays.contains(day) { weekdays.remove(day) } else { weekdays.insert(day) }
                                } label: {
                                    Text(ProJournalStore.calendar.shortWeekdaySymbols[day - 1])
                                        .font(.suit(.subheadline, weight: .medium)).foregroundStyle(Color.roomInk).frame(minWidth: 44, minHeight: 44)
                                        .background(weekdays.contains(day) ? Color.roomMint : Color.roomCard)
                                        .clipShape(RoundedRectangle(cornerRadius: 16))
                                        .overlay {
                                            RoundedRectangle(cornerRadius: 16)
                                                .strokeBorder(weekdays.contains(day) ? Color.roomAccent : Color.roomOutline,
                                                              lineWidth: weekdays.contains(day) ? 1.5 : 1)
                                        }
                                }
                                .buttonStyle(.borderless)
                                .accessibilityLabel(ProJournalStore.calendar.weekdaySymbols[day - 1])
                                .accessibilityAddTraits(weekdays.contains(day) ? .isSelected : [])
                            }
                        }
                    }
                    Button("Save weekly intention") {
                        perform(section: "plan") {
                            try store.setPlan(.init(intention: intention.trimmingCharacters(in: .whitespacesAndNewlines), context: context, weekdays: weekdays), week: week, hasAccess: account.hasProAccess)
                            baseline.plan = planDraft
                        }
                    }.disabled(intention.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || weekdays.isEmpty)
                    if let planFeedback { Text(planFeedback).font(.suit(.subheadline)).accessibilityAddTraits(.updatesFrequently) }
                    if store.data.plans[weekKey] != nil {
                        Button("Delete this plan", role: .destructive) {
                            perform { try store.deletePlan(week: week); loadPlan() }
                        }
                    }
                } header: { sectionHeader("Weekly intention") }
                .listRowBackground(Color.roomCard)

                Section {
                    DatePicker("Reflection date", selection: Binding(get: { reflectionDate }, set: { date in
                        let targetWeek = ProJournalStore.week(date)
                        let needsDiscard = targetWeek != week
                            ? hasUnsavedChanges
                            : (baseline.reflection != nil && baseline.reflection != reflectionDraft)
                        navigate(confirmWhen: needsDiscard) {
                            reflectionDate = date
                            if targetWeek != week {
                                week = targetWeek
                                loadPlan()
                            }
                            loadReflection()
                        }
                    }), in: ...Date.now, displayedComponents: .date)
                    .environment(\.calendar, ProJournalStore.calendar)
                    Picker("How did it go?", selection: $outcome) {
                        ForEach(ProJournalStore.outcomes, id: \.self) { Text(roomString(ProJournalStore.outcomeKey($0))).tag($0) }
                    }
                    TextField("What helped? What would you change?", text: $note, axis: .vertical)
                        .lineLimit(3...8)
                        .onChange(of: note) { _, value in note = String(value.prefix(2000)); saved = false }
                    if JournalSummaryPolicy.shouldShowCharacterCount(note.count) {
                        Text(roomString("%d / 2000 characters", note.count)).font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                    }
                    Button("Save reflection") {
                        perform(section: "reflection") {
                            try store.reflect(.init(note: note.trimmingCharacters(in: .whitespacesAndNewlines), outcome: outcome), date: reflectionDate, hasAccess: account.hasProAccess)
                            baseline.reflection = reflectionDraft
                        }
                    }.disabled(note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    if let reflectionFeedback { Text(reflectionFeedback).font(.suit(.subheadline)).accessibilityAddTraits(.updatesFrequently) }
                } header: { sectionHeader("Daily reflection") }
                .listRowBackground(Color.roomCard)
                } else if let plan = store.data.plans[weekKey] {
                    Section {
                        Text(plan.intention).textSelection(.enabled)
                        Text(roomString(FocusGoal(rawValue: plan.context)?.title ?? plan.context))
                        Text(Self.mondayFirstWeekdays.filter { plan.weekdays.contains($0) }.map { ProJournalStore.calendar.shortWeekdaySymbols[$0 - 1] }.joined(separator: ", "))
                        Button("Delete this plan", role: .destructive) {
                            perform { try store.deletePlan(week: week); loadPlan() }
                        }
                    } header: { sectionHeader("Weekly intention") }
                    .listRowBackground(Color.roomCard)
                }

                Section {
                    if weeklyEntries.isEmpty { Text("No reflections this week yet.").foregroundStyle(Color.roomInkSecondary) }
                    ForEach(weeklyEntries, id: \.0) { key, reflection in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(formattedEntryDate(key)).font(.suit(.subheadline)).foregroundStyle(Color.roomInkSecondary)
                            Text(roomString(ProJournalStore.outcomeKey(reflection.outcome))).font(.suit(.headline))
                            Text(reflection.note).textSelection(.enabled)
                            Button("Delete reflection", role: .destructive) {
                                perform {
                                    try store.deleteReflection(key)
                                    if ProJournalStore.key(reflectionDate) == key { loadReflection() }
                                }
                            }.buttonStyle(.borderless).frame(minHeight: 44)
                        }.padding(.vertical, 4)
                    }
                } header: { sectionHeader("This week’s reflections") }
                .listRowBackground(Color.roomCard)
            }
            if let error { Section { Text(error).foregroundStyle(Color.roomWarning) }.listRowBackground(Color.roomCard) }
            if saved { Section { Text("Saved on this iPhone.").foregroundStyle(Color.roomInkSecondary) }.listRowBackground(Color.roomCard) }
            Section {
                if store.loadFailed {
                    ShareLink(item: store.url) { Label("Share original journal file", systemImage: "square.and.arrow.up") }
                } else {
                Button("Prepare journal export") {
                    do { exportText = try store.export(); error = nil }
                    catch { self.error = roomString("The journal could not be exported. Your local data is unchanged.") }
                }
                }
                if let exportText {
                    ShareLink(item: exportText) { Label("Share journal copy", systemImage: "square.and.arrow.up") }
                }
                Button("Delete all local journal data", role: .destructive) { confirmDelete = true }
            } header: { sectionHeader("Your data") }
            .listRowBackground(Color.roomCard)
        }
        .font(.suit(.body))
        .listStyle(.insetGrouped).scrollContentBackground(.hidden).background(Color.roomCanvas)
        .tint(Color.roomAction)
        .navigationTitle("Plans & reflections").navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) {
            Button("Done") { navigate { loadPlan(); loadReflection(); dismiss() } }
        } }
        .interactiveDismissDisabled(hasUnsavedChanges)
        .onChange(of: planDraft) { _, _ in planFeedback = nil }
        .onChange(of: reflectionDraft) { _, _ in reflectionFeedback = nil }
        .confirmationDialog("Discard unsaved journal changes?", isPresented: $confirmDiscard, titleVisibility: .visible) {
            Button("Discard changes", role: .destructive) {
                let action = pendingNavigation
                pendingNavigation = nil
                action?()
            }
            Button("Continue editing", role: .cancel) { pendingNavigation = nil }
        }
        .confirmationDialog("Delete all plans and reflections from this iPhone? This cannot be undone.", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete all local journal data", role: .destructive) {
                perform { try store.deleteAll(); exportText = nil; loadPlan(); loadReflection() }
            }
        }
        .onAppear {
            guard !didLoad else { return }
            loadPlan(); loadReflection(); didLoad = true
        }
    }
    private func moveWeek(_ amount: Int) {
        navigate {
            week = ProJournalStore.calendar.date(byAdding: .weekOfYear, value: amount, to: week)!
            reflectionDate = min(week, Date.now)
            loadPlan(); loadReflection(); saved = false
        }
    }
    private func navigate(confirmWhen shouldConfirm: Bool? = nil, _ action: @escaping () -> Void) {
        if shouldConfirm ?? hasUnsavedChanges { pendingNavigation = action; confirmDiscard = true }
        else { action() }
    }
    private func sectionHeader(_ title: LocalizedStringKey) -> some View {
        Text(title)
            .font(.suit(.subheadline, weight: .semibold))
            .foregroundStyle(Color.roomInkSecondary)
            .textCase(nil)
    }
    private func formattedEntryDate(_ key: String) -> String {
        guard let date = ProJournalStore.date(fromKey: key) else { return key }
        return date.formatted(.dateTime.weekday(.abbreviated).month(.abbreviated).day().year())
    }
    private func loadPlan() {
        let plan = store.data.plans[weekKey]
        intention = plan?.intention ?? ""; context = plan?.context ?? goal.rawValue
        weekdays = plan?.weekdays ?? [2, 3, 4, 5, 6]
        baseline.plan = planDraft; planFeedback = nil
    }
    private func loadReflection() {
        let reflection = store.data.reflections[ProJournalStore.key(reflectionDate)]
        note = reflection?.note ?? ""; outcome = reflection?.outcome ?? ProJournalStore.outcomes[0]
        baseline.reflection = reflectionDraft; reflectionFeedback = nil
    }
    private func perform(section: String? = nil, _ action: () throws -> Void) {
        do { try action(); error = nil; exportText = nil; saved = true }
        catch ProJournalStore.Failure.accessRequired {
            error = roomString("An active Pro subscription is required to save new changes.")
        } catch {
            self.error = roomString("The journal could not be saved. Your previous data is unchanged. Try again.")
        }
        if section == "plan" {
            planFeedback = error ?? roomString("Saved on this iPhone.")
            reflectionFeedback = nil; saved = false; error = nil
        } else if section == "reflection" {
            reflectionFeedback = error ?? roomString("Saved on this iPhone.")
            planFeedback = nil; saved = false; error = nil
        } else {
            planFeedback = nil; reflectionFeedback = nil
        }
    }
}
