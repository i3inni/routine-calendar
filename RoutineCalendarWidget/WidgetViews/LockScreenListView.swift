import SwiftUI
import WidgetKit

/// 잠금화면 직사각형 위젯 — 오늘 루틴 목록 (완료 항목은 체크 표시)
struct LockScreenListView: View {
    let entry: LockScreenEntry

    private var dateKey: String { entry.date.dateKey }
    private var scheduled: [Routine] {
        WidgetDataReader.scheduledRoutines(entry: entry, dateKey: dateKey)
    }
    private var prog: (done: Int, total: Int, frac: Double) {
        WidgetDataReader.dayProgress(entry: entry, dateKey: dateKey)
    }
    private var allDone: Bool {
        prog.total > 0 && prog.done >= prog.total
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            // 헤더
            HStack {
                Text("같이해")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(.secondary)
                Spacer()
                Text("\(prog.done)/\(prog.total)")
                    .font(.system(size: 11, weight: .medium).monospacedDigit())
                    .foregroundStyle(.secondary)
            }

            if prog.total == 0 {
                Text("오늘 루틴이 없어요")
                    .font(.system(size: 13))
                    .foregroundStyle(.secondary)
            } else if allDone {
                HStack(spacing: 4) {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 12))
                    Text("오늘 루틴 모두 완료")
                        .font(.system(size: 13, weight: .medium))
                }
                .foregroundStyle(.primary)
            } else {
                ForEach(Array(scheduled.prefix(2))) { routine in
                    let done = WidgetDataReader.isDone(entry: entry, routine: routine, dateKey: dateKey)
                    HStack(spacing: 4) {
                        Image(systemName: done ? "checkmark.circle.fill" : "circle")
                            .font(.system(size: 12))
                            .foregroundStyle(done ? .secondary : .primary)
                        Text(routine.name)
                            .font(.system(size: 13, weight: .medium))
                            .foregroundStyle(done ? .secondary : .primary)
                            .strikethrough(done, color: .secondary)
                            .lineLimit(1)
                    }
                }
                if scheduled.count > 2 {
                    Text("+\(scheduled.count - 2)개")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                }
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }
}
