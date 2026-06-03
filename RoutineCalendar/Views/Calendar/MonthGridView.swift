import SwiftUI

struct MonthGridView: View {
    let year: Int
    let month: Int
    @Binding var selectedDateKey: String
    let calendarStyle: CalendarStyle

    @Environment(RoutineStore.self) private var store
    @Environment(\.colorScheme) private var scheme

    private var cells: [CalendarCell] {
        var comps = DateComponents()
        comps.year = year
        comps.month = month
        comps.day = 1
        guard let first = Calendar.gregorianSunday.date(from: comps) else { return [] }

        let firstWeekday = Calendar.gregorianSunday.component(.weekday, from: first) - 1  // 0=Sun
        let daysInMonth  = Calendar.gregorianSunday.range(of: .day, in: .month, for: first)?.count ?? 30

        var result: [CalendarCell] = []

        // Leading days from previous month
        for i in stride(from: firstWeekday, through: 1, by: -1) {
            let d = Calendar.gregorianSunday.date(byAdding: .day, value: -i, to: first)!
            result.append(CalendarCell(date: d, isCurrentMonth: false))
        }

        // Current month
        for day in 1...daysInMonth {
            var dc = DateComponents(); dc.year = year; dc.month = month; dc.day = day
            let d = Calendar.gregorianSunday.date(from: dc)!
            result.append(CalendarCell(date: d, isCurrentMonth: true))
        }

        // Trailing days to fill 42 cells
        let trail = 42 - result.count
        if trail > 0, let last = result.last?.date {
            for i in 1...trail {
                let d = Calendar.gregorianSunday.date(byAdding: .day, value: i, to: last)!
                result.append(CalendarCell(date: d, isCurrentMonth: false))
            }
        }
        return Array(result.prefix(42))
    }

    var body: some View {
        let cols = Array(repeating: GridItem(.flexible(), spacing: 0), count: 7)
        LazyVGrid(columns: cols, spacing: 0) {
            ForEach(cells, id: \.dateKey) { cell in
                DayCellView(
                    cell: cell,
                    isSelected: cell.dateKey == selectedDateKey,
                    progress: store.dayProgress(cell.dateKey),
                    calendarStyle: calendarStyle
                )
                .contentShape(Rectangle())
                .onTapGesture {
                    selectedDateKey = cell.dateKey
                }
            }
        }
        .padding(.horizontal, 8)
    }
}
