import SwiftUI

enum ProMotion {
    static let enter = Animation.timingCurve(0.2, 0, 0, 1, duration: 0.32)
    static func entrance(_ seconds: Double) -> Animation { .timingCurve(0.2, 0, 0, 1, duration: seconds) }
    static func state(duration: Double) -> Animation { .timingCurve(0.4, 0, 0.2, 1, duration: duration) }
    static let spring = Animation.spring(response: 0.22, dampingFraction: 0.65)
}

private struct ProReveal: ViewModifier {
    let delay: Int
    let duration: Int
    let rise: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var visible = false
    func body(content: Content) -> some View {
        content.opacity(visible ? 1 : 0).offset(y: reduceMotion || visible ? 0 : rise)
            .task(id: reduceMotion) {
                visible = false
                if reduceMotion { withAnimation(.linear(duration: 0.1)) { visible = true }; return }
                do {
                    if delay > 0 { try await Task.sleep(for: .milliseconds(delay)) }
                    withAnimation(ProMotion.entrance(Double(duration) / 1000)) { visible = true }
                } catch { }
            }
    }
}

extension View {
    func proReveal(delay: Int = 0, duration: Int = 320, rise: CGFloat = 16) -> some View {
        modifier(ProReveal(delay: delay, duration: duration, rise: rise))
    }
    func proShimmer(disabled: Bool) -> some View { modifier(ProShimmer(disabled: disabled)) }
}

struct ProInviteHero: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var entered = false
    @State private var start = Date()
    private let tiles: [(CGFloat, CGFloat, Double, CGFloat)] = [
        (-112, -64, -10, 68), (112, -88, 8, 60), (-104, 56, 6, 64), (116, 72, -7, 72)
    ]
    var body: some View {
        GeometryReader { geo in
            let k: CGFloat = min(1.1, max(0.75, geo.size.width / 350))
            TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion || scenePhase != .active)) { context in
                let elapsed = context.date.timeIntervalSince(start)
                ZStack {
                    Circle().fill(Color.roomMint).frame(width: 224 * k, height: 224 * k)
                        .scaleEffect(reduceMotion || entered ? 1 : 0.6).opacity(entered ? 1 : 0)
                    ForEach(0..<4, id: \.self) { i in
                        floatingTile(i, scale: k, elapsed: elapsed)
                    }
                    Image(OffzoneNookFullBody.welcome.assetName).resizable().scaledToFit()
                        .frame(height: 190 * k).scaleEffect(reduceMotion || entered ? 1 : 0.92).opacity(entered ? 1 : 0)
                        .offset(y: reduceMotion ? 0 : CGFloat(sin(elapsed * 2 * .pi / 3.2) * 4))
                }.frame(maxWidth: .infinity, maxHeight: .infinity)
            }
        }.frame(height: 270).accessibilityHidden(true)
            .task(id: reduceMotion) {
                start = .now; entered = false
                withAnimation(reduceMotion ? .linear(duration: 0.1) : ProMotion.entrance(0.5)) { entered = true }
            }
    }

    private func floatingTile(_ i: Int, scale: CGFloat, elapsed: TimeInterval) -> some View {
        let spec = tiles[i]
        let phase = Double(i) * Double.pi / 2
        let drift: CGFloat = reduceMotion ? 0 : CGFloat(sin(elapsed * 2 * Double.pi / 4.4 + phase) * 3)
        return ProFloatingTile(index: i, x: spec.0 * scale, y: spec.1 * scale + drift,
                               rotation: spec.2, size: spec.3 * scale)
    }
}

private struct ProFloatingTile: View {
    let index: Int
    let x: CGFloat
    let y: CGFloat
    let rotation: Double
    let size: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var moved = false
    var body: some View {
        Image("OffzonePro-tile-\(index + 1)").resizable().scaledToFit().frame(width: size, height: size)
            .opacity(moved ? 1 : 0).scaleEffect(reduceMotion || moved ? 1 : 0.55)
            .rotationEffect(.degrees(reduceMotion || moved ? rotation : 0))
            .offset(x: reduceMotion || moved ? x : 0, y: reduceMotion || moved ? y : 0)
            .task(id: reduceMotion) {
                moved = false
                if reduceMotion { withAnimation(.linear(duration: 0.1)) { moved = true }; return }
                do {
                    try await Task.sleep(for: .milliseconds(120 + 80 * index))
                    withAnimation(ProMotion.entrance(0.7)) { moved = true }
                } catch { }
            }
    }
}

