import Foundation

enum AppTheme: String, Codable, CaseIterable, Sendable {
    case system, light, dark
    var label: String {
        switch self {
        case .system: "시스템"
        case .light:  "라이트"
        case .dark:   "다크"
        }
    }
    var subLabel: String? {
        self == .system ? "기기 설정 따라가기" : nil
    }
}

enum CalendarStyle: String, Codable, CaseIterable, Sendable {
    case dots, bar, ring
    var label: String {
        switch self {
        case .dots: "점"
        case .bar:  "막대"
        case .ring: "링"
        }
    }
    var subLabel: String {
        switch self {
        case .dots: "루틴 개수만큼 점"
        case .bar:  "진행률 막대"
        case .ring: "날짜에 진행률 링"
        }
    }
}

enum CheckStyle: String, Codable, CaseIterable, Sendable {
    case circle, square, ring
    var label: String {
        switch self {
        case .circle: "원형"
        case .square: "사각형"
        case .ring:   "링"
        }
    }
}

enum WidgetStyle: String, Codable, CaseIterable, Sendable {
    case list, ring, combined
    var label: String {
        switch self {
        case .list:     "목록형"
        case .ring:     "링형"
        case .combined: "통합형"
        }
    }
    var subLabel: String {
        switch self {
        case .list:     "남은 루틴 + 연속·오늘"
        case .ring:     "오늘 링 + 연속"
        case .combined: "한 위젯에 모두"
        }
    }
}

struct AppSettings: Codable, Sendable {
    var theme: AppTheme           = .system
    var calendarStyle: CalendarStyle = .dots
    var checkStyle: CheckStyle    = .circle
    var widgetStyle: WidgetStyle  = .list
    var myDisplayName: String     = ""   // 친구에게 표시될 내 이름
    var dayResetHour: Int         = 0    // 하루 리셋 시각(새벽 0~6시). 0 = 자정 기준(기존과 동일)
    var nudgePresets: [String]    = AppSettings.defaultNudgePresets   // 자극하기 빠른 멘트(2개)

    static let defaultNudgePresets = ["얼른 루틴 시작해!", "오늘도 화이팅"]

    init(
        theme: AppTheme = .system,
        calendarStyle: CalendarStyle = .dots,
        checkStyle: CheckStyle = .circle,
        widgetStyle: WidgetStyle = .list,
        myDisplayName: String = "",
        dayResetHour: Int = 0,
        nudgePresets: [String] = AppSettings.defaultNudgePresets
    ) {
        self.theme = theme
        self.calendarStyle = calendarStyle
        self.checkStyle = checkStyle
        self.widgetStyle = widgetStyle
        self.myDisplayName = myDisplayName
        self.dayResetHour = dayResetHour
        self.nudgePresets = nudgePresets
    }

    // 이전 버전 데이터(myDisplayName/dayResetHour/nudgePresets 없음) 호환 디코더
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        theme         = try c.decodeIfPresent(AppTheme.self,      forKey: .theme)         ?? .system
        calendarStyle = try c.decodeIfPresent(CalendarStyle.self, forKey: .calendarStyle) ?? .dots
        checkStyle    = try c.decodeIfPresent(CheckStyle.self,    forKey: .checkStyle)    ?? .circle
        widgetStyle   = try c.decodeIfPresent(WidgetStyle.self,   forKey: .widgetStyle)   ?? .list
        myDisplayName = try c.decodeIfPresent(String.self,        forKey: .myDisplayName) ?? ""
        dayResetHour  = try c.decodeIfPresent(Int.self,           forKey: .dayResetHour)  ?? 0
        nudgePresets  = try c.decodeIfPresent([String].self,      forKey: .nudgePresets)  ?? AppSettings.defaultNudgePresets
    }
}

/// 하루 리셋 시각(새벽 N시)을 반영한 '논리적 날짜' 계산. 앱·위젯 공통.
/// resetHour=0이면 기존 자정 기준과 동일. 리셋 시각 이전 시간대는 전날로 친다.
/// (예: 리셋 4시면 새벽 2시는 아직 '어제', 새벽 5시부터 '오늘')
enum DayBoundary {
    /// App Group에 공유된 현재 리셋 시각(0~6). SettingsStore가 가벼운 Int로 기록.
    static var resetHour: Int { AppGroup.defaults.integer(forKey: AppGroup.dayResetHourKey) }

    static func dateKey(for date: Date, resetHour: Int) -> String {
        let shifted = Calendar.gregorianSunday.date(byAdding: .hour, value: -resetHour, to: date) ?? date
        return shifted.dateKey
    }

    static func todayKey(resetHour: Int) -> String { dateKey(for: Date(), resetHour: resetHour) }

    /// 공유 리셋 시각 기준의 오늘 dateKey.
    static var todayKey: String { todayKey(resetHour: resetHour) }

    /// 해당 dateKey가 '오늘'인지. (yyyy-MM-dd 문자열은 사전순=시간순)
    static func isToday(_ key: String) -> Bool { key == todayKey }

    /// 해당 dateKey가 미래(오늘 이후)인지.
    static func isFuture(_ key: String) -> Bool { key > todayKey }
}
