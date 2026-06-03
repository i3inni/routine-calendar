import SwiftUI

struct MonthHeaderView: View {
    let year: Int
    let month: Int
    let onPrev: () -> Void
    let onNext: () -> Void
    let onToday: () -> Void
    let onSettings: () -> Void

    @Environment(\.colorScheme) private var scheme

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 0) {
            // Month title + year
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text("\(month)월")
                    .font(.rcMonthTitle)
                    .foregroundStyle(Color.rcText(scheme))
                    .monospacedDigit()
                Text(String(year))
                    .font(.rcYearLabel)
                    .foregroundStyle(Color.rcText2(scheme))
                    .monospacedDigit()
            }

            Spacer()

            // Right cluster
            HStack(spacing: 2) {
                // 오늘 pill
                Button(action: onToday) {
                    Text("오늘")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.rcAccent(scheme))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(
                            scheme == .dark
                                ? Color.white.opacity(0.10)
                                : Color.black.opacity(0.05),
                            in: Capsule()
                        )
                }

                // ‹ prev
                Button(action: onPrev) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.rcAccent(scheme))
                        .frame(width: 34, height: 34)
                }

                // › next
                Button(action: onNext) {
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Color.rcAccent(scheme))
                        .frame(width: 34, height: 34)
                }

                // ⚙ settings
                Button(action: onSettings) {
                    Image(systemName: "gearshape")
                        .font(.system(size: 17, weight: .regular))
                        .foregroundStyle(Color.rcText2(scheme))
                        .frame(width: 34, height: 34)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.bottom, 6)
    }
}
