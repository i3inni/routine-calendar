import SwiftUI

struct MainTabView: View {
    @Environment(SettingsStore.self) private var settings
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
    }

    private var colorSchemeOverride: ColorScheme? {
        switch settings.theme {
        case .light:  return .light
        case .dark:   return .dark
        case .system: return nil
        }
    }
}
