import SwiftUI

struct CompletionControlView: View {
    let routine: Routine
    let dateKey: String
    let checkStyle: CheckStyle

    @Environment(RoutineStore.self) private var store
    @Environment(\.colorScheme) private var scheme

    // Animation states
    @State private var tapScale: CGFloat = 1.0
    @State private var burstScale: CGFloat = 1.0
    @State private var burstOpacity: Double = 0

    private var count: Int    { store.getCount(routine.id, dateKey) }
    private var isDone: Bool  { store.isDone(routine, dateKey) }
    private var isFuture: Bool { DayBoundary.isFuture(dateKey) }

    var body: some View {
        mainControl
            .scaleEffect(tapScale)
            .overlay(burstRing)
            .opacity(isFuture ? 0.35 : 1.0)
            .contentShape(Rectangle())
            .onTapGesture { if !isFuture { handleTap() } }
    }

    // MARK: - Main control

    @ViewBuilder
    private var mainControl: some View {
        if routine.type == .count {
            countRing
        } else {
            checkControl
        }
    }

    // MARK: - Count ring

    private var countRing: some View {
        let frac = routine.target > 0 ? min(1.0, Double(count) / Double(routine.target)) : 0
        return RingView(
            size: 34, stroke: 3.5, fraction: frac,
            color: isDone ? Color.rcAccent(scheme) : Color.rcText2(scheme),
            trackColor: Color.rcEmptyFill(scheme)
        ) {
            Text("\(count)")
                .font(.system(size: 11, weight: .bold).monospacedDigit())
                .foregroundStyle(isDone ? Color.rcAccent(scheme) : Color.rcText2(scheme))
                .animation(.spring(response: 0.3, dampingFraction: 0.6), value: isDone)
        }
    }

    // MARK: - Check controls

    @ViewBuilder
    private var checkControl: some View {
        switch checkStyle {
        case .circle: filledShape(cornerRadius: 999)
        case .square: filledShape(cornerRadius: 8)
        case .ring:   ringCheckView
        }
    }

    private func filledShape(cornerRadius: CGFloat) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .fill(isDone ? Color.rcAccent(scheme) : Color.clear)
            RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                .stroke(isDone ? Color.rcAccent(scheme) : Color.rcText3(scheme), lineWidth: 2)

            if isDone {
                Image(systemName: "checkmark")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color.rcAccentText(scheme))
                    .transition(
                        .scale(scale: 0.3, anchor: .center)
                        .combined(with: .opacity)
                    )
            }
        }
        .frame(width: 28, height: 28)
        .animation(.spring(response: 0.28, dampingFraction: 0.55), value: isDone)
    }

    private var ringCheckView: some View {
        RingView(
            size: 28, stroke: 3,
            fraction: isDone ? 1.0 : 0.0,
            color: Color.rcAccent(scheme),
            trackColor: Color.rcEmptyFill(scheme)
        ) {
            if isDone {
                Image(systemName: "checkmark")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(Color.rcAccent(scheme))
                    .transition(
                        .scale(scale: 0.2, anchor: .center)
                        .combined(with: .opacity)
                    )
            }
        }
        .animation(.spring(response: 0.35, dampingFraction: 0.5), value: isDone)
    }

    // MARK: - Burst ring overlay

    private var burstRing: some View {
        Circle()
            .stroke(Color.rcAccent(scheme), lineWidth: 2)
            .scaleEffect(burstScale)
            .opacity(burstOpacity)
            .allowsHitTesting(false)
    }

    // MARK: - Tap handler

    private func handleTap() {
        let completing = !isDone  // about to become done?

        // Toggle store
        store.toggle(routine, dateKey)

        // 1. Press-down scale
        withAnimation(.spring(response: 0.12, dampingFraction: 0.6)) {
            tapScale = 0.80
        }
        // 2. Spring back with overshoot
        withAnimation(.spring(response: 0.38, dampingFraction: 0.42).delay(0.08)) {
            tapScale = 1.0
        }

        // 3. Burst ring + stronger haptic only when completing
        if completing {
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            burstScale = 1.0
            burstOpacity = 0.65
            withAnimation(.easeOut(duration: 0.45)) {
                burstScale = 2.2
                burstOpacity = 0
            }
        } else {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        }
    }
}
