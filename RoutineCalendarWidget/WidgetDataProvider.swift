import Foundation
import WidgetKit

struct LockScreenEntry: TimelineEntry {
    let date: Date
    let routines: [Routine]
    let completion: [UUID: [String: Int]]
    var monthOffset: Int = 0          // 홈 위젯 미니 달력 표시 월 (0=이번 달)
    var calendarStyle: CalendarStyle = .dots   // 앱에서 선택한 달력 표시 스타일
    var friends: [Friend] = []        // 친구 위젯용 친구 현황
}

extension LockScreenEntry {
    /// Xcode Preview / 위젯 갤러리용 샘플 데이터.
    static var sample: LockScreenEntry {
        let cal = Calendar.gregorianSunday
        let today = Date()

        let r1 = Routine(name: "코드트리 한 문제 풀기")
        let r2 = Routine(name: "비타민 챙겨먹기")
        let r3 = Routine(name: "유산균 챙겨먹기")
        let r4 = Routine(name: "씻기 전 푸쉬업")
        let routines = [r1, r2, r3, r4]

        // 최근 6일 일부 완료 (달력 점/막대/링 표시용)
        var completion: [UUID: [String: Int]] = [:]
        for offset in 1...6 {
            guard let day = cal.date(byAdding: .day, value: -offset, to: today) else { continue }
            let key = day.dateKey
            let doneCount = (offset % 2 == 0) ? 4 : 3
            for (i, r) in routines.enumerated() where i < doneCount {
                completion[r.id, default: [:]][key] = 1
            }
        }
        completion[r4.id, default: [:]][today.dateKey] = 1   // 오늘 1개 완료

        let friends = [
            Friend(id: "1", name: "지수", initial: "지", doneToday: 2, totalToday: 3,
                   remaining: ["운동"], done: ["독서", "물 마시기"], streak: 5),
            Friend(id: "2", name: "민호", initial: "민", doneToday: 4, totalToday: 4,
                   remaining: [], done: ["운동", "독서", "물", "스트레칭"], streak: 12),
            Friend(id: "3", name: "하라", initial: "하", doneToday: 0, totalToday: 2,
                   remaining: ["명상", "산책"], done: [], streak: 0),
        ]

        return LockScreenEntry(date: today, routines: routines, completion: completion,
                               monthOffset: 0, calendarStyle: .ring, friends: friends)
    }
}

struct WidgetDataReader {
    static func readEntry(for date: Date = Date()) -> LockScreenEntry {
        let decoder = JSONDecoder()
        let defaults = AppGroup.defaults

        var routines: [Routine] = []
        if let data = defaults.data(forKey: AppGroup.routinesKey) {
            routines = (try? decoder.decode([Routine].self, from: data)) ?? []
        }

        var completion: [UUID: [String: Int]] = [:]
        if let data = defaults.data(forKey: AppGroup.completionKey),
           let c = try? decoder.decode([String: [String: Int]].self, from: data) {
            completion = Dictionary(uniqueKeysWithValues: c.compactMap { key, val in
                UUID(uuidString: key).map { ($0, val) }
            })
        }

        let monthOffset = defaults.integer(forKey: AppGroup.widgetMonthOffsetKey)

        // 앱에서 선택한 달력 스타일(점/막대/링)
        var calendarStyle: CalendarStyle = .dots
        if let sData = defaults.data(forKey: AppGroup.settingsKey),
           let settings = try? decoder.decode(AppSettings.self, from: sData) {
            calendarStyle = settings.calendarStyle
        }

        var friends: [Friend] = []
        if let fData = defaults.data(forKey: AppGroup.friendsKey) {
            friends = (try? decoder.decode([Friend].self, from: fData)) ?? []
        }

        return LockScreenEntry(date: date, routines: routines, completion: completion,
                               monthOffset: monthOffset, calendarStyle: calendarStyle, friends: friends)
    }

    // MARK: - Helpers

    static func isDone(entry: LockScreenEntry, routine: Routine, dateKey: String) -> Bool {
        let count = entry.completion[routine.id]?[dateKey] ?? 0
        return count >= routine.target
    }

    static func dayProgress(entry: LockScreenEntry, dateKey: String) -> (done: Int, total: Int, frac: Double) {
        guard let date = Date.from(dateKey: dateKey) else { return (0, 0, 0) }
        let scheduled = entry.routines.filter { $0.isScheduled(on: date) }
        let total = scheduled.count
        guard total > 0 else { return (0, 0, 0) }
        let done = scheduled.filter { isDone(entry: entry, routine: $0, dateKey: dateKey) }.count
        return (done, total, Double(done) / Double(total))
    }

    static func remainingRoutines(entry: LockScreenEntry, dateKey: String) -> [Routine] {
        guard let date = Date.from(dateKey: dateKey) else { return [] }
        return entry.routines
            .filter { $0.isScheduled(on: date) }
            .filter { !isDone(entry: entry, routine: $0, dateKey: dateKey) }
    }

    /// 오늘 예정된 모든 루틴 (미완료 먼저, 완료는 아래)
    static func scheduledRoutines(entry: LockScreenEntry, dateKey: String) -> [Routine] {
        guard let date = Date.from(dateKey: dateKey) else { return [] }
        return entry.routines
            .filter { $0.isScheduled(on: date) }
            .sorted { a, b in
                let aDone = isDone(entry: entry, routine: a, dateKey: dateKey)
                let bDone = isDone(entry: entry, routine: b, dateKey: dateKey)
                return !aDone && bDone  // 미완료를 앞으로
            }
    }
}
