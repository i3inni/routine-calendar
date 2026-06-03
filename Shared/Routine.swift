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

    // 해당 요일에 이 루틴이 예정됐는지 (weekday: 0=일)
    func isScheduled(on weekday: Int) -> Bool {
        switch repeatMode {
        case .daily:    return true
        case .weekdays: return (1...5).contains(weekday)
        case .custom:   return repeatDays.contains(weekday)
        }
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
        repeatDays: [Int] = []
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
    }

    // 이전 버전 데이터(repeatMode/repeatDays 없음) 호환 디코더
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
    }
}
