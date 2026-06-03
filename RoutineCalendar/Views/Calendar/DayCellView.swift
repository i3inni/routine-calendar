import SwiftUI

struct CalendarCell {
    let date: Date
    let isCurrentMonth: Bool
    var dateKey: String { date.dateKey }
    var day: Int { Calendar.gregorianSunday.component(.day, from: date) }
    var isToday: Bool { Calendar.gregorianSunday.isDateInToday(date) }
    var isFuture: Bool { date > Date() }
}

struct DayCellView: View {
    let cell: CalendarCell
    let isSelected: Bool
    let progress: (done: Int, total: Int, frac: Double)
    let calendarStyle: CalendarStyle

    @Environment(\.colorScheme) private var scheme

    private var showIndicator: Bool {
        cell.isCurrentMonth && !cell.isFuture && progress.total > 0
    }

    var body: some View {
        VStack(spacing: 2) {
            ZStack {
                // Ring style track (behind number)
                if calendarStyle == .ring && showIndicator {
                    Circle()
                        .stroke(Color.rcEmptyFill(scheme), lineWidth: 2.5)
                        .frame(width: 32, height: 32)
                    Circle()
                        .trim(from: 0, to: progress.frac)
                        .stroke(Color.rcAccent(scheme),
                                style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .frame(width: 32, height: 32)
                        .animation(.spring(response: 0.35, dampingFraction: 0.75), value: progress.frac)
                }

                // Date circle
                ZStack {
                    if isSelected {
                        Circle()
                            .fill(Color.rcAccent(scheme))
                            .frame(width: 30, height: 30)
                    } else if cell.isToday {
                        Circle()
                            .stroke(Color.rcAccent(scheme), lineWidth: 1.5)
                            .frame(width: 30, height: 30)
                    }
                    Text("\(cell.day)")
                        .font(.system(size: 16, weight: (isSelected || cell.isToday) ? .bold : .regular))
                        .foregroundStyle(isSelected ? Color.rcAccentText(scheme) : Color.rcText(scheme))
                        .monospacedDigit()
                }
                .frame(width: 32, height: 32)
            }

            // Completion indicator row (6pt height)
            Group {
                if showIndicator {
                    switch calendarStyle {
                    case .dots:
                        HStack(spacing: 2.5) {
                            ForEach(0..<min(5, progress.total), id: \.self) { i in
                                Circle()
                                    .fill(i < progress.done ? Color.rcAccent(scheme) : Color.rcEmptyFill(scheme))
                                    .frame(width: 4.5, height: 4.5)
                            }
                        }
                    case .bar:
                        ZStack(alignment: .leading) {
                            Capsule().fill(Color.rcEmptyFill(scheme)).frame(width: 20, height: 3.5)
                            Capsule().fill(Color.rcAccent(scheme))
                                .frame(width: max(0, 20 * progress.frac), height: 3.5)
                                .animation(.spring(response: 0.35, dampingFraction: 0.75), value: progress.frac)
                        }
                    case .ring:
                        Color.clear
                    }
                } else {
                    Color.clear
                }
            }
            .frame(height: 6)
        }
        .opacity(cell.isCurrentMonth ? 1 : 0.28)
        .frame(height: 50)
    }
}
