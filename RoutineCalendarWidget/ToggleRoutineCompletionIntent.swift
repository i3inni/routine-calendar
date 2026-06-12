import AppIntents
import WidgetKit
import Foundation

/// 홈 위젯에서 루틴 완료를 토글하는 인터랙티브 인텐트 (iOS 17+).
/// ① App Group 완료 토글(즉시 반영) ② 서버에 직접 push ③ 위젯 리로드. 앱을 열지 않는다.
@available(iOS 17.0, *)
struct ToggleRoutineCompletionIntent: AppIntent {
    static var title: LocalizedStringResource = "루틴 완료 토글"
    static var openAppWhenRun: Bool = false

    @Parameter(title: "routineId")
    var routineId: String

    init() {}
    init(routineId: String) { self.routineId = routineId }

    func perform() async throws -> some IntentResult {
        guard let uuid = UUID(uuidString: routineId) else { return .result() }

        // App Group에서 이 루틴의 target/type 읽기
        let entry = WidgetDataReader.readEntry()
        guard let routine = entry.routines.first(where: { $0.id == uuid }) else { return .result() }

        let dateKey = WidgetSync.todayKey
        let next = WidgetSync.toggleInAppGroup(
            routineId: uuid,
            target: routine.target,
            isCount: routine.type == .count,
            dateKey: dateKey
        )
        await WidgetSync.setCompletionOnServer(routineId: uuid, dateKey: dateKey, count: next)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

/// 홈 위젯 미니 달력의 표시 월을 바꾼다(`<`/`>`/오늘). App Group에 오프셋 저장 후 리로드.
@available(iOS 17.0, *)
struct SetMonthOffsetIntent: AppIntent {
    static var title: LocalizedStringResource = "달력 월 이동"
    static var openAppWhenRun: Bool = false

    @Parameter(title: "offset")
    var offset: Int

    init() {}
    init(offset: Int) { self.offset = offset }

    func perform() async throws -> some IntentResult {
        AppGroup.defaults.set(offset, forKey: AppGroup.widgetMonthOffsetKey)
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}

/// 친구 위젯에서 친구를 자극(기본 멘트). 서버 push 후 낙관적 남은횟수 감소 + 리로드.
@available(iOS 17.0, *)
struct NudgeFriendIntent: AppIntent {
    static var title: LocalizedStringResource = "친구 자극하기"
    static var openAppWhenRun: Bool = false

    @Parameter(title: "friendId")
    var friendId: String

    init() {}
    init(friendId: String) { self.friendId = friendId }

    func perform() async throws -> some IntentResult {
        let ok = await WidgetSync.nudgeFriendOnServer(userId: friendId, message: "오늘 루틴 했어? 🔥")
        if ok { WidgetSync.optimisticallyDecrementNudge(friendId: friendId) }
        WidgetCenter.shared.reloadAllTimelines()
        return .result()
    }
}
