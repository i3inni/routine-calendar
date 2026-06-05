import Foundation

enum AppGroup {
    static let suiteName = "group.com.i3inni.routinecalendar"
    static let routinesKey = "rcal_routines_v2"
    static let completionKey = "rcal_completion_v2"
    static let settingsKey = "rcal_settings_v2"

    static var defaults: UserDefaults {
        UserDefaults(suiteName: suiteName) ?? .standard
    }
}
