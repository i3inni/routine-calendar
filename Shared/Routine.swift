import Foundation

enum RoutineType: String, Codable, CaseIterable, Sendable {
    case check
    case count
}

enum RepeatMode: String, Codable, CaseIterable, Sendable {
    case daily     // 매일
    case weekdays  // 주간 (평일 월-금)
    case custom    // 직접 선택

    var label: String {
        switch self {
        case .daily:    return "매일"
        case .weekdays: return "주간"
        case .custom:   return "직접 선택"
        }
    }

    var defaultDays: [Int] {
        switch self {
        case .daily:    return Array(0...6)          // 모든 요일
        case .weekdays: return [1, 2, 3, 4, 5]       // 월~금
        case .custom:   return []
        }
    }
}

struct Routine: Identifiable, Codable, Equatable, Sendable {
    var id: UUID
    var name: String
    var type: RoutineType
    var target: Int
    var unit: String
    var reminder: String?   // "HH:MM" or nil
    var anytime: Bool
    var repeatMode: RepeatMode
    var repeatDays: [Int]   // 0=일 1=월 … 6=토 (custom 모드에서 사용)
    var createdAt: Date     // 시작일: 이 날짜(당일 포함)부터 루틴이 노출된다
    var endDate: Date?      // 종료일: 이 날짜(당일 포함)부터 루틴이 사라진다 (nil = 무기한). 이전 기록은 보존.

    // 해당 요일에 이 루틴이 예정됐는지 (weekday: 0=일)
    func isScheduled(on weekday: Int) -> Bool {
        switch repeatMode {
        case .daily:    return true
        case .weekdays: return (1...5).contains(weekday)
        case .custom:   return repeatDays.contains(weekday)
        }
    }

    // 해당 날짜에 이 루틴이 예정됐는지. 생성일 이전, 종료일 당일/이후 날짜에는 표시하지 않는다.
    func isScheduled(on date: Date) -> Bool {
        let cal = Calendar.gregorianSunday
        let targetDay = cal.startOfDay(for: date)
        let createdDay = cal.startOfDay(for: createdAt)
        guard targetDay >= createdDay else { return false }
        if let endDate, targetDay >= cal.startOfDay(for: endDate) { return false }

        let weekday = cal.component(.weekday, from: targetDay) - 1
        return isScheduled(on: weekday)
    }

    init(
        id: UUID = UUID(),
        name: String,
        type: RoutineType = .check,
        target: Int = 1,
        unit: String = "",
        reminder: String? = nil,
        anytime: Bool = true,
        repeatMode: RepeatMode = .daily,
        repeatDays: [Int] = [],
        createdAt: Date = Date(),
        endDate: Date? = nil
    ) {
        self.id = id
        self.name = name
        self.type = type
        self.target = target
        self.unit = unit
        self.reminder = reminder
        self.anytime = anytime
        self.repeatMode = repeatMode
        self.repeatDays = repeatDays
        self.createdAt = createdAt
        self.endDate = endDate
    }

    // 이전 버전 데이터(repeatMode/repeatDays/createdAt 없음) 호환 디코더
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id         = try c.decode(UUID.self,        forKey: .id)
        name       = try c.decode(String.self,      forKey: .name)
        type       = try c.decode(RoutineType.self, forKey: .type)
        target     = try c.decode(Int.self,         forKey: .target)
        unit       = try c.decode(String.self,      forKey: .unit)
        reminder   = try c.decodeIfPresent(String.self,     forKey: .reminder)
        anytime    = try c.decode(Bool.self,        forKey: .anytime)
        repeatMode = try c.decodeIfPresent(RepeatMode.self, forKey: .repeatMode) ?? .daily
        repeatDays = try c.decodeIfPresent([Int].self,      forKey: .repeatDays) ?? []
        createdAt  = try c.decodeIfPresent(Date.self,        forKey: .createdAt) ?? .distantPast
        endDate    = try c.decodeIfPresent(Date.self,        forKey: .endDate)
    }
}
