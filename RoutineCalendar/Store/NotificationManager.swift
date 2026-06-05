import UserNotifications

@MainActor
final class NotificationManager {
    static let shared = NotificationManager()

    // MARK: - Permission

    func requestPermission() async {
        _ = try? await UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound, .badge])
    }

    // MARK: - 루틴 리마인더 (매일 반복)

    func schedule(for routine: Routine) {
        cancel(for: routine.id)
        guard let timeStr = routine.reminder, !routine.anytime,
              let (hour, minute) = parseHHMM(timeStr) else { return }

        let content = UNMutableNotificationContent()
        content.title = routine.name
        content.body = "오늘 이 루틴을 완료할 시간이에요"
        content.sound = .default
        content.interruptionLevel = .timeSensitive
        attachIcon(to: content)

        var dc = DateComponents()
        dc.hour = hour
        dc.minute = minute
        let trigger = UNCalendarNotificationTrigger(dateMatching: dc, repeats: true)
        let request = UNNotificationRequest(
            identifier: routine.id.uuidString,
            content: content,
            trigger: trigger
        )
        UNUserNotificationCenter.current().add(request)
    }

    func cancel(for routineId: UUID) {
        UNUserNotificationCenter.current()
            .removePendingNotificationRequests(withIdentifiers: [routineId.uuidString])
    }

    // MARK: - 콕 찌르기 수신 알림
    // 서버 연결 전: FriendsStore.poke() 호출 시 로컬 알림으로 수신 시뮬레이션
    // MARK: - 야간 스트릭 지킴이 (오후 9시, 오늘 루틴 미완료 시)

    func scheduleStreakGuard(remainingCount: Int) {
        let id = "streak_guard"
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [id])
        guard remainingCount > 0 else { return }

        let content = UNMutableNotificationContent()
        content.title = "오늘의 연속 기록을 지켜요"
        content.body = "아직 \(remainingCount)개가 남았어요. 자정 전에 완료해 보세요"
        content.sound = .default
        attachIcon(to: content)

        var dc = DateComponents()
        dc.hour = 21
        dc.minute = 0
        let trigger = UNCalendarNotificationTrigger(dateMatching: dc, repeats: false)
        let request = UNNotificationRequest(identifier: id, content: content, trigger: trigger)
        UNUserNotificationCenter.current().add(request)
    }

    // MARK: - Helpers

    /// 알림에 앱 아이콘(인터로킹 링) 썸네일 첨부
    private func attachIcon(to content: UNMutableNotificationContent) {
        guard let url = Bundle.main.url(forResource: "notif_icon", withExtension: "png"),
              let attachment = try? UNNotificationAttachment(
                identifier: "appIcon",
                url: url,
                options: nil
              )
        else { return }
        content.attachments = [attachment]
    }

    private func parseHHMM(_ s: String) -> (Int, Int)? {
        let parts = s.split(separator: ":").compactMap { Int($0) }
        guard parts.count == 2 else { return nil }
        return (parts[0], parts[1])
    }
}
