import Foundation

enum APIConfig {
    /// 서버 API 베이스 URL. 값은 Config.xcconfig(API_BASE_URL) → Info.plist 에서 주입된다.
    static let baseURL: URL = {
        guard let str = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String,
              let url = URL(string: str.trimmingCharacters(in: .whitespaces)),
              url.scheme != nil else {
            fatalError("API_BASE_URL 이 비어있거나 잘못되었습니다. Config.xcconfig 를 확인하세요.")
        }
        return url
    }()
}

// MARK: - 서버 DTO (필요한 필드만; 모르는 키는 Codable이 무시)

struct AuthResponseDTO: Decodable {
    let accessToken: String
    let refreshToken: String
    let user: UserDTO
}

struct UserDTO: Decodable {
    let id: Int64
    let handle: String
    let nickname: String
    let profileImageUrl: String?
}

struct FriendDTO: Decodable {
    let userId: Int64
    let handle: String
    let nickname: String
    let profileImageUrl: String?
    let doneToday: Int
    let totalToday: Int
    let streak: Int
    let done: [String]
    let remaining: [String]
}

struct FriendRequestDTO: Decodable {
    let requestId: Int64
    let fromUserId: Int64
    let fromHandle: String
    let fromNickname: String
    let fromProfileImageUrl: String?
}

struct ConfigDTO: Decodable {
    let pokeCooldownSeconds: Int
}

struct ErrorResponseDTO: Decodable {
    let code: String
    let message: String
}

// MARK: - 에러

enum APIError: Error {
    case unauthorized
    case server(status: Int, code: String?, message: String?)
    case transport(Error)
    case decoding(Error)

    var serverCode: String? {
        if case let .server(_, code, _) = self { return code }
        return nil
    }
}

// MARK: - 클라이언트

final class APIClient: @unchecked Sendable {
    static let shared = APIClient()

    private let tokens = TokenStore.shared
    private let session = URLSession(configuration: .default)
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    private init() {}

    var hasRefreshToken: Bool { tokens.hasRefreshToken }

    // MARK: 인증

    /// 카카오 액세스 토큰 → 우리 서버 로그인 → JWT 발급 + 저장
    func kakaoLogin(kakaoAccessToken: String) async throws -> UserDTO {
        let res: AuthResponseDTO = try await send(
            "POST", "/auth/kakao",
            body: ["kakaoAccessToken": kakaoAccessToken],
            authorized: false
        )
        tokens.save(access: res.accessToken, refresh: res.refreshToken)
        return res.user
    }

    func appleLogin(identityToken: String, name: String?) async throws -> UserDTO {
        var body: [String: Any] = ["identityToken": identityToken]
        if let name, !name.isEmpty { body["name"] = name }
        let res: AuthResponseDTO = try await send(
            "POST", "/auth/apple",
            body: body,
            authorized: false
        )
        tokens.save(access: res.accessToken, refresh: res.refreshToken)
        return res.user
    }

    func devLogin(kakaoId: Int64, nickname: String) async throws -> UserDTO {
        let res: AuthResponseDTO = try await send(
            "POST", "/auth/dev-login",
            body: ["kakaoId": kakaoId, "nickname": nickname] as [String: Any],
            authorized: false
        )
        tokens.save(access: res.accessToken, refresh: res.refreshToken)
        return res.user
    }

    /// 저장된 refresh 토큰으로 자동 로그인. 없거나 만료면 throw.
    func autoLogin() async throws -> UserDTO {
        guard let refresh = tokens.refreshToken else { throw APIError.unauthorized }
        let res: AuthResponseDTO = try await send(
            "POST", "/auth/refresh",
            body: ["refreshToken": refresh],
            authorized: false
        )
        tokens.save(access: res.accessToken, refresh: res.refreshToken)
        return res.user
    }

    func logout() { tokens.clear() }

    /// 서버 설정값(콕 쿨다운 등). 인증 불필요.
    func config() async throws -> ConfigDTO {
        try await send("GET", "/config", authorized: false)
    }

