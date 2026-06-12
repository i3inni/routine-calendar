import SwiftUI

struct FriendCardView: View {
    let friend: Friend

    @Environment(\.colorScheme) private var scheme
    @Environment(DeepLinkRouter.self) private var deepLink
    @State private var showNudgeSheet = false

    private var ringFrac: Double {
        friend.totalToday > 0 ? Double(friend.doneToday) / Double(friend.totalToday) : 0
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header
            HStack(spacing: 12) {
                // Avatar
                Circle()
                    .fill(Color.rcCard2(scheme))
                    .frame(width: 44, height: 44)
                    .overlay(
                        Text(friend.initial)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(Color.rcText(scheme))
                    )

                VStack(alignment: .leading, spacing: 2) {
                    Text(friend.name)
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Color.rcText(scheme))
                    Text("\(friend.streak)일 연속 · 오늘 \(friend.doneToday)/\(friend.totalToday)")
                        .font(.rcMeta)
                        .foregroundStyle(Color.rcText2(scheme))
                        .monospacedDigit()
                }

                Spacer()

                // Progress ring (40px)
                RingView(size: 40, stroke: 3.5, fraction: ringFrac,
                         color: Color.rcAccent(scheme), trackColor: Color.rcEmptyFill(scheme)) {
                    if friend.isAllDone {
                        Image(systemName: "checkmark")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Color.rcAccent(scheme))
                    }
                }
            }

            // Routine list (잠금화면 목록 스타일 — 미완료 먼저, 완료는 체크 표시)
            if friend.totalToday == 0 {
                Text("오늘 루틴이 없어요")
                    .font(.system(size: 14))
                    .foregroundStyle(Color.rcText2(scheme))
            } else {
                Rectangle()
                    .fill(Color.rcSeparator(scheme))
                    .frame(height: 0.5)

                VStack(alignment: .leading, spacing: 9) {
                    ForEach(Array(friend.todayRoutines.enumerated()), id: \.offset) { _, item in
                        HStack(spacing: 9) {
                            Image(systemName: item.done ? "checkmark.circle.fill" : "circle")
                                .font(.system(size: 16))
                                .foregroundStyle(item.done ? Color.rcAccent(scheme) : Color.rcText3(scheme))
                            Text(item.name)
                                .font(.system(size: 14, weight: .medium))
                                .foregroundStyle(item.done ? Color.rcText3(scheme) : Color.rcText(scheme))
                                .strikethrough(item.done, color: Color.rcText3(scheme))
                                .lineLimit(1)
                            Spacer()
                        }
                    }
                }
            }

            // 자극하기 — 아직 오늘 루틴을 다 못 끝낸 친구에게만
            if !friend.isAllDone {
                if friend.nudgeOnCooldown, let resetAt = friend.nudgeResetAt {
                    // 쿨다운: 30분 카운트다운 (탭 불가)
                    HStack(spacing: 6) {
                        Image(systemName: "clock")
                        Text(timerInterval: Date.now...resetAt, countsDown: true)
                            .monospacedDigit()
                        Text("후 다시 가능")
                    }
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Color.rcText3(scheme))
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 10)
                    .background(Color.rcCard2(scheme),
                                in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                } else {
                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        showNudgeSheet = true
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "hand.point.right.fill")
                            Text("자극하기")
                            Text("\(friend.nudgeRemaining)/2")
                                .monospacedDigit()
                                .opacity(0.65)
                        }
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.rcAccent(scheme))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(Color.rcAccent(scheme).opacity(0.12),
                                    in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color.rcCard(scheme))
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .sheet(isPresented: $showNudgeSheet) {
            NudgeSheetView(friend: friend)
                .presentationDetents([.height(420)])
                .presentationDragIndicator(.visible)
        }
        // 위젯 "자극하기" 딥링크로 이 친구가 지목되면 자극 시트 열기
        .onChange(of: deepLink.pendingNudgeFriendId) { _, id in
            if id == friend.id { showNudgeSheet = true; deepLink.pendingNudgeFriendId = nil }
        }
        .onAppear {
            if deepLink.pendingNudgeFriendId == friend.id { showNudgeSheet = true; deepLink.pendingNudgeFriendId = nil }
        }
    }
}

