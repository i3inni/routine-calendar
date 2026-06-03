import Foundation
import WidgetKit

struct LockScreenEntry: TimelineEntry {
    let date: Date
    let routines: [Routine]
    let completion: [UUID: [String: Int]]
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

        return LockScreenEntry(date: date, routines: routines, completion: completion)
    }

    // MARK: - Helpers

    static func isDone(entry: LockScreenEntry, routine: Routine, dateKey: String) -> Bool {
        let count = entry.completion[routine.id]?[dateKey] ?? 0
        return count >= routine.target
    }

    static func dayProgress(entry: LockScreenEntry, dateKey: String) -> (done: Int, total: Int, frac: Double) {
        guard let date = Date.from(dateKey: dateKey) else { return (0, 0, 0) }
        let weekday = Calendar.gregorianSunday.component(.weekday, from: date) - 1
        let scheduled = entry.routines.filter { $0.isScheduled(on: weekday) }
        let total = scheduled.count
        guard total > 0 else { return (0, 0, 0) }
        let done = scheduled.filter { isDone(entry: entry, routine: $0, dateKey: dateKey) }.count
        return (done, total, Double(done) / Double(total))
    }

    static func remainingRoutines(entry: LockScreenEntry, dateKey: String) -> [Routine] {
        guard let date = Date.from(dateKey: dateKey) else { return [] }
        let weekday = Calendar.gregorianSunday.component(.weekday, from: date) - 1
        return entry.routines
            .filter { $0.isScheduled(on: weekday) }
            .filter { !isDone(entry: entry, routine: $0, dateKey: dateKey) }
    }

    /// 오늘 예정된 모든 루틴 (미완료 먼저, 완료는 아래)
    static func scheduledRoutines(entry: LockScreenEntry, dateKey: String) -> [Routine] {
        guard let date = Date.from(dateKey: dateKey) else { return [] }
        let weekday = Calendar.gregorianSunday.component(.weekday, from: date) - 1
        return entry.routines
            .filter { $0.isScheduled(on: weekday) }
            .sorted { a, b in
                let aDone = isDone(entry: entry, routine: a, dateKey: dateKey)
                let bDone = isDone(entry: entry, routine: b, dateKey: dateKey)
                return !aDone && bDone  // 미완료를 앞으로
            }
    }
}
