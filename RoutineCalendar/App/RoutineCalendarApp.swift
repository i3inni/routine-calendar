import SwiftUI
import UIKit
import UserNotifications
import KakaoSDKAuth
import KakaoSDKCommon

@main
struct RoutineCalendarApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate
    @State private var routineStore   = RoutineStore()
    @State private var settingsStore  = SettingsStore()
    @State private var friendsStore   = FriendsStore()
    @State private var session        = SessionStore()
    @State private var showSplash = true

    var body: some Scene {
        WindowGroup {
            ZStack {
                if session.isLoggedIn {
                    MainTabView()
                        .task(id: session.myUserId) { await postLogin() }
                } else if session.isReady {
                    LoginView()
                }

                if showSplash {
                    SplashView()
                        .transition(.opacity)
                        .zIndex(1)
                }
            }
            .environment(routineStore)
            .environment(settingsStore)
            .environment(friendsStore)
            .environment(session)
            .preferredColorScheme(colorSchemeOverride)
            .task {
                await session.bootstrap()   // 저장된 refresh 토큰으로 자동 로그인 시도
            }
            .task {
                // 알림 권한 요청은 시작을 막지 않도록 별도로 진행
                await NotificationManager.shared.requestPermission()
            }
            .task {
                try? await Task.sleep(nanoseconds: 1_300_000_000)  // 1.3초
                withAnimation(.easeOut(duration: 0.4)) {
                    showSplash = false
                }
            }
            .onOpenURL { url in
                // 카카오톡 앱 로그인 후 리다이렉트 처리
                if AuthApi.isKakaoTalkLoginUrl(url) {
                    _ = AuthController.handleOpenUrl(url: url)
                }
            }
        }
    }

    /// 로그인 직후 1회: 요약 업로드 훅 연결 + APNs 토큰 등록 + 친구 로드
    @MainActor
    private func postLogin() async {
        guard session.isLoggedIn else { return }

        let uploadSummary = {
            guard session.isLoggedIn else { return }
            let s = routineStore.todaySummary()
            let streak = routineStore.bestStreak()
            Task {
                try? await APIClient.shared.uploadSummary(
                    done: s.done, remaining: s.remaining, streak: streak)
            }
        }
        routineStore.onDataChanged = uploadSummary
        uploadSummary()

        DeviceTokenCenter.shared.registerIfPossible()
        await friendsStore.setup()
    }

    private var colorSchemeOverride: ColorScheme? {
        switch settingsStore.theme {
        case .light:  return .light
        case .dark:   return .dark
        case .system: return nil
        }
    }
}

// MARK: - APNs 등록 (콕 찌르기 등 원격 푸시 수신용)

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        // 카카오 SDK 초기화 (네이티브 앱 키가 설정된 경우)
        if KakaoConfig.isConfigured {
            KakaoSDK.initSDK(appKey: KakaoConfig.nativeAppKey)
        }
        // 원격 푸시(Spring Boot → APNs)를 받으려면 디바이스 토큰 등록이 필요하다.
        application.registerForRemoteNotifications()
        // 앱이 켜져 있을 때도 알림 배너를 표시
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    // 포그라운드에서도 배너 + 소리 표시 (콕 찌르기·루틴 알림)
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        completionHandler([.banner, .sound, .list])
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        // 디바이스 토큰을 보관 → 로그인 상태면 서버에 등록 (POST /me/device-token)
        let token = deviceToken.map { String(format: "%02x", $0) }.joined()
        DeviceTokenCenter.shared.update(token)
    }

    func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("⚠️ APNs 등록 실패:", error)
    }
}
