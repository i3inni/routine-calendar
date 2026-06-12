import WidgetKit
import SwiftUI
import AppIntents

// MARK: - 틴트(홈화면 색조) 대응
//
// iOS18 틴트/vibrant 렌더링에선 솔리드 카드 배경(흰색)이 단색 막대로 변해 내용을 덮는다.
// fullColor일 때만 카드 배경을 칠하고, 틴트 모드에선 배경을 비워 텍스트/아이콘만 보이게 한다.
extension View {
    // 항상 같은 구조(.background)를 적용하고 색만 바꾼다 — if/else로 뷰 타입이 갈리면
    // VStack이 행 높이를 일정하게 못 잡아 간격이 벌어질 수 있어 색만 바꿈.
    // 틴트 모드: 솔리드(흰 덩어리)는 안 되지만 반투명 fill은 카드 구분감을 준다.
    func widgetCard(_ scheme: ColorScheme, _ mode: WidgetRenderingMode, radius: CGFloat = 12) -> some View {
        background(mode == .fullColor ? Color.rcCard(scheme) : Color.rcText(scheme).opacity(0.08),
                   in: RoundedRectangle(cornerRadius: radius, style: .continuous))
    }

    func widgetCapsule(_ color: Color, _ mode: WidgetRenderingMode) -> some View {
        background(mode == .fullColor ? color : Color.clear, in: Capsule())
    }
}

// MARK: - 위젯 정의 (3종)

/// 큰 정사각형: 달력 + 오늘 루틴 체크
struct HomeRoutineWidget: Widget {
    let kind = "HomeRoutineWidget"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: LockScreenProvider()) { entry in
            HomeRoutineView(entry: entry)
                .widgetURL(URL(string: "routinecalendar://today"))
        }
        .configurationDisplayName("오늘 루틴 + 달력")
        .description("달력과 오늘 루틴을 한눈에 보고 바로 체크하세요.")
        .supportedFamilies([.systemLarge])
    }
}

/// 가로형: 오늘 루틴 체크만 (목록 위주 → 더 많이 표시)
struct RoutineListWidget: Widget {
    let kind = "RoutineListWidget"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: LockScreenProvider()) { entry in
            RoutineListOnlyView(entry: entry)
                .widgetURL(URL(string: "routinecalendar://today"))
        }
        .configurationDisplayName("오늘 루틴")
        .description("오늘 루틴을 홈에서 바로 체크하세요.")
        .supportedFamilies([.systemMedium])
    }
}

// MARK: - 큰 정사각형 (달력 + 목록)

struct HomeRoutineView: View {
    let entry: LockScreenEntry
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        VStack(alignment: .leading, spacing: 9) {
            TodayHeader(entry: entry)
            MonthMiniCalendar(entry: entry)
            Rectangle().fill(Color.rcSeparator(scheme)).frame(height: 1)
            RoutineChecklist(entry: entry, maxRows: 2)
        }
        .containerBackground(for: .widget) { Color.rcBg(scheme) }
    }
}

// MARK: - 가로형: 목록만

struct RoutineListOnlyView: View {
    let entry: LockScreenEntry
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            TodayHeader(entry: entry)
            RoutineChecklist(entry: entry, maxRows: 2)
        }
        .containerBackground(for: .widget) { Color.rcBg(scheme) }
    }
}

// MARK: - 공용: 헤더 (날짜 + 진행률 + 추가)

struct TodayHeader: View {
    let entry: LockScreenEntry
    @Environment(\.colorScheme) private var scheme
    @Environment(\.widgetRenderingMode) private var renderMode

    private var progress: (done: Int, total: Int, frac: Double) {
        WidgetDataReader.dayProgress(entry: entry, dateKey: entry.date.dateKey)
    }

    var body: some View {
        HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 1) {
                Text("오늘")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Color.rcText(scheme))
                Text(dateLabel(entry.date))
                    .font(.system(size: 12))
                    .foregroundStyle(Color.rcText2(scheme))
            }
            Spacer()
            Text("\(progress.done)/\(progress.total)")
                .font(.system(size: 14, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(Color.rcText2(scheme))
            Link(destination: URL(string: "routinecalendar://add-routine")!) {
                Image(systemName: "plus")
                    .font(.system(size: 14, weight: .bold))
                    // 틴트 모드: 채운 원이 단색이 되면 +가 안 보임 → 채움 빼고 테두리+아이콘
                    .foregroundStyle(renderMode == .fullColor ? Color.rcAccentText(scheme) : Color.rcText(scheme))
                    .frame(width: 28, height: 28)
                    .background {
                        if renderMode == .fullColor {
                            Circle().fill(Color.rcAccent(scheme))
                        } else {
                            Circle().stroke(Color.rcText3(scheme), lineWidth: 1)
                        }
                    }
            }
        }
    }

    private func dateLabel(_ date: Date) -> String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "M월 d일 (E)"
        return f.string(from: date)
    }
}

