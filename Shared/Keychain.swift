import Foundation
import Security

/// 작은 Keychain 래퍼 (generic password). 토큰 등 민감 값 저장용.
/// 앱·위젯 확장이 **공유 access group**으로 같은 항목을 읽고 쓴다(위젯의 서버 push용).
enum Keychain {
    /// 앱·위젯이 공유하는 Keychain access group.
    /// = `<AppIdentifierPrefix>` + 앱 번들ID. AppIdentifierPrefix는 보통 TeamID와 같다.
    /// (entitlements 의 `$(AppIdentifierPrefix)com.i3inni.routinecalendar` 와 일치해야 함)
    /// ⚠️ 위젯이 토큰을 못 읽으면 App ID Prefix가 TeamID와 다른 경우 — 이 값을 점검.
    static let accessGroup = "DBDJ2HDBU2.com.i3inni.routinecalendar"

    @discardableResult
    static func set(_ value: String, for key: String) -> Bool {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecAttrAccessGroup as String: accessGroup,
        ]
        SecItemDelete(query as CFDictionary)
        var attrs = query
        attrs[kSecValueData as String] = data
        attrs[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlock
        return SecItemAdd(attrs as CFDictionary, nil) == errSecSuccess
    }

    static func get(_ key: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecAttrAccessGroup as String: accessGroup,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    static func delete(_ key: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecAttrAccessGroup as String: accessGroup,
        ]
        SecItemDelete(query as CFDictionary)
    }
}

/// access/refresh 토큰 보관소. access는 빠른 접근을 위해 메모리 캐시 + Keychain 영속.
/// 앱과 위젯이 동일한 Keychain(공유그룹)을 쓰므로 토큰을 공유한다.
final class TokenStore: @unchecked Sendable {
    static let shared = TokenStore()

    private let accessKey = "rc.accessToken"
    private let refreshKey = "rc.refreshToken"
    private let lock = NSLock()
    private var cachedAccess: String?

    private init() { cachedAccess = Keychain.get(accessKey) }

    var accessToken: String? {
        lock.lock(); defer { lock.unlock() }
        return cachedAccess
    }

    var refreshToken: String? { Keychain.get(refreshKey) }

    var hasRefreshToken: Bool { refreshToken != nil }

    func save(access: String, refresh: String) {
        lock.lock(); cachedAccess = access; lock.unlock()
        Keychain.set(access, for: accessKey)
        Keychain.set(refresh, for: refreshKey)
    }

    func clear() {
        lock.lock(); cachedAccess = nil; lock.unlock()
        Keychain.delete(accessKey)
        Keychain.delete(refreshKey)
    }
}
