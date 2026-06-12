import Foundation

enum AppGroup {
    static let suiteName = "group.com.i3inni.routinecalendar"
    static let routinesKey = "rcal_routines_v2"
    static let completionKey = "rcal_completion_v2"
    static let settingsKey = "rcal_settings_v2"
    /// 마지막으로 동기화한 userId. 계정 전환 감지에 사용. (nil = 동기화 이력 없음 → 마이그레이션 대상)
    static let lastSyncedUserKey = "rcal_last_synced_user"
    /// 서버 API 베이스 URL. 앱이 기록 → 위젯이 읽어 서버에 완료 push.
    static let apiBaseURLKey = "rcal_api_base_url"
    /// 홈 위젯 미니 달력이 표시 중인 달 오프셋(0=이번 달, -1=지난 달 …).
    static let widgetMonthOffsetKey = "rcal_widget_month_offset"
    /// 친구 위젯용 친구 현황 스냅샷([Friend]). 앱이 친구 새로고침 시 기록.
    static let friendsKey = "rcal_friends_v1"

    static var defaults: UserDefaults {
        UserDefaults(suiteName: suiteName) ?? .standard
    }
}
