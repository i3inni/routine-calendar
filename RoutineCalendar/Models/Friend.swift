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
    var lastPokedAt: Date?     // 내가 마지막으로 콕한 시각(서버 기준, 쿨다운 표시용)

    var isAllDone: Bool { totalToday > 0 && doneToday >= totalToday }

    /// 오늘 전체 루틴 (미완료 먼저, 완료 아래) — 잠금화면 목록 스타일 표시용
    var todayRoutines: [(name: String, done: Bool)] {
        remaining.map { ($0, false) } + done.map { ($0, true) }
    }
}
