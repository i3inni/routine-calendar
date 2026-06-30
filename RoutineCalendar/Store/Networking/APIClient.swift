import Foundation

enum APIConfig {
    /// 서버 API 베이스 URL. 값은 Config.xcconfig(API_BASE_URL) → Info.plist 에서 주입된다.
    ///
    /// DEBUG 빌드(시뮬레이터)에서는 로컬 서버에 붙는다. 로컬은 dev-login이 켜져 있어
    /// "개발용 로그인"으로 소셜 로그인 없이 바로 들어갈 수 있다. (`./gradlew bootRun`)
    /// 실기기로 테스트하려면 localhost 대신 Mac의 LAN IP(예: http://192.168.x.x:8080)로 바꾼다.
    static let baseURL: URL = {
        #if DEBUG
        // 개발 서버 주소는 DEV_API_BASE_URL(Config/Secrets.xcconfig)에서 주입.
        // 기본값 localhost(시뮬레이터용), 실기기 테스트 시 Secrets에서 Mac LAN IP로 덮어쓴다.
        if let str = Bundle.main.object(forInfoDictionaryKey: "DEV_API_BASE_URL") as? String,
           let url = URL(string: str.trimmingCharacters(in: .whitespaces)), url.scheme != nil {
            return url
        }
        return URL(string: "http://localhost:8080")!
        #else
        guard let str = Bundle.main.object(forInfoDictionaryKey: "API_BASE_URL") as? String,
              let url = URL(string: str.trimmingCharacters(in: .whitespaces)),
              url.scheme != nil else {
            fatalError("API_BASE_URL 이 비어있거나 잘못되었습니다. Config.xcconfig 를 확인하세요.")
        }
        return url
        #endif
    }()

    /// 위젯 확장이 서버에 직접 push 할 수 있도록 베이스 URL을 App Group에 공유한다.
    /// (위젯엔 Info.plist API_BASE_URL이 없으므로 앱이 대신 기록)
    static func publishToWidget() {
        AppGroup.defaults.set(baseURL.absoluteString, forKey: AppGroup.apiBaseURLKey)
    }
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
    let nudgeRemaining: Int?       // 구 서버엔 없을 수 있어 optional
    let nudgeResetAtMs: Int64?     // 0회일 때 다시 가능해지는 시각(epoch ms)
}

struct FriendRequestDTO: Decodable {
    let requestId: Int64
    let fromUserId: Int64
    let fromHandle: String
    let fromNickname: String
    let fromProfileImageUrl: String?
}

struct KakaoFriendCandidateDTO: Decodable, Identifiable {
    let userId: Int64
    let handle: String
    let nickname: String
    let profileImageUrl: String?
    let kakaoNickname: String?    // 카카오톡 표시 이름
    var id: Int64 { userId }
}

struct SentFriendRequestDTO: Decodable {
    let requestId: Int64
    let toUserId: Int64
    let toHandle: String
    let toNickname: String
    let toProfileImageUrl: String?
}

struct RoutineDTO: Decodable {
    let id: UUID
    let name: String
    let type: String
    let target: Int
    let unit: String
    let reminder: String?
    let anytime: Bool
    let repeatMode: String
    let repeatDays: [Int]
    let createdAt: String?
    let endDate: String?     // "yyyy-MM-dd" or nil (서버 미지원 시 nil)
}

struct CompletionDTO: Decodable {
    let routineId: UUID
    let date: String   // "yyyy-MM-dd"
    let count: Int
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

    /// 닉네임(친구에게 보이는 이름) 변경 → 갱신된 내 정보 반환.
    func updateNickname(_ nickname: String) async throws -> UserDTO {
        try await send("PATCH", "/me", body: ["nickname": nickname])
    }

    /// 계정 삭제 예약(3일 유예). 유예 내 재로그인하면 취소됨.
    func deleteAccount() async throws {
        try await sendNoContent("DELETE", "/me")
    }

    /// 피드백/기능 요청 작성.
    func submitFeedback(_ content: String) async throws {
        try await sendNoContent("POST", "/feedback", body: ["content": content])
    }

    // MARK: 친구

    func friends() async throws -> [FriendDTO] {
        try await send("GET", "/me/friends")
    }

    func incomingRequests() async throws -> [FriendRequestDTO] {
        try await send("GET", "/me/friend-requests")
    }

    func outgoingRequests() async throws -> [SentFriendRequestDTO] {
        try await send("GET", "/me/friend-requests/sent")
    }

    /// 카카오 친구 중 앱 사용자 찾기 (+ 내 카카오 연동)
    func kakaoFriends(kakaoAccessToken: String) async throws -> [KakaoFriendCandidateDTO] {
        try await send("POST", "/me/kakao/friends", body: ["kakaoAccessToken": kakaoAccessToken])
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

    /// 친구 자극하기(콕). 입력한 멘트가 상대에게 푸시로 전송된다.
    func nudge(_ userId: Int64, message: String) async throws {
        try await sendNoContent("POST", "/me/friends/\(userId)/nudge", body: ["message": message])
    }

    func uploadSummary(done: [String], remaining: [String], streak: Int) async throws {
        try await sendNoContent("POST", "/me/summary",
                                body: ["done": done, "remaining": remaining, "streak": streak] as [String: Any])
    }

    func registerDeviceToken(_ token: String) async throws {
        try await sendNoContent("POST", "/me/device-token", body: ["token": token])
    }

    // MARK: 루틴 동기화 (계정 귀속)

    func routines() async throws -> [RoutineDTO] {
        try await send("GET", "/me/routines")
    }

    func createRoutine(_ r: Routine) async throws {
        try await sendNoContent("POST", "/me/routines", body: routineBody(r))
    }

    func updateRoutine(_ r: Routine) async throws {
        try await sendNoContent("PUT", "/me/routines/\(r.id.uuidString)", body: routineBody(r))
    }

    func deleteRoutine(id: UUID) async throws {
        try await sendNoContent("DELETE", "/me/routines/\(id.uuidString)")
    }

    func completions() async throws -> [CompletionDTO] {
        try await send("GET", "/me/routines/completions")
    }

    func setCompletion(routineId: UUID, date: String, count: Int) async throws {
        try await sendNoContent("PUT", "/me/routines/\(routineId.uuidString)/completions/\(date)",
                                body: ["count": count])
    }

    private func routineBody(_ r: Routine) -> [String: Any] {
        [
            "id": r.id.uuidString,
            "name": r.name,
            "type": r.type.rawValue,
            "target": r.target,
            "unit": r.unit,
            "reminder": r.reminder ?? NSNull(),
            "anytime": r.anytime,
            "repeatMode": r.repeatMode.rawValue,
            "repeatDays": r.repeatDays,
            // 시작일/종료일도 함께 전송(서버가 지원하면 영속화, 미지원이면 무시됨)
            "createdAt": Self.iso8601.string(from: r.createdAt),
            "endDate": r.endDate.map { $0.dateKey } ?? NSNull()
        ]
    }

    private static let iso8601: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

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
