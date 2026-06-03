import Foundation
import Security

/// 작은 Keychain 래퍼 (generic password). 토큰 등 민감 값 저장용.
enum Keychain {
    @discardableResult
    static func set(_ value: String, for key: String) -> Bool {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
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
        ]
        SecItemDelete(query as CFDictionary)
    }
}

/// access/refresh 토큰 보관소. access는 빠른 접근을 위해 메모리 캐시 + Keychain 영속.
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
