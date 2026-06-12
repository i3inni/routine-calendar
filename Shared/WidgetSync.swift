import Foundation

/// 위젯(AppIntent)에서 루틴 완료를 처리하는 공유 로직.
/// ① App Group 완료 데이터를 토글(앱·위젯 즉시 반영) ② 서버에 직접 push(401이면 refresh 후 재시도).
/// 순수 Foundation + 공유 TokenStore/AppGroup만 사용 → 위젯 확장에서도 동작.
enum WidgetSync {

    // MARK: - App Group 완료 토글

    /// 오늘 날짜 키.
    static var todayKey: String { Date().dateKey }

    /// App Group의 완료 데이터를 토글하고 새 카운트를 반환한다.
    /// (앱의 RoutineStore.toggle 과 동일한 규칙)
    @discardableResult
    static func toggleInAppGroup(routineId: UUID, target: Int, isCount: Bool, dateKey: String) -> Int {
        var all = loadCompletion()
        let key = routineId.uuidString
        let current = all[key]?[dateKey] ?? 0
        let next: Int
        if isCount {
            next = current >= target ? 0 : current + 1
        } else {
            next = current > 0 ? 0 : 1
        }
        all[key, default: [:]][dateKey] = next
        saveCompletion(all)
        return next
    }

    private static func loadCompletion() -> [String: [String: Int]] {
        guard let data = AppGroup.defaults.data(forKey: AppGroup.completionKey),
              let dict = try? JSONDecoder().decode([String: [String: Int]].self, from: data)
        else { return [:] }
        return dict
    }

    private static func saveCompletion(_ dict: [String: [String: Int]]) {
        if let data = try? JSONEncoder().encode(dict) {
            AppGroup.defaults.set(data, forKey: AppGroup.completionKey)
            AppGroup.defaults.synchronize()
        }
    }

    // MARK: - 서버 push (PUT /me/routines/{id}/completions/{date})

    /// 완료 카운트를 서버에 반영. access 만료(401)면 refresh 후 1회 재시도.
    static func setCompletionOnServer(routineId: UUID, dateKey: String, count: Int) async {
        guard let base = AppGroup.defaults.string(forKey: AppGroup.apiBaseURLKey) else { return }
        let path = "/me/routines/\(routineId.uuidString)/completions/\(dateKey)"
        let body = try? JSONSerialization.data(withJSONObject: ["count": count])
        _ = await request(method: "PUT", base: base, path: path, body: body, allowRefresh: true)
    }

    private static func request(method: String, base: String, path: String, body: Data?, allowRefresh: Bool) async -> Bool {
        guard let url = URL(string: base + path) else { return false }
        guard let token = TokenStore.shared.accessToken else {
            if allowRefresh, await refresh(base: base) {
                return await request(method: method, base: base, path: path, body: body, allowRefresh: false)
            }
            return false
        }
        var req = URLRequest(url: url)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.httpBody = body
        guard let (_, resp) = try? await URLSession.shared.data(for: req),
              let http = resp as? HTTPURLResponse else { return false }
        if http.statusCode == 401, allowRefresh, await refresh(base: base) {
            return await request(method: method, base: base, path: path, body: body, allowRefresh: false)
        }
        return (200..<300).contains(http.statusCode)
    }

    // MARK: - 친구 위젯 (현황 스냅샷)

    /// 친구 현황을 App Group에 저장(위젯이 읽어 표시). 자극은 앱 시트에서 처리.
    static func saveFriends(_ friends: [Friend]) {
        if let data = try? JSONEncoder().encode(friends) {
            AppGroup.defaults.set(data, forKey: AppGroup.friendsKey)
            AppGroup.defaults.synchronize()
        }
    }

    /// refresh 토큰으로 새 access/refresh 발급 → 공유 Keychain에 저장.
    private static func refresh(base: String) async -> Bool {
        guard let refreshToken = TokenStore.shared.refreshToken,
              let url = URL(string: base + "/auth/refresh") else { return false }
        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.httpBody = try? JSONSerialization.data(withJSONObject: ["refreshToken": refreshToken])
        guard let (data, resp) = try? await URLSession.shared.data(for: req),
              let http = resp as? HTTPURLResponse, (200..<300).contains(http.statusCode),
              let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let access = json["accessToken"] as? String,
              let newRefresh = json["refreshToken"] as? String else { return false }
        TokenStore.shared.save(access: access, refresh: newRefresh)
        return true
    }
}
