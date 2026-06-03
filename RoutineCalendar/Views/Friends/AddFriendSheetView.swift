import SwiftUI
import UIKit

struct AddFriendSheetView: View {
    @Environment(FriendsStore.self) private var friendsStore
    @Environment(SessionStore.self) private var session
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme

    @State private var inputId = ""
    @State private var isSending = false
    @State private var didSend = false
    @State private var errorMessage: String?
    @State private var showCopiedToast = false

    private var myHandle: String { session.currentUser?.handle ?? "—" }
    private var shareText: String {
        "같이해에서 함께 루틴 해요! 내 ID: \(myHandle)"
    }

    var body: some View {
        NavigationStack {
            ZStack {
                Color.rcBg(scheme).ignoresSafeArea()
                ScrollView {
                    VStack(spacing: 0) {
                        // 내 ID (친구에게 알려주는 코드)
                        SectionLabel("내 ID")
                        VStack(spacing: 16) {
                            Text(myHandle)
                                .font(.system(size: 32, weight: .bold, design: .monospaced))
                                .tracking(3)
                                .foregroundStyle(Color.rcText(scheme))
                                .padding(.top, 8)

                            HStack(spacing: 12) {
                                Button {
                                    UIPasteboard.general.string = myHandle
                                    showCopiedToast = true
                                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                                        showCopiedToast = false
                                    }
                                } label: {
                                    Text(showCopiedToast ? "복사됨 ✓" : "ID 복사")
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundStyle(Color.rcAccent(scheme))
                                        .padding(.horizontal, 20)
                                        .padding(.vertical, 10)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                                .stroke(Color.rcAccent(scheme), lineWidth: 1.5)
                                        )
                                }
                                .animation(.easeInOut(duration: 0.15), value: showCopiedToast)

                                ShareLink(item: shareText) {
                                    Text("공유하기")
                                        .font(.system(size: 15, weight: .semibold))
                                        .foregroundStyle(Color.rcAccentText(scheme))
                                        .padding(.horizontal, 20)
                                        .padding(.vertical, 10)
                                        .background(
                                            Color.rcAccent(scheme),
                                            in: RoundedRectangle(cornerRadius: 12, style: .continuous)
                                        )
                                }
                            }

                            Text("이 ID를 친구에게 알려주면 친구가 나를 추가할 수 있어요")
                                .font(.system(size: 13))
                                .foregroundStyle(Color.rcText2(scheme))
                                .multilineTextAlignment(.center)
                                .padding(.bottom, 8)
                        }
                        .frame(maxWidth: .infinity)
                        .padding()
                        .rcCard(scheme, radius: 16)
                        .padding(.horizontal, 16)

                        // 친구 ID 입력
                        SectionLabel("친구 ID 입력")
                        VStack(spacing: 12) {
                            HStack(spacing: 8) {
                                TextField("친구의 ID", text: $inputId)
                                    .font(.system(size: 16, weight: .medium))
                                    .autocorrectionDisabled()
                                    .onChange(of: inputId) { _, _ in errorMessage = nil }
                                    .submitLabel(.done)
                                    .onSubmit { Task { await sendRequest() } }

                                Button {
                                    Task { await sendRequest() }
                                } label: {
                                    Group {
                                        if isSending {
                                            ProgressView()
                                                .tint(Color.rcAccentText(scheme))
                                        } else {
                                            Text(didSend ? "보냄 ✓" : "요청")
                                                .font(.system(size: 15, weight: .semibold))
                                                .foregroundStyle(Color.rcAccentText(scheme))
                                        }
                                    }
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 8)
                                    .background(
                                        (inputId.isEmpty || didSend)
                                            ? Color.rcText3(scheme)
                                            : Color.rcAccent(scheme),
                                        in: RoundedRectangle(cornerRadius: 10, style: .continuous)
                                    )
                                }
                                .disabled(inputId.isEmpty || didSend || isSending)
                            }
                            .padding()
                            .rcCard(scheme, radius: 16)

                            if let errorMessage {
                                Text(errorMessage)
                                    .font(.system(size: 13))
                                    .foregroundStyle(Color.rcDestructive)
                            } else if didSend {
                                Text("친구 요청을 보냈어요! 상대가 수락하면 친구가 됩니다.")
                                    .font(.system(size: 13))
                                    .foregroundStyle(Color.rcText2(scheme))
                            }
                        }
                        .padding(.horizontal, 16)

                        // Footer
                        Text("카카오 로그인으로 친구를 연결합니다.")
                            .font(.system(size: 12.5))
                            .foregroundStyle(Color.rcText2(scheme))
                            .multilineTextAlignment(.center)
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 16)
                            .padding(.top, 24)
                            .padding(.bottom, 40)
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { dismiss() }
                        .foregroundStyle(Color.rcText2(scheme))
                }
                ToolbarItem(placement: .principal) {
                    Text("친구 추가")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(Color.rcText(scheme))
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }

    private func sendRequest() async {
        let id = inputId.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !id.isEmpty, !isSending, !didSend else { return }

        isSending = true
        errorMessage = nil
        let result = await friendsStore.sendRequest(id: id)
        isSending = false

        switch result {
        case .requestSent:
            didSend = true
            try? await Task.sleep(nanoseconds: 1_200_000_000)
            dismiss()
        default:
            errorMessage = result.message
        }
    }
}

private struct SectionLabel: View {
    let text: String
    @Environment(\.colorScheme) private var scheme
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(Color.rcText2(scheme))
            .tracking(0.3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 7)
    }
}
