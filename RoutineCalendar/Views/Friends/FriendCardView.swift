import SwiftUI

struct FriendCardView: View {
    let friend: Friend

    @Environment(\.colorScheme) private var scheme

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
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color.rcCard(scheme))
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}
