import Foundation
import Observation
import WidgetKit

@Observable
@MainActor
final class RoutineStore {
    var routines: [Routine] = []
    private(set) var completion: [UUID: [String: Int]] = [:]

    /// 루틴/완료가 바뀔 때 호출 (서버에 오늘 요약 업로드). 앱 진입점에서 연결.
    var onDataChanged: (() -> Void)?

    init() { load() }

    // MARK: - CRUD

    func addRoutine(_ routine: Routine) {
        routines.append(routine)
        save()
    }

    func updateRoutine(_ routine: Routine) {
        guard let idx = routines.firstIndex(where: { $0.id == routine.id }) else { return }
        routines[idx] = routine
        save()
    }

    func deleteRoutine(_ routine: Routine) {
        routines.removeAll { $0.id == routine.id }
        completion.removeValue(forKey: routine.id)
        save()
    }

    // MARK: - Completion

    func getCount(_ routineId: UUID, _ dateKey: String) -> Int {
        completion[routineId]?[dateKey] ?? 0
    }

    func isDone(_ routine: Routine, _ dateKey: String) -> Bool {
        getCount(routine.id, dateKey) >= routine.target
    }

    func toggle(_ routine: Routine, _ dateKey: String) {
        let current = getCount(routine.id, dateKey)
        let next: Int
        if routine.type == .check {
            next = current > 0 ? 0 : 1
        } else {
            next = current >= routine.target ? 0 : current + 1
        }
        if completion[routine.id] == nil { completion[routine.id] = [:] }
        completion[routine.id]![dateKey] = next
        save()
    }

    // MARK: - Selectors

    /// 해당 날짜에 예정된 루틴만 반환
    func scheduledRoutines(for dateKey: String) -> [Routine] {
        guard let date = Date.from(dateKey: dateKey) else { return routines }
        let weekday = Calendar.gregorianSunday.component(.weekday, from: date) - 1  // 0=일
        return routines.filter { $0.isScheduled(on: weekday) }
    }

    func dayProgress(_ dateKey: String) -> (done: Int, total: Int, frac: Double) {
        let scheduled = scheduledRoutines(for: dateKey)
        let total = scheduled.count
        guard total > 0 else { return (0, 0, 0) }
        let done = scheduled.filter { isDone($0, dateKey) }.count
        return (done, total, Double(done) / Double(total))
    }

    func streak(_ routine: Routine) -> Int {
        var date = Date()
        // 오늘 예정이 아니면 어제부터 카운트
        let todayWd = Calendar.gregorianSunday.component(.weekday, from: date) - 1
        if !routine.isScheduled(on: todayWd) || !isDone(routine, date.dateKey) {
            date = Calendar.gregorianSunday.date(byAdding: .day, value: -1, to: date) ?? date
        }
        var count = 0
        while count < 366 {
            let wd = Calendar.gregorianSunday.component(.weekday, from: date) - 1
            // 예정된 날이 아니면 스킵 (스트릭 유지)
            if !routine.isScheduled(on: wd) {
                date = Calendar.gregorianSunday.date(byAdding: .day, value: -1, to: date) ?? date
                continue
            }
            guard isDone(routine, date.dateKey) else { break }
            count += 1
            date = Calendar.gregorianSunday.date(byAdding: .day, value: -1, to: date) ?? date
        }
        return count
    }

    func week(_ routine: Routine) -> [Bool] {
        (0..<7).map { offset in
            let date = Calendar.gregorianSunday.date(byAdding: .day, value: offset - 6, to: Date()) ?? Date()
            let wd = Calendar.gregorianSunday.component(.weekday, from: date) - 1
            guard routine.isScheduled(on: wd) else { return false }
            return isDone(routine, date.dateKey)
        }
    }

    // MARK: - Persistence

    func save() {
        let encoder = JSONEncoder()
        if let data = try? encoder.encode(routines) {
            AppGroup.defaults.set(data, forKey: AppGroup.routinesKey)
        }
        let stringKeyed = Dictionary(uniqueKeysWithValues: completion.map { ($0.key.uuidString, $0.value) })
        if let data = try? encoder.encode(stringKeyed) {
            AppGroup.defaults.set(data, forKey: AppGroup.completionKey)
        }
        AppGroup.defaults.synchronize()

        // 위젯 갱신
        WidgetCenter.shared.reloadAllTimelines()

        // 오후 9시 스트릭 가드 알림 업데이트 (오늘 예정된 루틴 기준)
        let today = Date().dateKey
        let remaining = scheduledRoutines(for: today).filter { !isDone($0, today) }.count
        NotificationManager.shared.scheduleStreakGuard(remainingCount: remaining)

        // 서버에 오늘 요약 업로드 (연결돼 있으면)
        onDataChanged?()
    }

    // MARK: - 친구 공유용 요약

    /// 오늘 예정된 루틴의 완료/미완료 이름
    func todaySummary() -> (done: [String], remaining: [String]) {
        let today = Date().dateKey
        let scheduled = scheduledRoutines(for: today)
        let done = scheduled.filter { isDone($0, today) }.map(\.name)
        let remaining = scheduled.filter { !isDone($0, today) }.map(\.name)
        return (done, remaining)
    }

    /// 전체 루틴 중 가장 긴 연속 기록 (친구에게 표시될 streak)
    func bestStreak() -> Int {
        routines.map { streak($0) }.max() ?? 0
    }

    private func load() {
        let decoder = JSONDecoder()
        if let data = AppGroup.defaults.data(forKey: AppGroup.routinesKey),
           let loaded = try? decoder.decode([Routine].self, from: data) {
            routines = loaded
        }
        if let data = AppGroup.defaults.data(forKey: AppGroup.completionKey),
           let loaded = try? decoder.decode([String: [String: Int]].self, from: data) {
            completion = Dictionary(uniqueKeysWithValues: loaded.compactMap { key, val in
                UUID(uuidString: key).map { ($0, val) }
            })
        }
    }
}
