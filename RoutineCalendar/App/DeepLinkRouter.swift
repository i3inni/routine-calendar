import SwiftUI

/// 외부에서 들어온 딥링크(친구추가)를 화면으로 전달하는 라우터.
/// onOpenURL → 여기에 handle 저장 → MainTabView/FriendsView가 반응.
@Observable
@MainActor
final class DeepLinkRouter {
    /// 친구추가 딥링크로 들어온 상대 ID(handle). 화면이 소비하면 nil로 되돌린다.
    var pendingFriendHandle: String?
    /// 위젯 "＋"로 들어온 루틴 추가 요청. 캘린더 화면이 소비하면 false로 되돌린다.
    var pendingAddRoutine = false
    /// 위젯 "자극"으로 들어온 친구 id. 친구 탭에서 해당 친구 자극 시트를 연다.
    var pendingNudgeFriendId: String?

    /// 자극 딥링크(routinecalendar://nudge/FRIENDID)면 id를 세우고 true.
    @discardableResult
    func handleIfNudge(_ url: URL) -> Bool {
        guard url.scheme == Self.scheme, url.host == "nudge" else { return false }
        let id = url.lastPathComponent
        guard !id.isEmpty, id != "nudge", id != "/" else { return false }
        pendingNudgeFriendId = id
        return true
    }

    /// 루틴 추가 딥링크(routinecalendar://add-routine)면 플래그를 세우고 true.
    @discardableResult
    func handleIfAddRoutine(_ url: URL) -> Bool {
        guard url.scheme == Self.scheme, url.host == "add-routine" else { return false }
        pendingAddRoutine = true
        return true
    }

    static let scheme = "routinecalendar"          // 커스텀 스킴 (테스트/폴백용)
    static let addFriendPath = "/add-friend"        // Universal Link 경로

    /// 공유용 딥링크: routinecalendar://add-friend/HANDLE
    /// 커스텀 스킴이라 서버(AASA)·실기기 없이도 앱이 바로 열린다.
    /// (서버 배포 후 Universal Link(https)로 바꾸면 앱 미설치 폴백까지 가능)
    static func addFriendURL(handle: String) -> URL {
        URL(string: "\(scheme)://add-friend/\(handle)")!
    }

    /// 친구추가 링크면 handle을 세팅하고 true.
    /// 지원 형식:
    ///   routinecalendar://add-friend/HANDLE   (커스텀 스킴, 경로)
    ///   routinecalendar://add-friend?id=HANDLE (커스텀 스킴, 쿼리 — 하위호환)
    ///   https://<도메인>/add-friend/HANDLE      (Universal Link, 서버 배포 후)
    @discardableResult
    func handleIfFriendLink(_ url: URL) -> Bool {
        let components = URLComponents(url: url, resolvingAgainstBaseURL: false)
        let isUniversal = url.scheme == "https" && (components?.path.hasPrefix(Self.addFriendPath) ?? false)
        let isCustomScheme = url.scheme == Self.scheme && url.host == "add-friend"
        guard isUniversal || isCustomScheme else { return false }

        // 코드 추출: 쿼리(?id=) 우선, 없으면 경로 마지막 조각(/add-friend/CODE)
        var code = components?.queryItems?.first(where: { $0.name == "id" })?.value
        if (code ?? "").isEmpty {
            let last = url.lastPathComponent
            code = (last.isEmpty || last == "/" || last == "add-friend") ? nil : last
        }
        guard let code, !code.isEmpty else { return false }
        pendingFriendHandle = code.uppercased()
        return true
    }
}