    /// 닉네임(친구에게 보이는 이름) 변경 → 갱신된 내 정보 반환.
    func updateNickname(_ nickname: String) async throws -> UserDTO {
        try await send("PATCH", "/me", body: ["nickname": nickname])
    }

    /// 계정 삭제 예약(3일 유예). 유예 내 재로그인하면 취소됨.
    func deleteAccount() async throws {
        try await sendNoContent("DELETE", "/me")
    }

    // MARK: 친구

    func friends() async throws -> [FriendDTO] {
        try await send("GET", "/me/friends")
    }

    func incomingRequests() async throws -> [FriendRequestDTO] {
        try await send("GET", "/me/friend-requests")
    }

    func sendFriendRequest(handle: String) async throws {
        try await sendNoContent("POST", "/friend-requests", body: ["handle": handle])
    }

    func acceptRequest(_ requestId: Int64) async throws {
        try await sendNoContent("POST", "/friend-requests/\(requestId)/accept")
    }

    func declineRequest(_ requestId: Int64) async throws {
        try await sendNoContent("POST", "/friend-requests/\(requestId)/decline")
    }

    func removeFriend(_ userId: Int64) async throws {
        try await sendNoContent("DELETE", "/me/friends/\(userId)")
    }

    func poke(toUserId: Int64) async throws {
        try await sendNoContent("POST", "/pokes", body: ["toUserId": toUserId])
    }

    func uploadSummary(done: [String], remaining: [String], streak: Int) async throws {
        try await sendNoContent("POST", "/me/summary",
                                body: ["done": done, "remaining": remaining, "streak": streak] as [String: Any])
    }

    func registerDeviceToken(_ token: String) async throws {
        try await sendNoContent("POST", "/me/device-token", body: ["token": token])
    }

    // MARK: - 코어 요청

    private func send<R: Decodable>(_ method: String, _ path: String,
                                    body: Any? = nil, authorized: Bool = true) async throws -> R {
        let data = try await perform(method, path, body: body, authorized: authorized, isRetry: false)
        do {
            return try decoder.decode(R.self, from: data)
        } catch {
            throw APIError.decoding(error)
        }
    }

    private func sendNoContent(_ method: String, _ path: String,
                               body: Any? = nil, authorized: Bool = true) async throws {
        _ = try await perform(method, path, body: body, authorized: authorized, isRetry: false)
    }

    private func perform(_ method: String, _ path: String,
                         body: Any?, authorized: Bool, isRetry: Bool) async throws -> Data {
        var request = URLRequest(url: APIConfig.baseURL.appendingPathComponent(path))
        request.httpMethod = method
        if let body {
            request.setValue("application/json", forHTTPHeaderField: "Content-Type")
            request.httpBody = try JSONSerialization.data(withJSONObject: body)
        }
        if authorized, let access = tokens.accessToken {
            request.setValue("Bearer \(access)", forHTTPHeaderField: "Authorization")
        }

        let data: Data, response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw APIError.transport(error)
        }
        guard let http = response as? HTTPURLResponse else {
            throw APIError.server(status: -1, code: nil, message: "no response")
        }

        // access 만료(401) → refresh 후 1회 재시도
        if http.statusCode == 401, authorized, !isRetry, tokens.hasRefreshToken {
            try await refreshTokens()
            return try await perform(method, path, body: body, authorized: true, isRetry: true)
        }

        guard (200..<300).contains(http.statusCode) else {
            let err = try? decoder.decode(ErrorResponseDTO.self, from: data)
            if http.statusCode == 401 { throw APIError.unauthorized }
            throw APIError.server(status: http.statusCode, code: err?.code, message: err?.message)
        }
        return data
    }

    private func refreshTokens() async throws {
        guard let refresh = tokens.refreshToken else { throw APIError.unauthorized }
        let data = try await perform("POST", "/auth/refresh",
                                     body: ["refreshToken": refresh], authorized: false, isRetry: true)
        guard let res = try? decoder.decode(AuthResponseDTO.self, from: data) else {
            tokens.clear()
            throw APIError.unauthorized
        }
        tokens.save(access: res.accessToken, refresh: res.refreshToken)
    }
}
