import UserNotifications

@MainActor
final class NotificationManager {
    static let shared = NotificationManager()

    // MARK: - Permission

    func requestPermission() async {
        _ = try? await UNUserNotificationCenter.current()
            .requestAuthorization(options: [.alert, .sound, .badge])
    }

    // MARK: - 루틴 리마인더
    //
    // 루틴 알림은 **서버 푸시**(ReminderScheduler)가 보낸다 — 완료 여부를 발송 시점에
    // 체크해 "완료했으면 안 보냄"이 가능하기 때문. 로컬 예약은 쓰지 않는다(중복 방지).
    // cancel(for:)는 구버전에서 예약해둔 로컬 반복 알림을 청소하는 용도로만 남긴다.

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