struct ProRemindHero: View {
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var drop = false
    @State private var badge: CGFloat = 0
    @State private var start = Date()
    var body: some View {
        VStack(spacing: 12) {
            HStack(alignment: .center, spacing: 12) {
                Image(systemName: "bell.fill").font(.suit(.title2)).dynamicTypeSize(.large).foregroundStyle(Color.roomAccent)
                    .frame(width: 40, height: 40).background(Color.roomMint, in: RoundedRectangle(cornerRadius: 10))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: 4) {
                    Text(roomString("pro_banner_title")).font(.suit(.subheadline, weight: .semibold))
                    Text(roomString("pro_banner_body")).font(.suit(.caption)).foregroundStyle(Color.roomInkSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }.frame(maxWidth: .infinity, alignment: .leading)
            }.padding(12).editorialPanel()
                .offset(y: reduceMotion || drop ? 0 : -72).opacity(drop ? 1 : 0)
            TimelineView(.animation(minimumInterval: 1 / 30, paused: reduceMotion || scenePhase != .active)) { context in
                let cycle = max(0, context.date.timeIntervalSince(start) - 1.2).truncatingRemainder(dividingBy: 3.4)
                let t = cycle / 0.7
                let angle = reduceMotion || cycle >= 0.7 ? 0 : sin(t * 6 * .pi) * 4 * (1 - t)
                Image("OffzonePro-nook-clock").resizable().scaledToFit().frame(height: 190)
                    .rotationEffect(.degrees(angle), anchor: .bottom)
                    .overlay(alignment: .topTrailing) {
                        Text("1").font(.suit(.caption, weight: .bold)).dynamicTypeSize(.large).foregroundStyle(Color.roomCanvas)
                            .frame(width: 30, height: 30).background(Color.roomAccent, in: Circle())
                            .scaleEffect(reduceMotion ? 1 : badge).offset(x: -4, y: 14).accessibilityHidden(true)
                    }
            }.frame(maxWidth: .infinity)
        }
        .task(id: reduceMotion) {
            start = .now; drop = false; badge = reduceMotion ? 1 : 0
            if reduceMotion { withAnimation(.linear(duration: 0.1)) { drop = true }; return }
            do {
                try await Task.sleep(for: .milliseconds(200))
                withAnimation(.spring(response: 0.5, dampingFraction: 0.7)) { drop = true }
                try await Task.sleep(for: .milliseconds(600))
                withAnimation(.spring(response: 0.24, dampingFraction: 0.6)) { badge = 1.15 }
                try await Task.sleep(for: .milliseconds(120))
                withAnimation(.spring(response: 0.24, dampingFraction: 0.65)) { badge = 1 }
            } catch { }
        }
    }
}

private struct ProRowHeights: PreferenceKey {
    static var defaultValue: [Int: CGFloat] = [:]
    static func reduce(value: inout [Int: CGFloat], nextValue: () -> [Int: CGFloat]) { value.merge(nextValue(), uniquingKeysWith: { _, new in new }) }
}

struct ProTrialTimeline: View {
    let days: [Int]
    let remind: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var fill: CGFloat = 0
    @State private var litCount = 0
    @State private var heights: [Int: CGFloat] = [:]
    var body: some View {
        VStack(alignment: .leading, spacing: 22) {
            ForEach(Array(days.enumerated()), id: \.offset) { i, day in
                let lit = i < litCount
                HStack(alignment: .top, spacing: 16) {
                    ProTimelineNode(icon: i == 0 ? "star.fill" : i == days.count - 1 ? "calendar" : "bell.fill", lit: lit)
                    VStack(alignment: .leading, spacing: 5) {
                        Text(i == 0 ? roomString("pro_how_today") : ProPlanMath.futureDayLabel(day))
                            .font(.suit(.headline, weight: lit ? .semibold : .medium))
                        Text(roomString(i == 0 ? "pro_how_today_body" : i == days.count - 1 ? "pro_how_end_body" : remind ? "pro_how_remind_body" : "pro_how_free_body"))
                            .font(.suit(.subheadline, weight: lit ? .semibold : .medium)).foregroundStyle(Color.roomInkSecondary)
                    }.opacity(lit ? 1 : 0.55).frame(maxWidth: .infinity, minHeight: 52, alignment: .leading)
                        .fixedSize(horizontal: false, vertical: true)
                        .animation(reduceMotion ? .linear(duration: 0.1) : ProMotion.state(duration: 0.18), value: lit)
                }
                .background(GeometryReader { geo in Color.clear.preference(key: ProRowHeights.self, value: [i: geo.size.height]) })
                .proReveal(delay: 200 + i * 90)
            }
        }
        .background(alignment: .topLeading) {
            let height = days.indices.dropLast().reduce(CGFloat(0)) { $0 + (heights[$1] ?? 52) + 22 }
            ZStack(alignment: .top) {
                Capsule().fill(Color.roomLine)
                Capsule().fill(Color.roomAccent).scaleEffect(x: 1, y: fill, anchor: .top)
            }.frame(width: 4, height: height).offset(x: 16, y: 18).accessibilityHidden(true)
        }
        .onPreferenceChange(ProRowHeights.self) { if heights != $0 { heights = $0 } }
        .padding(20).editorialPanel().proReveal(rise: 24)
        .task(id: reduceMotion) {
            fill = reduceMotion ? 1 : 0; litCount = reduceMotion ? days.count : 0
            guard !reduceMotion && !days.isEmpty else { return }
            do {
                try await Task.sleep(for: .milliseconds(300))
                withAnimation(.linear(duration: 2.4)) { fill = 1 }
                litCount = 1
                // Explicit thresholds include 1.0: the last node must finish fully lit.
                for i in 1..<days.count {
                    try await Task.sleep(for: .milliseconds(2400 / max(1, days.count - 1)))
                    litCount = i + 1
                }
            } catch { }
        }
        .accessibilityIdentifier("pro.timeline")
    }
}

