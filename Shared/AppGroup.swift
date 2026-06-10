import Foundation

enum AppGroup {
    static let suiteName = "group.com.i3inni.routinecalendar"
    static let routinesKey = "rcal_routines_v2"
    static let completionKey = "rcal_completion_v2"
    static let settingsKey = "rcal_settings_v2"
    /// 마지막으로 동기화한 userId. 계정 전환 감지에 사용. (nil = 동기화 이력 없음 → 마이그레이션 대상)
    static let lastSyncedUserKey = "rcal_last_synced_user"

    static var defaults: UserDefaults {
        UserDefaults(suiteName: suiteName) ?? .standard
    }
}
