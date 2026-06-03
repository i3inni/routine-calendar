import Foundation

/// 카카오 네이티브 앱 키. Info.plist의 KAKAO_NATIVE_APP_KEY( = project.yml의 빌드 설정)에서 읽는다.
/// 키는 카카오 개발자 콘솔 > 내 애플리케이션 > 앱 키 > '네이티브 앱 키'.
enum KakaoConfig {
    static var nativeAppKey: String {
        (Bundle.main.object(forInfoDictionaryKey: "KAKAO_NATIVE_APP_KEY") as? String) ?? ""
    }

    /// 실제 키가 채워졌는지 (플레이스홀더면 false → 개발용 로그인만 노출)
    static var isConfigured: Bool {
        let key = nativeAppKey
        return !key.isEmpty && !key.hasPrefix("YOUR_")
    }
}
