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

    /// 로그인+초기 동기화 완료 후 true. 이후의 로컬 변경은 서버로 push된다.
    private var syncEnabled = false

    init() { load() }

    // MARK: - CRUD

    func addRoutine(_ routine: Routine) {
        routines.append(routine)
        save()
        push { try await APIClient.shared.createRoutine(routine) }
    }

    func updateRoutine(_ routine: Routine) {
        guard let idx = routines.firstIndex(where: { $0.id == routine.id }) else { return }
        routines[idx] = routine
        save()
        push { try await APIClient.shared.updateRoutine(routine) }
    }

    func deleteRoutine(_ routine: Routine) {
        routines.removeAll { $0.id == routine.id }
        completion.removeValue(forKey: routine.id)
        save()
        push { try await APIClient.shared.deleteRoutine(id: routine.id) }
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
        let routineId = routine.id
        push { try await APIClient.shared.setCompletion(routineId: routineId, date: dateKey, count: next) }
    }

    /// 위젯(AppIntent)이 App Group에 반영한 완료를 앱 메모리로 다시 읽어온다.
    /// 포그라운드 복귀 시 호출 → 홈 위젯에서 체크한 게 앱에도 보이고, 오늘 요약도 갱신.
    func reloadCompletionFromAppGroup() {
        let decoder = JSONDecoder()
        guard let data = AppGroup.defaults.data(forKey: AppGroup.completionKey),
              let loaded = try? decoder.decode([String: [String: Int]].self, from: data) else { return }
        let merged = Dictionary(uniqueKeysWithValues: loaded.compactMap { key, val in
            UUID(uuidString: key).map { ($0, val) }
        })
        guard merged != completion else { return }   // 변화 없으면 무시
        completion = merged
        onDataChanged?()   // 위젯 변경분으로 친구 공유용 오늘 요약 재업로드
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

    // MARK: - 서버 동기화

    /// 로그인 직후 1회 호출. 계정 전환 감지 → 서버 상태 채택(또는 기존 로컬 마이그레이션).
    func syncOnLogin(userId: Int64) async {
        let last = (AppGroup.defaults.object(forKey: AppGroup.lastSyncedUserKey) as? NSNumber)?.int64Value
        let isFirstEverSync = (last == nil)
        let isAccountSwitch = (last != nil && last != userId)

        // 계정이 바뀌면 이전 계정의 로컬 데이터/알림을 비운다 (계정별 분리)
        if isAccountSwitch {
            for r in routines { NotificationManager.shared.cancel(for: r.id) }
            routines = []
            completion = [:]
            persistCache()
        }

        do {
            async let rTask = APIClient.shared.routines()
            async let cTask = APIClient.shared.completions()
            let serverRoutines = try await rTask
            let serverCompletions = try await cTask

            if isFirstEverSync && serverRoutines.isEmpty && !routines.isEmpty {
                // 기존(로컬 전용) 사용자의 첫 동기화 → 로컬을 서버로 업로드
                syncEnabled = true
                await pushAllLocal()
            } else {
                // 서버를 원본으로 채택
                routines = serverRoutines.map(Routine.init(dto:))
                completion = Self.buildCompletion(serverCompletions)
                persistCache()
                rescheduleAllReminders()
                updateStreakGuard()
                syncEnabled = true
            }
            AppGroup.defaults.set(NSNumber(value: userId), forKey: AppGroup.lastSyncedUserKey)
            onDataChanged?()   // 동기화된 상태로 오늘 요약 갱신
        } catch {
            // 오프라인 등: 로컬 유지. 이후 변경은 push 시도.
            syncEnabled = true
        }
    }

    /// 로컬 전체를 서버로 업로드 (마이그레이션용).
    private func pushAllLocal() async {
        for r in routines {
            try? await APIClient.shared.createRoutine(r)
        }
        for (routineId, dates) in completion {
            for (dateKey, count) in dates where count > 0 {
                try? await APIClient.shared.setCompletion(routineId: routineId, date: dateKey, count: count)
            }
        }
    }

    /// 동기화 활성 시에만 서버로 비동기 push (optimistic, 실패는 무시).
    private func push(_ op: @escaping @Sendable () async throws -> Void) {
        guard syncEnabled else { return }
        Task { try? await op() }
    }

    private func rescheduleAllReminders() {
        for r in routines { NotificationManager.shared.schedule(for: r) }
    }

    private static func buildCompletion(_ dtos: [CompletionDTO]) -> [UUID: [String: Int]] {
        var result: [UUID: [String: Int]] = [:]
        for d in dtos { result[d.routineId, default: [:]][d.date] = d.count }
        return result
    }

    // MARK: - Persistence

    func save() {
        persistCache()
        updateStreakGuard()
        // 서버에 오늘 요약 업로드 (연결돼 있으면)
        onDataChanged?()
    }

    /// 로컬 캐시(App Group) + 위젯만 갱신. 동기화 중 요약 재업로드 루프를 피하기 위해 분리.
    private func persistCache() {
        let encoder = JSONEncoder()
        if let data = try? encoder.encode(routines) {
            AppGroup.defaults.set(data, forKey: AppGroup.routinesKey)
        }
        let stringKeyed = Dictionary(uniqueKeysWithValues: completion.map { ($0.key.uuidString, $0.value) })
        if let data = try? encoder.encode(stringKeyed) {
            AppGroup.defaults.set(data, forKey: AppGroup.completionKey)
        }
        AppGroup.defaults.synchronize()
        WidgetCenter.shared.reloadAllTimelines()
    }

    /// 오후 9시 스트릭 가드 알림 업데이트 (오늘 예정된 루틴 기준)
    private func updateStreakGuard() {
        let today = Date().dateKey
        let remaining = scheduledRoutines(for: today).filter { !isDone($0, today) }.count
        NotificationManager.shared.scheduleStreakGuard(remainingCount: remaining)
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

// MARK: - 서버 DTO → 화면 모델 매핑

private extension Routine {
    init(dto: RoutineDTO) {
        self.init(
            id: dto.id,
            name: dto.name,
            type: RoutineType(rawValue: dto.type) ?? .check,
            target: dto.target,
            unit: dto.unit,
            reminder: dto.reminder,
            anytime: dto.anytime,
            repeatMode: RepeatMode(rawValue: dto.repeatMode) ?? .daily,
            repeatDays: dto.repeatDays
        )
    }
}
