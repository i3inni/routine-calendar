import SwiftUI

struct RoutineRowView: View {
    let routine: Routine
    let dateKey: String
    let checkStyle: CheckStyle
    let onEdit: () -> Void

    @Environment(RoutineStore.self) private var store
    @Environment(\.colorScheme) private var scheme

    private var isDone: Bool { store.isDone(routine, dateKey) }
    private var count:  Int  { store.getCount(routine.id, dateKey) }
    private var streakDays: Int { store.streak(routine) }
    /// 연속 막대: 오른쪽 끝(오늘)부터 현재 연속일수만큼 채운다. 끊기면(streak 0) 모두 빈칸.
    private var weekDays: [Bool] {
        let filled = min(max(streakDays, 0), 7)
        return (0..<7).map { $0 >= 7 - filled }
    }

    var body: some View {
        HStack(alignment: .center, spacing: 13) {
            CompletionControlView(
                routine: routine,
                dateKey: dateKey,
                checkStyle: checkStyle
            )

            VStack(alignment: .leading, spacing: 5) {
                // Name line
                HStack(spacing: 4) {
                    Text(routine.name)
                        .font(.rcRoutineName)
                        .foregroundStyle(isDone && routine.type == .check
                            ? Color.rcText3(scheme)
                            : Color.rcText(scheme))
                        .strikethrough(isDone && routine.type == .check,
                                       color: Color.rcText3(scheme))
                        .opacity(isDone && routine.type == .check ? 0.55 : 1)

                    if routine.type == .count {
                        Text(" \(count)/\(routine.target)\(routine.unit)")
                            .font(.system(size: 14, weight: .regular))
                            .foregroundStyle(Color.rcText3(scheme))
                            .monospacedDigit()
                    }
                }

                // Meta row
                HStack(spacing: 8) {
                    if let reminder = routine.reminder, !routine.anytime {
                        HStack(spacing: 3) {
                            Image(systemName: "bell.fill")
                                .font(.system(size: 10))
                            Text(reminder)
                                .font(.rcMeta)
                        }
                        .foregroundStyle(Color.rcText2(scheme))
                    }

                    // Streak chip
                    HStack(spacing: 4) {
                        MiniWeekView(days: weekDays)
                        Text("\(streakDays)")
                            .font(.system(size: 12.5, weight: .bold))
                            .foregroundStyle(Color.rcText(scheme))
                            .monospacedDigit()
                        Text("일 연속")
                            .font(.rcMeta)
                            .foregroundStyle(Color.rcText2(scheme))
                    }
                }
            }

            Spacer()

            Image(systemName: "chevron.right")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color.rcText3(scheme))
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .contentShape(Rectangle())
        .onTapGesture { onEdit() }
    }
}