private struct ProTimelineNode: View {
    let icon: String
    let lit: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var scale: CGFloat = 1
    var body: some View {
        Image(systemName: icon).font(.suit(.body, weight: .semibold)).dynamicTypeSize(.large).foregroundStyle(Color.roomCanvas)
            .opacity(lit ? 1 : 0).frame(width: 36, height: 36)
            .background(lit ? Color.roomAccent : Color.roomLine, in: Circle()).scaleEffect(reduceMotion ? 1 : scale)
            .animation(reduceMotion ? .linear(duration: 0.1) : ProMotion.state(duration: 0.18), value: lit)
            .accessibilityHidden(true)
            .task(id: lit) {
                guard lit && !reduceMotion else { scale = 1; return }
                withAnimation(ProMotion.spring) { scale = 1.15 }
                do { try await Task.sleep(for: .milliseconds(110)); withAnimation(ProMotion.spring) { scale = 1 } } catch { }
            }
    }
}

struct ProPlanCard: View {
    let plan: ProPlanDisplay
    let selected: Bool
    let savings: Int?
    let perMonth: String?
    let burst: Int
    let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var scale: CGFloat = 1
    var body: some View {
        Button(action: action) {
            HStack(alignment: .center, spacing: 12) {
                VStack(alignment: .leading, spacing: 6) {
                    Text(roomString(plan.annual ? "pro_yearly" : "pro_monthly")).font(.suit(.headline, weight: .semibold))
                    if let savings {
                        ViewThatFits(in: .horizontal) { badges(savings, horizontal: true); badges(savings, horizontal: false) }
                    }
                    Text(plan.trialDays.map { roomString("pro_free_then", ProPlanMath.dayLabel($0), plan.cadence) } ?? plan.cadence)
                        .font(.suit(.subheadline)).fixedSize(horizontal: false, vertical: true)
                    if let perMonth { Text(roomString("pro_per_month", perMonth)).font(.suit(.caption)).foregroundStyle(Color.roomInkSecondary) }
                }.frame(maxWidth: .infinity, alignment: .leading)
                ZStack {
                    Circle().strokeBorder(selected ? Color.roomAccent : Color.roomInkSecondary, lineWidth: 1.5)
                    Circle().fill(Color.roomAccent).frame(width: 14, height: 14).scaleEffect(selected ? 1 : 0)
                        .animation(reduceMotion ? .linear(duration: 0.1) : ProMotion.spring, value: selected)
                }.frame(width: 24, height: 24).accessibilityHidden(true)
            }.padding(18).frame(maxWidth: .infinity, minHeight: 84, alignment: .leading)
                .foregroundStyle(Color.roomInk).multilineTextAlignment(.leading)
                .background(selected ? Color.roomMint : Color.roomCard, in: RoundedRectangle(cornerRadius: 24))
                .overlay { RoundedRectangle(cornerRadius: 24).strokeBorder(selected ? Color.roomAccent : Color.roomOutline, lineWidth: selected ? 2 : 1) }
                .animation(reduceMotion ? .linear(duration: 0.1) : ProMotion.state(duration: 0.22), value: selected)
        }.buttonStyle(.plain).scaleEffect(reduceMotion ? 1 : scale)
            .overlay { ProConfetti(trigger: burst) }
            .accessibilityAddTraits(selected ? .isSelected : []).accessibilityIdentifier(plan.annual ? "pro.annual" : "pro.monthly")
            .task(id: selected) {
                guard selected && !reduceMotion else { scale = 1; return }
                withAnimation(ProMotion.spring) { scale = 1.03 }
                do { try await Task.sleep(for: .milliseconds(110)); withAnimation(ProMotion.spring) { scale = 1 } } catch { }
            }
    }
    @ViewBuilder private func badges(_ savings: Int, horizontal: Bool) -> some View {
        let content = Group {
            Text(roomString("pro_best_value")).padding(.horizontal, 8).padding(.vertical, 4).foregroundStyle(Color.roomCanvas).background(Color.roomAccent, in: Capsule())
            Text(roomString("pro_save", savings)).padding(.horizontal, 8).padding(.vertical, 4).background(Color.roomButter, in: Capsule())
        }.font(.suit(.caption, weight: .bold)).fixedSize(horizontal: false, vertical: true)
        if horizontal { HStack(spacing: 6) { content }.fixedSize(horizontal: true, vertical: true) }
        else { VStack(alignment: .leading, spacing: 6) { content } }
    }
}

