import SwiftUI
import AuthenticationServices

struct LoginView: View {
    @Environment(SessionStore.self) private var session
    @Environment(\.colorScheme) private var scheme

    /// 개발용 로그인 버튼 노출 조건. DEBUG 빌드(시뮬레이터)에서만 노출.
    /// (App Store 빌드엔 절대 안 보임 — 로그인은 애플로만)
    private var showDevLogin: Bool {
        #if DEBUG
        return true
        #else
        return false
        #endif
    }

    var body: some View {
        ZStack {
            Color.rcBg(scheme).ignoresSafeArea()

            VStack(spacing: 16) {
                Spacer()

                InterlockingRingsIcon(color: Color.rcText(scheme))
                    .frame(width: 88, height: 60)
                Text("같이해")
                    .font(.custom("Ownglyph_PDH-Rg", size: 34))
                    .foregroundStyle(Color.rcText(scheme))
                Text("친구와 함께 루틴을 만들어요")
                    .font(.system(size: 15))
                    .foregroundStyle(Color.rcText2(scheme))

                Spacer()

                if session.isLoggingIn {
                    ProgressView().padding(.bottom, 8)
                }

                // 애플 로그인 (현재 유일한 로그인 수단. 카카오는 추후 '친구 찾기' 연동용으로만 사용)
                SignInWithAppleButton(.signIn) { request in
                    request.requestedScopes = [.fullName]
                } onCompletion: { result in
                    Task { await session.loginWithApple(result) }
                }
                .signInWithAppleButtonStyle(scheme == .dark ? .white : .black)
                .frame(height: 50)
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .disabled(session.isLoggingIn)

                // 개발용 로그인 (DEBUG 빌드 또는 카카오 키 미설정 시 노출)
                if showDevLogin {
                    Button {
                        Task { await session.devLogin() }
                    } label: {
                        Text("개발용 로그인")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(Color.rcText2(scheme))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(Color.rcSeparator(scheme), lineWidth: 1)
                            )
                    }
                    .disabled(session.isLoggingIn)
                }

                if let error = session.loginError {
                    Text(error)
                        .font(.system(size: 13))
                        .foregroundStyle(Color.rcDestructive)
                        .multilineTextAlignment(.center)
                }
            }
            .padding(.horizontal, 32)
            .padding(.bottom, 40)
        }
    }
}
