import WidgetKit
import SwiftUI
import AppIntents

// MARK: - 친구 위젯 (내 현황 + 친구 루틴 + 자극하기)

struct FriendsWidget: Widget {
    let kind = "FriendsWidget"
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: LockScreenProvider()) { entry in
            FriendsWidgetView(entry: entry)
                .widgetURL(URL(string: "routinecalendar://friends"))
        }
        .configurationDisplayName("친구 루틴")
        .description("친구들의 오늘 루틴을 보고 자극을 보내세요.")
        .supportedFamilies([.systemMedium])
    }
}

struct FriendsWidgetView: View {
    let entry: LockScreenEntry
    @Environment(\.colorScheme) private var scheme
    @Environment(\.widgetRenderingMode) private var renderMode

    private var myProgress: (done: Int, total: Int, frac: Double) {
        WidgetDataReader.dayProgress(entry: entry, dateKey: entry.date.dateKey)
    }
    private let maxRows = 2   // 미디엄: 제목 + 친구 2명 (넘치면 "+N명 더")
    private var sortedFriends: [Friend] {
        entry.friends.sorted { !$0.isAllDone && $1.isAllDone }   // 미완료 먼저
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            header
            Rectangle().fill(Color.rcSeparator(scheme)).frame(height: 1)
            friendList
        }
        .padding(.horizontal, 6)
        .padding(.top, 12)
        .padding(.bottom, 5)
        .containerBackground(for: .widget) { Color.rcBg(scheme) }
    }

    private var header: some View {
        HStack(spacing: 6) {
            Image(systemName: "person.2.fill")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.rcAccent(scheme))
            Text("친구 루틴")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(Color.rcText(scheme))
            Spacer()
            HStack(spacing: 4) {
                Text("나").font(.system(size: 12)).foregroundStyle(Color.rcText2(scheme))
                Text("\(myProgress.done)/\(myProgress.total)")
                    .font(.system(size: 13, weight: .semibold)).monospacedDigit()
                    .foregroundStyle(myProgress.total > 0 && myProgress.done >= myProgress.total
                                     ? Color.rcAccent(scheme) : Color.rcText(scheme))
            }
            .padding(.horizontal, 9).padding(.vertical, 4)
            .widgetCapsule(Color.rcCard(scheme), renderMode)
        }
    }

    private var friendList: some View {
        VStack(spacing: 6) {
            if entry.friends.isEmpty {
                Text("친구를 추가해보세요")
                    .font(.system(size: 13))
                    .foregroundStyle(Color.rcText3(scheme))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ForEach(sortedFriends.prefix(maxRows)) { friend in
                    row(friend)
                }
                if sortedFriends.count > maxRows {
                    Text("+\(sortedFriends.count - maxRows)명 더")
                        .font(.system(size: 11))
                        .foregroundStyle(Color.rcText3(scheme))
                        .frame(maxWidth: .infinity, alignment: .leading)
                }
                Spacer(minLength: 0)
            }
        }
    }

    private func row(_ friend: Friend) -> some View {
        HStack(spacing: 9) {
            Text(friend.initial)
                .font(.system(size: 12.5, weight: .semibold))
                .foregroundStyle(Color.rcText(scheme))
                .frame(width: 28, height: 28)
                .background {
                    if renderMode == .fullColor {
                        Circle().fill(Color.rcCard2(scheme))
                    } else {
                        Circle().stroke(Color.rcText3(scheme), lineWidth: 1)   // 틴트: 테두리만
                    }
                }
            VStack(alignment: .leading, spacing: 1) {
                Text(friend.name)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Color.rcText(scheme))
                    .lineLimit(1)
                Text("\(friend.streak)일 연속 · 오늘 \(friend.doneToday)/\(friend.totalToday)")
                    .font(.system(size: 11))
                    .foregroundStyle(Color.rcText2(scheme))
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
            trailing(friend)
        }
        .padding(.vertical, 5).padding(.horizontal, 11)
        .widgetCard(scheme, renderMode, radius: 13)
    }

    @ViewBuilder
    private func trailing(_ friend: Friend) -> some View {
        if friend.isAllDone {
            badge("완료", color: Color.rcAccent(scheme))
        } else if friend.nudgeOnCooldown {
            badge("대기", color: Color.rcText3(scheme))
        } else {
            // 위젯은 멘트 입력 불가 → 앱의 자극 시트로 이동
            Link(destination: URL(string: "routinecalendar://nudge/\(friend.id)")!) {
                Text("자극하기")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(renderMode == .fullColor ? Color.rcAccentText(scheme) : Color.rcText(scheme))
                    .padding(.horizontal, 12).padding(.vertical, 5)
                    .background {
                        if renderMode == .fullColor {
                            Capsule().fill(Color.rcAccent(scheme))
                        } else {
                            Capsule().stroke(Color.rcText3(scheme), lineWidth: 1)
                        }
                    }
            }
        }
    }

    private func badge(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(color)
            .padding(.horizontal, 10).padding(.vertical, 5)
            .widgetCapsule(Color.rcCard2(scheme), renderMode)
    }
}

#Preview("친구 루틴", as: .systemMedium) {
    FriendsWidget()
} timeline: {
    LockScreenEntry.sample
}
