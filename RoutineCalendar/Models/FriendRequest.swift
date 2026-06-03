import Foundation

/// 친구 요청 (A → B). B가 수락하면 둘 다 친구가 된다.
struct FriendRequest: Identifiable {
    var id: String          // 서버 측 요청 식별자
    var fromCode: String    // 요청 보낸 사람 ID
    var fromName: String    // 요청 보낸 사람 이름
    var toCode: String      // 받는 사람 ID
    var createdAt: Date

    var initial: String { String(fromName.prefix(1)) }
}
