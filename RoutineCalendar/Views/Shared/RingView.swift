import SwiftUI

struct RingView<Center: View>: View {
    var size: CGFloat
    var stroke: CGFloat
    var fraction: Double          // 0–1
    var color: Color
    var trackColor: Color
    @ViewBuilder var center: () -> Center

    var body: some View {
        ZStack {
            Circle()
                .stroke(trackColor, lineWidth: stroke)
            Circle()
                .trim(from: 0, to: max(0, min(1, fraction)))
                .stroke(color, style: StrokeStyle(lineWidth: stroke, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .animation(.spring(response: 0.35, dampingFraction: 0.75), value: fraction)
            center()
        }
        .frame(width: size, height: size)
    }
}

extension RingView where Center == EmptyView {
    init(size: CGFloat, stroke: CGFloat, fraction: Double, color: Color, trackColor: Color) {
        self.init(size: size, stroke: stroke, fraction: fraction, color: color, trackColor: trackColor) {
            EmptyView()
        }
    }
}
