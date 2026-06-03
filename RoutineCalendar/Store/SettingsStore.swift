import Foundation
import Observation

@Observable
@MainActor
final class SettingsStore {
    var theme: AppTheme = .system
    var calendarStyle: CalendarStyle = .dots
    var checkStyle: CheckStyle = .circle
    var widgetStyle: WidgetStyle = .list
    var myDisplayName: String = ""

    init() { load() }

    func save() {
        let s = AppSettings(theme: theme, calendarStyle: calendarStyle, checkStyle: checkStyle, widgetStyle: widgetStyle, myDisplayName: myDisplayName)
        if let data = try? JSONEncoder().encode(s) {
            AppGroup.defaults.set(data, forKey: AppGroup.settingsKey)
            AppGroup.defaults.synchronize()
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
    }
}
