import SwiftUI

/// 7-cell horizontal strip. `days[0]` = oldest, `days[6]` = today.
struct MiniWeekView: View {
    var days: [Bool]
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<7, id: \.self) { i in
                RoundedRectangle(cornerRadius: 2.5)
                    .fill(days[i] ? Color.rcAccent(scheme) : Color.rcEmptyFill(scheme))
                    .frame(width: 7, height: 7)
                    .overlay {
                        if i == 6 {
                            RoundedRectangle(cornerRadius: 2.5)
                                .stroke(Color.rcAccent(scheme), lineWidth: 1.5)
                                .padding(-1.5)
                        }
                    }
            }
        }
    }
}
