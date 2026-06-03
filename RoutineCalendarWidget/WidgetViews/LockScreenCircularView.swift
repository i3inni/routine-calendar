import SwiftUI
import WidgetKit

/// 잠금화면 원형 위젯 — 오늘 진행률 링
struct LockScreenCircularView: View {
    let entry: LockScreenEntry

    private var prog: (done: Int, total: Int, frac: Double) {
        WidgetDataReader.dayProgress(entry: entry, dateKey: entry.date.dateKey)
    }

    var body: some View {
        ZStack {
            // 진행률 링
            Circle()
                .stroke(.secondary.opacity(0.3), lineWidth: 4)
            Circle()
                .trim(from: 0, to: prog.frac)
                .stroke(.primary, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                .rotationEffect(.degrees(-90))

            // 중앙 텍스트 (한 줄)
            Text("\(prog.done)/\(prog.total)")
                .font(.system(size: 14, weight: .bold).monospacedDigit())
                .foregroundStyle(.primary)
                .minimumScaleFactor(0.5)
                .lineLimit(1)
        }
        .padding(4)
    }
}
