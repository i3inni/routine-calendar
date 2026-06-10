import Foundation
import KakaoSDKAuth
import KakaoSDKUser

enum KakaoLoginError: LocalizedError {
    case notConfigured
    case noToken

    var errorDescription: String? {
        switch self {
        case .notConfigured: return "카카오 앱 키가 설정되지 않았어요."
        case .noToken:       return "카카오 토큰을 받지 못했어요."
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

    /// '친구 목록(friends)' 권한을 포함해 로그인 → 카카오 액세스 토큰 반환.
    /// (카카오 친구 찾기용. 미동의 시 SDK가 동의창을 띄운다.)
    @MainActor
    static func loginForFriends() async throws -> String {
        guard KakaoConfig.isConfigured else { throw KakaoLoginError.notConfigured }

        return try await withCheckedThrowingContinuation { continuation in
            UserApi.shared.loginWithKakaoAccount(scopes: ["friends", "profile_nickname"]) { token, error in
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
