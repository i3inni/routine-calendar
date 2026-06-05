import Foundation
import Observation
import AuthenticationServices

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

    // MARK: - 애플 로그인

    func loginWithApple(_ result: Result<ASAuthorization, Error>) async {
        loginError = nil
        switch result {
        case .failure(let error):
            // 사용자가 취소한 경우는 에러로 표시하지 않음
            if (error as? ASAuthorizationError)?.code == .canceled { return }
            loginError = error.localizedDescription
        case .success(let auth):
            guard let credential = auth.credential as? ASAuthorizationAppleIDCredential,
                  let tokenData = credential.identityToken,
                  let identityToken = String(data: tokenData, encoding: .utf8) else {
                loginError = "애플 로그인 정보를 가져오지 못했어요."
                return
            }
            isLoggingIn = true
            defer { isLoggingIn = false }
            // 이름은 최초 로그인 때만 들어온다.
            let name = credential.fullName.flatMap(Self.formatName)
            do {
                currentUser = try await APIClient.shared.appleLogin(identityToken: identityToken, name: name)
                isOffline = false
            } catch {
                loginError = error.localizedDescription
            }
        }
    }

    private static func formatName(_ comps: PersonNameComponents) -> String? {
        let formatter = PersonNameComponentsFormatter()
        let s = formatter.string(from: comps).trimmingCharacters(in: .whitespaces)
        return s.isEmpty ? nil : s
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

    // MARK: - 계정 삭제 (3일 유예, 재로그인 시 취소)

    /// 계정 삭제를 예약하고 로그아웃. 성공 시 true.
    @discardableResult
    func deleteAccount() async -> Bool {
        do {
            try await APIClient.shared.deleteAccount()
            logout()
            return true
        } catch {
            loginError = error.localizedDescription
            return false
        }
    }

    // MARK: - 닉네임 변경 (친구에게 보이는 이름)

    /// 서버에 닉네임을 저장하고 currentUser를 갱신. 성공 시 true.
    @discardableResult
    func updateNickname(_ nickname: String) async -> Bool {
        let trimmed = nickname.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        do {
            currentUser = try await APIClient.shared.updateNickname(trimmed)
            return true
        } catch {
            loginError = error.localizedDescription
            return false
        }
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
