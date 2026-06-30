import SwiftUI

struct DayPanelView: View {
    let dateKey: String
    @Binding var routineToEdit: Routine?
    @Binding var showAddSheet: Bool

    @Environment(RoutineStore.self) private var store
    @Environment(SettingsStore.self) private var settings
    @Environment(\.colorScheme) private var scheme

    private var date: Date? { Date.from(dateKey: dateKey) }
    private var isToday: Bool { DayBoundary.isToday(dateKey) }

    private var weekdayLabel: String {
        guard let d = date else { return "" }
        let wd = Calendar.gregorianSunday.component(.weekday, from: d) - 1
        let letters = ["일", "월", "화", "수", "목", "금", "토"]
        let m = Calendar.gregorianSunday.component(.month, from: d)
        let day = Calendar.gregorianSunday.component(.day, from: d)
        return "\(m)월 \(day)일 \(letters[wd])"
    }

    var body: some View {
        let prog = store.dayProgress(dateKey)

        VStack(alignment: .leading, spacing: 0) {
            // Day header
            HStack(alignment: .firstTextBaseline) {
                if isToday {
                    Text("오늘")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Color.rcText(scheme))
                    Text(weekdayLabel)
                        .font(.system(size: 15, weight: .regular))
                        .foregroundStyle(Color.rcText2(scheme))
                        .padding(.leading, 4)
                } else {
                    Text(weekdayLabel)
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Color.rcText(scheme))
                }
                Spacer()
                Text("\(prog.done)/\(prog.total) 완료")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Color.rcText2(scheme))
                    .monospacedDigit()
            }
            .padding(.horizontal, 4)
            .padding(.top, 14)
            .padding(.bottom, 10)

            // Routine list card (해당 날짜에 예정된 루틴만)
            let scheduled = store.scheduledRoutines(for: dateKey)
            if !scheduled.isEmpty {
                VStack(spacing: 0) {
                    ForEach(Array(scheduled.enumerated()), id: \.element.id) { idx, routine in
                        if idx > 0 {
                            Rectangle()
                                .fill(Color.rcSeparator(scheme))
                                .frame(height: 0.5)
                                .padding(.leading, 16)
                        }
                        RoutineRowView(
                            routine: routine,
                            dateKey: dateKey,
                            checkStyle: settings.checkStyle,
                            onEdit: { routineToEdit = routine }
                        )
                    }
                }
                .rcCard(scheme, radius: 20)
            }

            // + 루틴 추가 button
            Button(action: { showAddSheet = true }) {
                HStack {
                    Image(systemName: "plus")
                    Text("루틴 추가")
                }
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Color.rcAccent(scheme))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 13)
                .background(Color.rcCard(scheme))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            }
            .padding(.top, 12)
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 28)
    }
}
