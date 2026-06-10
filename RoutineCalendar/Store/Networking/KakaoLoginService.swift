import Foundation
import KakaoSDKAuth
import KakaoSDKCommon
import KakaoSDKUser

enum KakaoLoginError: LocalizedError {
    case notConfigured
    case noToken
    case sdk(String)

    var errorDescription: String? {
        switch self {
        case .notConfigured: return "카카오 앱 키가 설정되지 않았어요."
        case .noToken:       return "카카오 토큰을 받지 못했어요."
        case .sdk(let msg):  return msg
        }
    }
}

/// 카카오 SDK 로그인 래퍼. 카카오톡 앱 로그인 → 불가 시 카카오계정 웹 로그인.
/// 성공 시 카카오 액세스 토큰 문자열을 돌려준다 (서버 /auth/kakao 로 전달).
enum KakaoLoginService {

    @MainActor
    static func login() async throws -> String {
        guard KakaoConfig.isConfigured else { throw KakaoLoginError.notConfigured }

        return try await withCheckedThrowingContinuation { continuation in
            let handler: (OAuthToken?, Error?) -> Void = { token, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let token {
                    continuation.resume(returning: token.accessToken)
                } else {
                    continuation.resume(throwing: KakaoLoginError.noToken)
                }
            }

            if UserApi.isKakaoTalkLoginAvailable() {
                UserApi.shared.loginWithKakaoTalk(completion: handler)
            } else {
                UserApi.shared.loginWithKakaoAccount(completion: handler)
            }
        }
    }

    /// 카카오 친구 찾기용 로그인. 애플로 들어온 유저는 카카오 세션이 없으므로
    /// ① 기본 로그인으로 세션을 만들고 → ② friends 권한이 없으면 추가 동의를 받는다.
    /// 최종적으로 친구목록 권한이 포함된 액세스 토큰을 반환.
    @MainActor
    static func loginForFriends() async throws -> String {
        guard KakaoConfig.isConfigured else { throw KakaoLoginError.notConfigured }

        do {
            // ① 카카오 세션이 없을 때만 기본 로그인 (있으면 매번 로그인창 안 띄움)
            var didLogin = false
            if !AuthApi.hasToken() {
                _ = try await login()
                didLogin = true
            }

            // ② friends 권한이 이미 동의돼 있고 토큰이 있으면 그대로 사용 (재동의 생략)
            if try await hasScope("friends"), let token = cachedAccessToken() {
                return token
            }
            // KakaoTalk 앱에서 막 복귀한 직후엔 웹 인증 세션의 표시 윈도우가
            // 아직 준비되지 않아 실패할 수 있어, 잠깐 대기 후 추가 동의를 띄운다.
            if didLogin {
                try? await Task.sleep(nanoseconds: 500_000_000)
            }
            return try await consent(scopes: ["friends", "profile_nickname"])
        } catch {
            // SdkError의 실제 사유(ClientFailed/ApiFailed/AuthFailed + reason)를 노출
            throw KakaoLoginError.sdk(Self.describe(error))
        }
    }

    /// 캐시된 카카오 액세스 토큰 (hasScope 호출 직후라 유효).
    private static func cachedAccessToken() -> String? {
        TokenManagerProvider.shared.manager.getToken()?.accessToken
    }

    /// 카카오 SdkError의 구체 사유를 사람이 읽을 수 있는 문자열로.
    private static func describe(_ error: Error) -> String {
        guard let e = error as? SdkError else { return error.localizedDescription }
        switch e {
        case .ClientFailed(let reason, let msg):
            return "ClientFailed(\(reason))\(msg.map { " - \($0)" } ?? "")"
        case .ApiFailed(let reason, _):
            return "ApiFailed(\(reason))"
        case .AuthFailed(let reason, _):
            return "AuthFailed(\(reason))"
        default:
            return String(describing: e)
        }
    }

    /// 특정 동의항목이 이미 동의됐는지 확인.
    @MainActor
    private static func hasScope(_ id: String) async throws -> Bool {
        try await withCheckedThrowingContinuation { continuation in
            UserApi.shared.scopes { info, error in
                if let error {
                    continuation.resume(throwing: error)
                } else {
                    let agreed = info?.scopes?.contains { $0.id == id && $0.agreed } ?? false
                    continuation.resume(returning: agreed)
                }
            }
        }
    }

    /// 추가 동의항목(scopes) 요청 → 갱신된 액세스 토큰 반환.
    @MainActor
    private static func consent(scopes: [String]) async throws -> String {
        try await withCheckedThrowingContinuation { continuation in
            UserApi.shared.loginWithKakaoAccount(scopes: scopes) { token, error in
                if let error {
                    continuation.resume(throwing: error)
                } else if let token {
                    continuation.resume(returning: token.accessToken)
                } else {
                    continuation.resume(throwing: KakaoLoginError.noToken)
                }
            }
        }
    }

    static func logout() {
        UserApi.shared.logout { _ in }
    }
}