// MARK: - 공용: 오늘 루틴 체크 목록

struct RoutineChecklist: View {
    let entry: LockScreenEntry
    let maxRows: Int
    @Environment(\.colorScheme) private var scheme
    @Environment(\.widgetRenderingMode) private var renderMode

    private var todayKey: String { entry.date.dateKey }
    private var todays: [Routine] {
        WidgetDataReader.scheduledRoutines(entry: entry, dateKey: todayKey)
    }

    var body: some View {
        if todays.isEmpty {
            Text("오늘 예정된 루틴이 없어요")
                .font(.system(size: 13))
                .foregroundStyle(Color.rcText3(scheme))
                .frame(maxWidth: .infinity, maxHeight: .infinity)
        } else {
            VStack(spacing: 7) {
                ForEach(todays.prefix(maxRows)) { routine in
                    row(routine)
                }
                if todays.count > maxRows {
                    Text("+\(todays.count - maxRows)개 더 · 탭하면 전체 보기")
                        .font(.system(size: 11))
                        .foregroundStyle(Color.rcText3(scheme))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)   // 상단 정렬(간격 벌어짐 방지)
        }
    }

    private func row(_ routine: Routine) -> some View {
        let done = WidgetDataReader.isDone(entry: entry, routine: routine, dateKey: todayKey)
        return HStack(spacing: 10) {
            Button(intent: ToggleRoutineCompletionIntent(routineId: routine.id.uuidString)) {
                Image(systemName: done ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 21))
                    .foregroundStyle(done ? Color.rcAccent(scheme) : Color.rcText3(scheme))
            }
            .buttonStyle(.plain)

            Text(routine.name)
                .font(.system(size: 14.5, weight: .medium))
                .strikethrough(done, color: Color.rcText3(scheme))
                .foregroundStyle(done ? Color.rcText3(scheme) : Color.rcText(scheme))
                .lineLimit(1)

            Spacer(minLength: 0)

            if routine.type == .count {
                let c = entry.completion[routine.id]?[todayKey] ?? 0
                Text("\(c)/\(routine.target)")
                    .font(.system(size: 11, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Color.rcText2(scheme))
            }
        }
        .padding(.vertical, 6)
        .padding(.horizontal, 11)
        .widgetCard(scheme, renderMode)
    }
}

// MARK: - 공용: 미니 달력 (월 이동 + 완료 표시)

struct MonthMiniCalendar: View {
    let entry: LockScreenEntry
    var rowSpacing: CGFloat = 4   // 달력 전용 위젯은 더 크게 줘서 위아래로 펼침
    @Environment(\.colorScheme) private var scheme
    @Environment(\.widgetRenderingMode) private var renderMode

    private let cal = Calendar.gregorianSunday
    private let weekdaySymbols = ["일", "월", "화", "수", "목", "금", "토"]

    private var monthStart: Date {
        let thisMonth = cal.date(from: cal.dateComponents([.year, .month], from: entry.date)) ?? entry.date
        return cal.date(byAdding: .month, value: entry.monthOffset, to: thisMonth) ?? thisMonth
    }

    var body: some View {
        VStack(spacing: rowSpacing) {
            navBar
            HStack(spacing: 0) {
                ForEach(0..<7, id: \.self) { i in
                    Text(weekdaySymbols[i])
                        .font(.system(size: 9))
                        .foregroundStyle(i == 0 ? Color.rcDestructive.opacity(0.7) : Color.rcText3(scheme))
                        .frame(maxWidth: .infinity)
                }
            }
            ForEach(weeks, id: \.self) { week in
                HStack(spacing: 0) {
                    ForEach(0..<7, id: \.self) { i in
                        cell(week[i]).frame(maxWidth: .infinity)
                    }
                }
            }
        }
    }

