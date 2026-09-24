import SwiftUI

struct ProJournalView: View {
    let goal: FocusGoal
    @EnvironmentObject private var account: AccountStore
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
    @State private var paywall = false
    @State private var confirmDelete = false
    @State private var exportText: String?
    private var weekKey: String { ProJournalStore.key(week) }
    private var hasAccess: Bool { account.hasProAccess }
    private var weeklyEntries: [(String, ProJournalData.Reflection)] {
        let end = ProJournalStore.key(ProJournalStore.calendar.date(byAdding: .day, value: 7, to: week)!)
        return store.data.reflections.filter { $0.key >= weekKey && $0.key < end }.sorted { $0.key > $1.key }
    }

    var body: some View {
        List {
            Section {
                Text("A little intention. A little reflection.")
                    .font(.suit(.title2, weight: .bold))
                Text("Stored only on this iPhone, excluded from backup. Export before changing phones or deleting Offzone.")
                    .font(.suit(.footnote)).foregroundStyle(Color.roomInkSecondary)
                if !hasAccess {
                    Text("Pro lets you write plans and reflections. Your existing entries remain readable, exportable and deletable.")
                    if account.canShowOffer {
                        Button("Explore Pro") { paywall = true }
                    } else {
                        Text("Pro purchases are currently unavailable.").font(.suit(.footnote))
                    }
                }
            }
            if store.loadFailed {
                Section {
                    Text("Your journal could not be read. It has not been overwritten. Export a copy or delete the local journal to start again.")
                        .foregroundStyle(Color.roomWarning)
                }
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
                    let summary = store.summary(week: week)
                    LabeledContent("Planned days", value: String(summary.planned))
                    LabeledContent("Days with a reflection", value: String(summary.reflected))
                    LabeledContent("Intention kept (self-reported)", value: String(summary.kept))
                    Text("Your own reflections, not measured focus time. A weekly plan does not schedule app blocking.")
                        .font(.suit(.footnote)).foregroundStyle(Color.roomInkSecondary)
                } header: { Text("Weekly overview") }

                Section {
                    TextField("What do you want to make room for?", text: $intention, axis: .vertical)
                        .lineLimit(2...4).onChange(of: intention) { _, value in intention = String(value.prefix(160)); saved = false }
                        .disabled(!hasAccess)
                    Picker("Context", selection: $context) {
                        ForEach(FocusGoal.allCases) { item in Text(roomString(item.title)).tag(item.rawValue) }
                    }.disabled(!hasAccess)
                    ScrollView(.horizontal) {
                        HStack(spacing: 4) {
                            ForEach([2, 3, 4, 5, 6, 7, 1], id: \.self) { day in
                                Button {
                                    if weekdays.contains(day) { weekdays.remove(day) } else { weekdays.insert(day) }
                                } label: {
                                    Text(ProJournalStore.calendar.shortWeekdaySymbols[day - 1])
                                        .font(.suit(.caption)).frame(minWidth: 44, minHeight: 44)
                                        .background(weekdays.contains(day) ? Color.roomMint : Color.roomPaper)
                                        .clipShape(RoundedRectangle(cornerRadius: 10))
                                }
                                .buttonStyle(.borderless)
                                .accessibilityLabel(ProJournalStore.calendar.weekdaySymbols[day - 1])
                                .accessibilityAddTraits(weekdays.contains(day) ? .isSelected : [])
                                .disabled(!hasAccess)
                            }
                        }
                    }
                    Button("Save weekly intention") {
                        perform {
                            try store.setPlan(.init(intention: intention.trimmingCharacters(in: .whitespacesAndNewlines), context: context, weekdays: weekdays), week: week, hasAccess: account.hasProAccess)
                        }
                    }.disabled(!hasAccess || intention.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || weekdays.isEmpty)
                    if store.data.plans[weekKey] != nil {
                        Button("Delete this plan", role: .destructive) {
                            perform { try store.deletePlan(week: week); loadPlan() }
                        }
                    }
                } header: { Text("Weekly intention") }

                Section {
                    DatePicker("Reflection date", selection: $reflectionDate, in: ...Date.now, displayedComponents: .date)
                        .onChange(of: reflectionDate) { _, _ in loadReflection() }
                    Picker("How did it go?", selection: $outcome) {
                        ForEach(ProJournalStore.outcomes, id: \.self) { Text(roomString($0)).tag($0) }
                    }.disabled(!hasAccess)
                    TextField("What helped? What would you change?", text: $note, axis: .vertical)
                        .lineLimit(3...8).disabled(!hasAccess)
                        .onChange(of: note) { _, value in note = String(value.prefix(2000)); saved = false }
                    Text(roomString("%d / 2000 characters", note.count)).font(.suit(.caption)).foregroundStyle(Color.roomInkSecondary)
                    Button("Save reflection") {
                        perform {
                            try store.reflect(.init(note: note.trimmingCharacters(in: .whitespacesAndNewlines), outcome: outcome), date: reflectionDate, hasAccess: account.hasProAccess)
                        }
                    }.disabled(!hasAccess || note.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    Text("One reflection per day. Saving again updates that day’s entry.").font(.suit(.footnote))
                } header: { Text("Daily reflection") }

                Section {
                    if weeklyEntries.isEmpty { Text("No reflections this week yet.").foregroundStyle(Color.roomInkSecondary) }
                    ForEach(weeklyEntries, id: \.0) { key, reflection in
                        VStack(alignment: .leading, spacing: 8) {
                            Text(key).font(.suit(.caption)).monospacedDigit()
                            Text(roomString(reflection.outcome)).font(.suit(.headline))
                            Text(reflection.note).textSelection(.enabled)
                            Button("Delete reflection", role: .destructive) {
                                perform {
                                    try store.deleteReflection(key)
                                    if ProJournalStore.key(reflectionDate) == key { loadReflection() }
                                }
                            }.buttonStyle(.borderless).frame(minHeight: 44)
                        }.padding(.vertical, 4)
                    }
                } header: { Text("This week’s reflections") }
            }
            if let error { Section { Text(error).foregroundStyle(Color.roomWarning) } }
            if saved { Section { Text("Saved on this iPhone.").foregroundStyle(Color.roomInkSecondary) } }
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
                    Text("This copy contains your private intentions and reflections.").font(.suit(.footnote))
                }
                Button("Delete all local journal data", role: .destructive) { confirmDelete = true }
            } header: { Text("Your data") }
        }
        .listStyle(.insetGrouped).scrollContentBackground(.hidden).background(Color.roomCanvas)
        .foregroundStyle(Color.roomInk).tint(Color.roomAction)
        .navigationTitle("Plans & reflections").navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $paywall) { RoomPaywallView(goal: goal) }
        .confirmationDialog("Delete all plans and reflections from this iPhone? This cannot be undone.", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Delete all local journal data", role: .destructive) {
                perform { try store.deleteAll(); exportText = nil; loadPlan(); loadReflection() }
            }
        }
        .onAppear { loadPlan(); loadReflection() }
    }
    private func moveWeek(_ amount: Int) {
        week = ProJournalStore.calendar.date(byAdding: .weekOfYear, value: amount, to: week)!
        loadPlan(); saved = false
    }
    private func loadPlan() {
        let plan = store.data.plans[weekKey]
        intention = plan?.intention ?? ""; context = plan?.context ?? goal.rawValue
        weekdays = plan?.weekdays ?? [2, 3, 4, 5, 6]
    }
    private func loadReflection() {
        let reflection = store.data.reflections[ProJournalStore.key(reflectionDate)]
        note = reflection?.note ?? ""; outcome = reflection?.outcome ?? ProJournalStore.outcomes[0]
    }
    private func perform(_ action: () throws -> Void) {
        do { try action(); error = nil; exportText = nil; saved = true }
        catch ProJournalStore.Failure.accessRequired {
            error = roomString("An active Pro subscription is required to save new changes.")
        } catch {
            self.error = roomString("The journal could not be saved. Your previous data is unchanged. Try again.")
        }
    }
}
