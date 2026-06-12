import Foundation

struct Friend: Identifiable, Codable {
    var id: String
    var name: String
    var initial: String
    var doneToday: Int
    var totalToday: Int
    var remaining: [String]    // 오늘 미완료 루틴 이름
    var done: [String]         // 오늘 완료 루틴 이름
    var streak: Int
    var nudgeRemaining: Int = 2       // 이 친구에게 남은 자극 횟수 (0~2)
    var nudgeResetAt: Date? = nil     // 0회일 때 다시 가능해지는 시각

    var isAllDone: Bool { totalToday > 0 && doneToday >= totalToday }

    /// 자극 쿨다운 중인지 (남은 0회 + 리셋시각이 미래)
    var nudgeOnCooldown: Bool {
        nudgeRemaining == 0 && (nudgeResetAt.map { $0 > Date() } ?? false)
    }

    /// 오늘 전체 루틴 (미완료 먼저, 완료 아래) — 잠금화면 목록 스타일 표시용
    var todayRoutines: [(name: String, done: Bool)] {
        remaining.map { ($0, false) } + done.map { ($0, true) }
    }
}
