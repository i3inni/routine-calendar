import Foundation

/// APNs 디바이스 토큰을 보관했다가, 로그인된 상태에서 서버에 등록한다.
/// (토큰은 로그인 전에 도착할 수 있으므로 분리해 둔다.)
final class DeviceTokenCenter: @unchecked Sendable {
    static let shared = DeviceTokenCenter()

    private let lock = NSLock()
    private var token: String?

    private init() {}

    /// AppDelegate가 APNs 토큰을 받으면 호출.
    func update(_ token: String) {
        lock.lock(); self.token = token; lock.unlock()
        registerIfPossible()
    }

    /// 로그인 직후 호출 — 보관 중인 토큰이 있으면 서버에 등록.
    func registerIfPossible() {
        lock.lock(); let token = self.token; lock.unlock()
        guard let token, APIClient.shared.hasRefreshToken else { return }
        Task { try? await APIClient.shared.registerDeviceToken(token) }
    }
}
