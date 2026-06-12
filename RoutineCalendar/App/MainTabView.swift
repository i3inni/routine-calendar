import SwiftUI

struct MainTabView: View {
    @Environment(SettingsStore.self) private var settings
    @Environment(DeepLinkRouter.self) private var deepLink
    @Environment(\.colorScheme) private var scheme
    @State private var selectedTab = 0

    var body: some View {
        TabView(selection: $selectedTab) {
            CalendarView()
                .tabItem {
                    Label("캘린더", systemImage: "calendar")
                }
                .tag(0)

            FriendsView()
                .tabItem {
                    Label("친구", systemImage: "person.2")
                }
                .tag(1)
        }
        .tint(Color.rcAccent(scheme))
        .preferredColorScheme(colorSchemeOverride)
        // 친구추가/자극 딥링크 → 친구 탭으로 전환 (콜드/웜 실행 모두 대응)
        .onChange(of: deepLink.pendingFriendHandle) { _, new in
            if new != nil { selectedTab = 1 }
        }
        .onChange(of: deepLink.pendingNudgeFriendId) { _, new in
            if new != nil { selectedTab = 1 }
        }
        .onAppear {
            if deepLink.pendingFriendHandle != nil || deepLink.pendingNudgeFriendId != nil { selectedTab = 1 }
        }
    }

    private var colorSchemeOverride: ColorScheme? {
        switch settings.theme {
        case .light:  return .light
        case .dark:   return .dark
        case .system: return nil
        }
    }
}