struct ProConfetti: View {
    let trigger: Int
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var start = Date()
    @State private var active = false
    private let colors: [Color] = [.roomAccent, .roomButter, .roomMint, .roomCard]
    var body: some View {
        if !reduceMotion {
            GeometryReader { geo in
                TimelineView(.animation(minimumInterval: 1 / 30, paused: !active || scenePhase != .active)) { context in
                    let t = min(1, max(0, context.date.timeIntervalSince(start) / 0.7))
                    if active && t < 1 {
                        ForEach(0..<24, id: \.self) { i in
                            let angle = (-160 + Double((i * 47) % 140)) * .pi / 180
                            let reach = Double(90 + (i * 13) % 60) * t
                            RoundedRectangle(cornerRadius: 2).fill(colors[i % 4])
                                .frame(width: i % 2 == 0 ? 8 : 5, height: i % 2 == 0 ? 4 : 5)
                                .rotationEffect(.degrees(Double(i * 37) * t))
                                .position(x: geo.size.width * 0.78 + cos(angle) * reach, y: sin(angle) * reach + 140 * t * t)
                                .opacity(1 - max(0, (t - 0.7) / 0.3))
                        }
                    }
                }
            }.allowsHitTesting(false).accessibilityHidden(true)
                .task(id: trigger) {
                    guard trigger > 0 else { return }
                    start = .now; active = true
                    do { try await Task.sleep(for: .milliseconds(700)); active = false } catch { active = false }
                }
        }
    }
}

struct ProDoneHero: View {
    var body: some View {
        ZStack {
            Circle().fill(Color.roomMint).frame(width: 224, height: 224)
            Image(OffzoneNookFullBody.ready.assetName).resizable().scaledToFit().frame(height: 200)
        }.frame(maxWidth: .infinity).frame(height: 250).proReveal(duration: 500)
            .overlay { ProConfetti(trigger: 1) }.accessibilityHidden(true)
    }
}

private struct ProShimmer: ViewModifier {
    let disabled: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var progress: CGFloat = 0
    func body(content: Content) -> some View {
        content.overlay {
            if !reduceMotion {
                GeometryReader { geo in
                    LinearGradient(colors: [.clear, Color.white.opacity(0.28), .clear], startPoint: .leading, endPoint: .trailing)
                        .frame(width: geo.size.width * 0.25, height: geo.size.height * 3)
                        .rotationEffect(.degrees(25)).offset(x: -geo.size.width * 0.25 + geo.size.width * 1.5 * progress, y: -geo.size.height)
                }.clipShape(Capsule()).opacity(progress == 0 ? 0 : 1).allowsHitTesting(false).accessibilityHidden(true)
            }
        }
        .task(id: reduceMotion || disabled || scenePhase != .active) {
            progress = 0
            guard !reduceMotion && !disabled && scenePhase == .active else { return }
            do {
                try await Task.sleep(for: .seconds(2))
                while !Task.isCancelled {
                    progress = 0
                    withAnimation(ProMotion.state(duration: 0.6)) { progress = 1 }
                    try await Task.sleep(for: .seconds(5))
                }
            } catch { progress = 0 }
        }
    }
}
