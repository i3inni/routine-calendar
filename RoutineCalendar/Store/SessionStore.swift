import Foundation
import Observation

/// 로그인 세션 상태 + 자동 로그인.
///
/// - 자동 로그인: 저장된 refresh 토큰으로 조용히 복원.
/// - 카카오 로그인: 카카오 SDK 토큰 → 서버 /auth/kakao → 우리 JWT.
/// - dev 로그인: 카카오 키 없이 개발/테스트용.
@Observable
@MainActor
final class SessionStore {
    var currentUser: UserDTO?
    var isReady = false          // 부트스트랩(자동 로그인 시도) 완료 여부
    var isLoggingIn = false
    var loginError: String?
    var isOffline = false

    private let devKakaoIdKey = "rc.devKakaoId"

    var myUserId: Int64? { currentUser?.id }
    var isLoggedIn: Bool { currentUser != nil }

    /// 앱 시작 시 1회. 저장된 refresh 토큰이 있으면 자동 로그인 시도.
    func bootstrap() async {
        defer { isReady = true }
        guard APIClient.shared.hasRefreshToken else { return }
        do {
            currentUser = try await APIClient.shared.autoLogin()
            isOffline = false
        } catch APIError.unauthorized {
            APIClient.shared.logout()   // refresh 만료 → 로그인 화면으로
        } catch {
            isOffline = true
        }
    }

    // MARK: - 카카오 로그인

    func loginWithKakao() async {
        isLoggingIn = true
        loginError = nil
        defer { isLoggingIn = false }
        do {
            let kakaoToken = try await KakaoLoginService.login()
            currentUser = try await APIClient.shared.kakaoLogin(kakaoAccessToken: kakaoToken)
            isOffline = false
        } catch {
            loginError = error.localizedDescription
        }
    }

    // MARK: - 개발용 로그인 (카카오 키 없이)

    func devLogin(nickname: String = "테스터") async {
        isLoggingIn = true
        loginError = nil
        defer { isLoggingIn = false }
        do {
            currentUser = try await APIClient.shared.devLogin(
                kakaoId: stableDevKakaoId(), nickname: nickname)
            isOffline = false
        } catch {
            loginError = error.localizedDescription
        }
    }

    func logout() {
        APIClient.shared.logout()
        KakaoLoginService.logout()
        currentUser = nil
    }

    /// 기기마다 고정된 가짜 kakaoId (dev-login용)
    private func stableDevKakaoId() -> Int64 {
        let defaults = UserDefaults.standard
        if let existing = defaults.object(forKey: devKakaoIdKey) as? NSNumber {
            return existing.int64Value
        }
        let id = Int64.random(in: 1_000...9_999_999)
        defaults.set(NSNumber(value: id), forKey: devKakaoIdKey)
        return id
    }
}
