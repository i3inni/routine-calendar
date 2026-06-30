import Foundation
import Observation
import WidgetKit

@Observable
@MainActor
final class SettingsStore {
    var theme: AppTheme = .system
    var calendarStyle: CalendarStyle = .dots
    var checkStyle: CheckStyle = .circle
    var widgetStyle: WidgetStyle = .list
    var myDisplayName: String = ""
    var dayResetHour: Int = 0   // 하루 리셋 시각(새벽 0~6시)
    var nudgePreset1: String = AppSettings.defaultNudgePresets[0]
    var nudgePreset2: String = AppSettings.defaultNudgePresets[1]

    /// 자극하기 시트에 노출할 빈 값 제외 멘트들.
    var nudgePresets: [String] {
        [nudgePreset1, nudgePreset2]
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty }
    }

    init() { load() }

    func save() {
        let s = AppSettings(theme: theme, calendarStyle: calendarStyle, checkStyle: checkStyle,
                            widgetStyle: widgetStyle, myDisplayName: myDisplayName, dayResetHour: dayResetHour,
                            nudgePresets: [nudgePreset1, nudgePreset2])
        if let data = try? JSONEncoder().encode(s) {
            AppGroup.defaults.set(data, forKey: AppGroup.settingsKey)
            // '오늘' 계산용 가벼운 Int(앱/위젯이 매 렌더마다 싸게 읽음)
            AppGroup.defaults.set(dayResetHour, forKey: AppGroup.dayResetHourKey)
            AppGroup.defaults.synchronize()
            WidgetCenter.shared.reloadAllTimelines()   // 스타일/테마/리셋시각 변경을 위젯에 반영
        }
    }

    private func load() {
        guard let data = AppGroup.defaults.data(forKey: AppGroup.settingsKey),
              let s = try? JSONDecoder().decode(AppSettings.self, from: data)
        else { return }
        theme = s.theme
        calendarStyle = s.calendarStyle
        checkStyle = s.checkStyle
        widgetStyle = s.widgetStyle
        myDisplayName = s.myDisplayName
        dayResetHour = s.dayResetHour
        nudgePreset1 = s.nudgePresets.indices.contains(0) ? s.nudgePresets[0] : AppSettings.defaultNudgePresets[0]
        nudgePreset2 = s.nudgePresets.indices.contains(1) ? s.nudgePresets[1] : AppSettings.defaultNudgePresets[1]
        // 가벼운 Int 키도 항상 최신으로 (구버전 캐시 호환)
        AppGroup.defaults.set(dayResetHour, forKey: AppGroup.dayResetHourKey)
    }
}
