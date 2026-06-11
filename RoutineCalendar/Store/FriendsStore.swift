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
    var outgoingRequests: [FriendRequest] = []   // 내가 보낸 요청 (수락 대기)
    var isLoading = false

    var kakaoCandidates: [KakaoFriendCandidateDTO] = []   // 카카오 친구 찾기 결과
    var requestedHandles: Set<String> = []               // 후보 중 요청 보낸 handle

    private let api = APIClient.shared

    // MARK: - 초기화 / 갱신

    func setup() async {
        await refresh()
    }

    func refresh() async {
        isLoading = true
        defer { isLoading = false }
        do {
            async let friendsDTO = api.friends()
            async let requestsDTO = api.incomingRequests()
            async let sentDTO = api.outgoingRequests()
            friends = try await friendsDTO.map(Friend.init(dto:))
            incomingRequests = try await requestsDTO.map(FriendRequest.init(dto:))
            outgoingRequests = try await sentDTO.map(FriendRequest.init(sentDto:))
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
            await refresh()   // 보낸 요청/친구 변화 즉시 반영 (수동 새로고침 불필요)
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

    // MARK: - 자극하기 (콕)

    enum NudgeResult { case sent, cooldown, failed }

    /// 친구에게 자극 멘트를 푸시로 보낸다. (한 친구당 30분에 2번 제한)
    func nudge(_ friend: Friend, message: String) async -> NudgeResult {
        let text = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, let id = Int64(friend.id) else { return .failed }
        do {
            try await api.nudge(id, message: text)
            return .sent
        } catch let APIError.server(_, code, _) where code == "FRIEND_429" {
            return .cooldown
        } catch {
            return .failed
        }
    }

    // MARK: - 카카오 친구 찾기

    enum KakaoFindResult { case ok, alreadyLinked, consentRequired, notConfigured, failed(String) }

    /// 카카오 로그인(친구목록 권한) → 내 카톡 친구 중 앱 사용자 후보를 불러온다.
    func findKakaoFriends() async -> KakaoFindResult {
        guard KakaoConfig.isConfigured else { return .notConfigured }

        // 1단계: 카카오 로그인(friends 권한)
        let token: String
        do {
            token = try await KakaoLoginService.loginForFriends()
        } catch {
            return .failed("① 카카오 로그인 실패: \(error.localizedDescription)")
        }

        // 2단계: 서버에 친구 매칭 요청
        do {
            kakaoCandidates = try await api.kakaoFriends(kakaoAccessToken: token)
            requestedHandles = []
            return .ok
        } catch let APIError.server(_, code, _) where code == "KAKAO_409" {
            return .alreadyLinked
        } catch let APIError.server(_, code, message) where code == "KAKAO_409_2" {
            return .failed(message ?? "이 계정엔 이미 다른 카카오가 연동돼 있어요.")
        } catch let APIError.server(_, code, _) where code == "KAKAO_403_3" {
            return .consentRequired
        } catch let APIError.server(status, code, message) {
            return .failed("② 서버 \(status) [\(code ?? "-")] \(message ?? "")")
        } catch let APIError.transport(e) {
            return .failed("② 네트워크 오류: \(e.localizedDescription)")
        } catch let APIError.decoding(e) {
            return .failed("② 응답 해석 실패: \(e.localizedDescription)")
        } catch APIError.unauthorized {
            return .failed("② 인증 만료(앱 토큰 401)")
        } catch {
            return .failed("② \(error.localizedDescription)")
        }
    }

    /// 후보에게 친구 요청 (기존 handle 기반 요청 재사용). 결과를 반환해 호출부가 피드백.
    @discardableResult
    func requestKakaoFriend(_ candidate: KakaoFriendCandidateDTO) async -> AddFriendResult {
        let result = await sendRequest(id: candidate.handle)   // 내부에서 refresh
        if case .requestSent = result {
            requestedHandles.insert(candidate.handle)
        }
        return result
    }

    // MARK: - 친구 끊기

    func removeFriend(_ friend: Friend) {
        friends.removeAll { $0.id == friend.id }
        guard let id = Int64(friend.id) else { return }
        Task { try? await api.removeFriend(id) }
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
            nudgeRemaining: dto.nudgeRemaining ?? 2,
            nudgeResetAt: dto.nudgeResetAtMs.map { Date(timeIntervalSince1970: Double($0) / 1000) }
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

    /// 보낸 요청: 표시 대상은 받는 사람(addressee). from* 필드에 상대 정보를 담는다.
    init(sentDto: SentFriendRequestDTO) {
        self.init(
            id: String(sentDto.requestId),
            fromCode: sentDto.toHandle,
            fromName: sentDto.toNickname,
            toCode: "",
            createdAt: Date()
        )
    }
}
