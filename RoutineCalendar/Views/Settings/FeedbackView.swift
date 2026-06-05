import SwiftUI

/// 기능 제안·피드백 작성 화면. 서버 POST /feedback 로 전송.
struct FeedbackView: View {
    @Environment(\.colorScheme) private var scheme
    @Environment(\.dismiss) private var dismiss

    @State private var text = ""
    @State private var isSending = false
    @State private var sent = false
    @State private var errorMessage: String?

    private var trimmed: String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        ZStack {
            Color.rcBg(scheme).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("이런 기능이 있으면 좋겠어요, 불편한 점, 버그 등\n무엇이든 자유롭게 남겨주세요. 잘 읽고 반영할게요 🙌")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.rcText2(scheme))
                        .padding(.top, 8)

                    // 입력 카드 (TextEditor + placeholder)
                    ZStack(alignment: .topLeading) {
                        if trimmed.isEmpty {
                            Text("예) 루틴별 색상을 고르고 싶어요 / 주간 통계가 있으면 좋겠어요")
                                .font(.system(size: 15))
                                .foregroundStyle(Color.rcText3(scheme))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 16)
                        }
                        TextEditor(text: $text)
                            .font(.system(size: 15))
                            .foregroundStyle(Color.rcText(scheme))
                            .scrollContentBackground(.hidden)
                            .padding(8)
                            .frame(minHeight: 180)
                    }
                    .background(Color.rcCard(scheme), in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                    HStack {
                        Spacer()
                        Text("\(trimmed.count)/2000")
                            .font(.system(size: 12))
                            .foregroundStyle(Color.rcText3(scheme))
                    }

                    // 전송 버튼
                    Button {
                        Task { await submit() }
                    } label: {
                        Group {
                            if isSending {
                                ProgressView().tint(Color.rcAccentText(scheme))
                            } else {
                                Text("보내기")
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(Color.rcAccentText(scheme))
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 15)
                        .background(
                            (trimmed.isEmpty ? Color.rcText3(scheme) : Color.rcAccent(scheme)),
                            in: RoundedRectangle(cornerRadius: 14, style: .continuous)
                        )
                    }
                    .disabled(trimmed.isEmpty || isSending)

                    if let errorMessage {
                        Text(errorMessage)
                            .font(.system(size: 13))
                            .foregroundStyle(Color.rcDestructive)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 40)
            }
        }
        .navigationTitle("피드백")
        .navigationBarTitleDisplayMode(.inline)
        .alert("보내주셔서 감사해요!", isPresented: $sent) {
            Button("확인") { dismiss() }
        } message: {
            Text("소중한 의견 잘 읽고 반영할게요.")
        }
    }

    private func submit() async {
        let content = trimmed
        guard !content.isEmpty, !isSending else { return }
        isSending = true
        errorMessage = nil
        defer { isSending = false }
        do {
            try await APIClient.shared.submitFeedback(content)
            sent = true
        } catch {
            errorMessage = "전송에 실패했어요. 잠시 후 다시 시도해 주세요."
        }
    }
}
