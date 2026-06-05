import Foundation
import Observation

enum AddFriendResult {
    case requestSent   // 요청 전송됨
    case isSelf        // 내 ID를 입력함
    case alreadyFriend // 이미 친구
    case notFound      // 해당 사용자를 찾을 수 없음
    case error(String) // 서버 등 실제 오류

    var message: String? {
        switch self {
        case .requestSent:   return nil
        case .isSelf:        return "내 ID는 추가할 수 없어요."
        case .alreadyFriend: return "이미 친구예요."
        case .notFound:      return "해당 ID의 사용자를 찾을 수 없어요. 다시 확인해 주세요."
        case .error(let m):  return "오류: \(m)"
        }
    }
}

/// 친구 기능 데이터 계층. Spring Boot 서버(REST)와 통신한다.
@Observable
@MainActor
final class FriendsStore {
    var friends: [Friend] = []
    var incomingRequests: [FriendRequest] = []
    var isLoading = false

    private let api = APIClient.shared

    // MARK: - 초기화 / 갱신

    func setup() async {
        // 서버가 정한 콕 쿨다운(환경변수)을 받아와 표시에 사용
        if let cfg = try? await api.config() {
            pokeCooldown = TimeInterval(cfg.pokeCooldownSeconds)
        }
        await refresh()
    }

    func refresh() async {
        isLoading = true
        defer { isLoading = false }
        do {
            async let friendsDTO = api.friends()
            async let requestsDTO = api.incomingRequests()
            friends = try await friendsDTO.map(Friend.init(dto:))
            incomingRequests = try await requestsDTO.map(FriendRequest.init(dto:))
        } catch {
            // 네트워크/인증 실패 시 기존 목록 유지
        }
    }

    // MARK: - 친구 요청 보내기 (handle 검색)

    func sendRequest(id rawId: String) async -> AddFriendResult {
        let handle = rawId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !handle.isEmpty else { return .notFound }
        do {
            try await api.sendFriendRequest(handle: handle)
            return .requestSent
        } catch let APIError.server(_, code, message) {
            switch code {
            case "USER_404":      return .notFound
            case "FRIEND_400_1":  return .isSelf
            case "FRIEND_409_1":  return .alreadyFriend
            case "FRIEND_409_2":  return .requestSent   // 이미 보낸 요청
            default:              return .error(message ?? code ?? "알 수 없는 오류")
            }
        } catch {
            return .error(error.localizedDescription)
        }
    }

    // MARK: - 받은 요청 수락 / 거절

    func acceptRequest(_ request: FriendRequest) async {
        guard let id = Int64(request.id) else { return }
        try? await api.acceptRequest(id)
        incomingRequests.removeAll { $0.id == request.id }
        await refresh()   // 새 친구 반영
    }

    func declineRequest(_ request: FriendRequest) async {
        incomingRequests.removeAll { $0.id == request.id }
        guard let id = Int64(request.id) else { return }
        try? await api.declineRequest(id)
    }

    // MARK: - 친구 끊기

    func removeFriend(_ friend: Friend) {
        friends.removeAll { $0.id == friend.id }
        pokeTimes.removeValue(forKey: friend.id)
        guard let id = Int64(friend.id) else { return }
        Task { try? await api.removeFriend(id) }
    }

    // MARK: - 콕 찌르기 (친구별 1시간 쿨다운; 서버도 검증)

    // 서버 /config 값으로 갱신됨(기본 3600초)
    private var pokeCooldown: TimeInterval = 3600
    private(set) var pokeTimes: [String: Date] = [:]

    /// 서버 기준 마지막 콕(lastPokedAt)과 로컬 낙관적 기록 중 더 최근 값.
    /// → 로그아웃/앱 재시작에도 서버 값이 남아 쿨다운이 유지된다.
    private func lastPokeTime(_ friend: Friend) -> Date? {
        [friend.lastPokedAt, pokeTimes[friend.id]].compactMap { $0 }.max()
    }

    func canPoke(_ friend: Friend) -> Bool {
        guard let last = lastPokeTime(friend) else { return true }
        return Date().timeIntervalSince(last) >= pokeCooldown
    }

    func pokeRemainingLabel(_ friend: Friend) -> String? {
        guard let last = lastPokeTime(friend) else { return nil }
        let remaining = pokeCooldown - Date().timeIntervalSince(last)
        guard remaining > 0 else { return nil }
        // 1분 미만이면 초 단위로, 그 이상은 분 단위로 표시
        if remaining < 60 {
            return "\(Int(ceil(remaining)))초 후"
        }
        return "\(Int(ceil(remaining / 60)))분 후"
    }

    func poke(_ friend: Friend) {
        guard canPoke(friend), let id = Int64(friend.id) else { return }
        pokeTimes[friend.id] = Date()
        Task { try? await api.poke(toUserId: id) }
    }
}

// MARK: - 서버 DTO → 화면 모델 매핑

private extension Friend {
    init(dto: FriendDTO) {
        self.init(
            id: String(dto.userId),
            name: dto.nickname,
            initial: String(dto.nickname.prefix(1)),
            doneToday: dto.doneToday,
            totalToday: dto.totalToday,
            remaining: dto.remaining,
            done: dto.done,
            streak: dto.streak,
            lastPokedAt: dto.lastPokedAtMillis.map { Date(timeIntervalSince1970: Double($0) / 1000) }
        )
    }
}

private extension FriendRequest {
    init(dto: FriendRequestDTO) {
        self.init(
            id: String(dto.requestId),
            fromCode: dto.fromHandle,
            fromName: dto.fromNickname,
            toCode: "",
            createdAt: Date()
        )
    }
}