// MARK: - 자극 멘트 입력 시트

private struct NudgeSheetView: View {
    let friend: Friend

    @Environment(FriendsStore.self) private var friendsStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme

    @State private var text = ""
    @State private var isSending = false
    @State private var errorMessage: String?

    private let presets = ["얼른 루틴 시작해!", "오늘도 화이팅", "딱 하나만 더!", "나 벌써 다 했지롱"]
    private let maxLength = 50

    private var trimmed: String {
        text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    var body: some View {
        ZStack {
            Color.rcBg(scheme).ignoresSafeArea()
            VStack(alignment: .leading, spacing: 16) {
                // 제목
                VStack(alignment: .leading, spacing: 4) {
                    Text("\(friend.name)님 자극하기")
                        .font(.system(size: 20, weight: .bold))
                        .foregroundStyle(Color.rcText(scheme))
                    Text("보낼 멘트가 \(friend.name)님에게 알림으로 전송돼요.")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.rcText2(scheme))
                }
                .padding(.top, 12)

                // 빠른 멘트 칩
                FlowChips(presets: presets, scheme: scheme) { preset in
                    text = preset
                }

                // 입력
                ZStack(alignment: .topLeading) {
                    if trimmed.isEmpty {
                        Text("직접 멘트를 입력해도 좋아요")
                            .font(.system(size: 15))
                            .foregroundStyle(Color.rcText3(scheme))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 12)
                    }
                    TextField("", text: $text, axis: .vertical)
                        .font(.system(size: 15))
                        .foregroundStyle(Color.rcText(scheme))
                        .padding(10)
                        .frame(minHeight: 70, alignment: .topLeading)
                        .onChange(of: text) { _, new in
                            if new.count > maxLength { text = String(new.prefix(maxLength)) }
                        }
                }
                .background(Color.rcCard(scheme), in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                HStack {
                    if let errorMessage {
                        Text(errorMessage)
                            .font(.system(size: 13))
                            .foregroundStyle(Color.rcDestructive)
                    }
                    Spacer()
                    Text("\(trimmed.count)/\(maxLength)")
                        .font(.system(size: 12))
                        .foregroundStyle(Color.rcText3(scheme))
                }

                // 보내기
                Button {
                    Task { await send() }
                } label: {
                    Group {
                        if isSending {
                            ProgressView().tint(Color.rcAccentText(scheme))
                        } else {
                            Text("자극 보내기")
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

                Spacer(minLength: 0)
            }
            .padding(.horizontal, 22)
            .padding(.top, 8)
        }
    }

    private func send() async {
        let message = trimmed
        guard !message.isEmpty, !isSending else { return }
        isSending = true
        errorMessage = nil
        let result = await friendsStore.nudge(friend, message: message)
        isSending = false
        switch result {
        case .sent:
            UINotificationFeedbackGenerator().notificationOccurred(.success)
            dismiss()                      // 즉시 닫기
            Task { await friendsStore.refresh() }   // 남은 횟수는 백그라운드로 갱신
        case .cooldown:
            errorMessage = "잠시 후에 다시 자극할 수 있어요. (한 친구당 30분에 2번까지)"
        case .failed:
            errorMessage = "전송에 실패했어요. 잠시 후 다시 시도해 주세요."
        }
    }
}

// MARK: - 빠른 멘트 칩 (2열 래핑)

private struct FlowChips: View {
    let presets: [String]
    let scheme: ColorScheme
    let onTap: (String) -> Void

    private let columns = [GridItem(.flexible()), GridItem(.flexible())]

    var body: some View {
        LazyVGrid(columns: columns, spacing: 10) {
            ForEach(presets, id: \.self) { preset in
                Button { onTap(preset) } label: {
                    Text(preset)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.rcAccent(scheme))
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 13)
                        .padding(.horizontal, 8)
                        .background(Color.rcAccent(scheme).opacity(0.12),
                                    in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(Color.rcAccent(scheme).opacity(0.25), lineWidth: 1)
                        )
                }
                .buttonStyle(.plain)
            }
        }
    }
}
