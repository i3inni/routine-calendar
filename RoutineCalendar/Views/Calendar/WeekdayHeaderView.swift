import SwiftUI

struct WeekdayHeaderView: View {
    @Environment(\.colorScheme) private var scheme
    private let labels = ["S", "M", "T", "W", "T", "F", "S"]

    var body: some View {
        HStack(spacing: 0) {
            ForEach(0..<7, id: \.self) { i in
                Text(labels[i])
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(i == 0 ? Color.rcText2(scheme) : Color.rcText3(scheme))
                    .frame(maxWidth: .infinity)
            }
        }
        .padding(.vertical, 4)
    }
}
