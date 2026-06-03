import SwiftUI

struct LoginView: View {
    @Environment(SessionStore.self) private var session
    @Environment(\.colorScheme) private var scheme

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

                // 카카오 로그인
                Button {
                    Task { await session.loginWithKakao() }
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "message.fill")
                        Text("카카오로 시작하기")
                            .font(.system(size: 16, weight: .semibold))
                    }
                    .foregroundStyle(Color(hex: "191600"))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 15)
                    .background(Color(hex: "FEE500"), in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                .disabled(session.isLoggingIn)

                // 개발용 로그인 (카카오 키 미설정 시 노출)
                if !KakaoConfig.isConfigured {
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
