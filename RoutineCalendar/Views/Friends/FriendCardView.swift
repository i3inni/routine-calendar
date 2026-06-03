import SwiftUI

struct FriendCardView: View {
    let friend: Friend
    let canPoke: Bool
    let pokeRemainingLabel: String?
    let onPoke: () -> Void

    @Environment(\.colorScheme) private var scheme

    // Poke 버튼 눌림 효과
    @State private var pokeTapScale: CGFloat = 1.0
    @State private var pokeHaloScale: CGFloat = 1.0
    @State private var pokeHaloOpacity: Double = 0

    private var ringFrac: Double {
        friend.totalToday > 0 ? Double(friend.doneToday) / Double(friend.totalToday) : 0
    }

    private var pokeButtonLabel: String {
        if canPoke { return "콕 찌르기" }
        if let label = pokeRemainingLabel { return label }
        return "✓ 찔렀어요"
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

                // 콕 찌르기 버튼 (완료 링 좌측)
                if !friend.isAllDone {
                    Button {
                        handlePokeTap()
                    } label: {
                        Text(pokeButtonLabel)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(canPoke ? Color.rcAccentText(scheme) : Color.rcText2(scheme))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 7)
                            .background(
                                canPoke ? Color.rcAccent(scheme) : Color.rcCard2(scheme),
                                in: Capsule()
                            )
                    }
                    .background {
                        Capsule()
                            .stroke(Color.rcAccent(scheme), lineWidth: 2.5)
                            .scaleEffect(pokeHaloScale)
                            .opacity(pokeHaloOpacity)
                            .allowsHitTesting(false)
                    }
                    .disabled(!canPoke)
                    .scaleEffect(pokeTapScale)
                }

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
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color.rcCard(scheme))
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    // MARK: - Poke 눌림 효과 (가벼운 스케일 + 햅틱 + 링 펄스)

    private func handlePokeTap() {
        guard canPoke else { return }
        onPoke()
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()

        // 톡 눌렸다 살짝 튀어오르는 팝
        withAnimation(.easeOut(duration: 0.08)) {
            pokeTapScale = 0.84
        }
        withAnimation(.spring(response: 0.34, dampingFraction: 0.48).delay(0.08)) {
            pokeTapScale = 1.0
        }

        // 버튼 주위로 링이 톡 번졌다 사라짐
        pokeHaloScale = 1.0
        pokeHaloOpacity = 0.7
        withAnimation(.easeOut(duration: 0.45)) {
            pokeHaloScale = 1.4
            pokeHaloOpacity = 0
        }
    }
}