    private var navBar: some View {
        HStack(spacing: 8) {
            Button(intent: SetMonthOffsetIntent(offset: entry.monthOffset - 1)) {
                Image(systemName: "chevron.left").font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.rcText2(scheme)).frame(width: 22, height: 22)
            }
            .buttonStyle(.plain)

            Text(monthLabel)
                .font(.system(size: 13, weight: .bold))
                .foregroundStyle(Color.rcText(scheme))

            Button(intent: SetMonthOffsetIntent(offset: entry.monthOffset + 1)) {
                Image(systemName: "chevron.right").font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Color.rcText2(scheme)).frame(width: 22, height: 22)
            }
            .buttonStyle(.plain)

            Spacer()

            if entry.monthOffset != 0 {
                Button(intent: SetMonthOffsetIntent(offset: 0)) {
                    Text("오늘").font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Color.rcAccent(scheme))
                        .padding(.horizontal, 9).padding(.vertical, 3)
                        .widgetCapsule(Color.rcCard2(scheme), renderMode)
                }
                .buttonStyle(.plain)
            }
        }
    }

    // 앱에서 선택한 스타일(점/막대/링)로 완료 표시
    @ViewBuilder
    private func cell(_ day: Date?) -> some View {
        if let day {
            let prog = WidgetDataReader.dayProgress(entry: entry, dateKey: day.dateKey)
            let isToday = entry.monthOffset == 0 && cal.isDate(day, inSameDayAs: entry.date)
            let isFuture = day > entry.date
            let show = !isFuture && prog.total > 0
            VStack(spacing: 0.5) {
                ZStack {
                    if entry.calendarStyle == .ring && show {
                        Circle().stroke(Color.rcEmptyFill(scheme), lineWidth: 2)
                            .frame(width: 18, height: 18)
                        Circle().trim(from: 0, to: prog.frac)
                            .stroke(Color.rcAccent(scheme), style: StrokeStyle(lineWidth: 2, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                            .frame(width: 18, height: 18)
                    } else if isToday {
                        Circle().stroke(Color.rcAccent(scheme), lineWidth: 1.2)
                            .frame(width: 18, height: 18)
                    }
                    Text("\(cal.component(.day, from: day))")
                        .font(.system(size: 10, weight: isToday ? .bold : .regular))
                        .foregroundStyle(isToday ? Color.rcAccent(scheme) : Color.rcText(scheme))
                }
                .frame(width: 18, height: 18)

                indicator(prog: prog, show: show)
                    .frame(height: 4)
            }
        } else {
            Color.clear.frame(width: 18, height: 22)
        }
    }

    // 점 / 막대 인디케이터 (링 스타일은 셀에서 처리 → 여기선 빈칸)
    @ViewBuilder
    private func indicator(prog: (done: Int, total: Int, frac: Double), show: Bool) -> some View {
        if show && entry.calendarStyle == .dots {
            HStack(spacing: 1.5) {
                ForEach(0..<min(4, prog.total), id: \.self) { i in
                    Circle()
                        .fill(i < prog.done ? Color.rcAccent(scheme) : Color.rcEmptyFill(scheme))
                        .frame(width: 3, height: 3)
                }
            }
        } else if show && entry.calendarStyle == .bar {
            ZStack(alignment: .leading) {
                Capsule().fill(Color.rcEmptyFill(scheme)).frame(width: 14, height: 3)
                Capsule().fill(Color.rcAccent(scheme)).frame(width: max(0, 14 * prog.frac), height: 3)
            }
        } else {
            Color.clear
        }
    }

    private var monthLabel: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "yyyy년 M월"
        return f.string(from: monthStart)
    }

    private var weeks: [[Date?]] {
        guard let range = cal.range(of: .day, in: .month, for: monthStart) else { return [] }
        let leading = cal.component(.weekday, from: monthStart) - 1
        var cells: [Date?] = Array(repeating: nil, count: leading)
        for d in range {
            cells.append(cal.date(byAdding: .day, value: d - 1, to: monthStart))
        }
        while cells.count % 7 != 0 { cells.append(nil) }
        return stride(from: 0, to: cells.count, by: 7).map { Array(cells[$0..<$0 + 7]) }
    }
}

// MARK: - Previews (캔버스에서 fullColor ↔ 틴트 전환해 확인)

#Preview("오늘 루틴 + 달력", as: .systemLarge) {
    HomeRoutineWidget()
} timeline: {
    LockScreenEntry.sample
}

#Preview("오늘 루틴(M)", as: .systemMedium) {
    RoutineListWidget()
} timeline: {
    LockScreenEntry.sample
}
