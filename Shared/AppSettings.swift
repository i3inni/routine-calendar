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

    init(
        theme: AppTheme = .system,
        calendarStyle: CalendarStyle = .dots,
        checkStyle: CheckStyle = .circle,
        widgetStyle: WidgetStyle = .list,
        myDisplayName: String = ""
    ) {
        self.theme = theme
        self.calendarStyle = calendarStyle
        self.checkStyle = checkStyle
        self.widgetStyle = widgetStyle
        self.myDisplayName = myDisplayName
    }

    // 이전 버전 데이터(myDisplayName 없음) 호환 디코더
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        theme         = try c.decodeIfPresent(AppTheme.self,      forKey: .theme)         ?? .system
        calendarStyle = try c.decodeIfPresent(CalendarStyle.self, forKey: .calendarStyle) ?? .dots
        checkStyle    = try c.decodeIfPresent(CheckStyle.self,    forKey: .checkStyle)    ?? .circle
        widgetStyle   = try c.decodeIfPresent(WidgetStyle.self,   forKey: .widgetStyle)   ?? .list
        myDisplayName = try c.decodeIfPresent(String.self,        forKey: .myDisplayName) ?? ""
    }
}
