import SwiftUI

struct SplashView: View {
    @Environment(\.colorScheme) private var scheme
    @State private var scale: CGFloat = 0.7
    @State private var opacity: Double = 0

    var body: some View {
        ZStack {
            Color.rcBg(scheme).ignoresSafeArea()

            VStack(spacing: 16) {
                InterlockingRingsIcon(color: Color.rcText(scheme))
                    .frame(width: 88, height: 60)

                Text("같이해")
                    .font(.custom("Ownglyph_PDH-Rg", size: 34))
                    .foregroundStyle(Color.rcText(scheme))
            }
            .scaleEffect(scale)
            .opacity(opacity)
        }
        .onAppear {
            withAnimation(.spring(response: 0.6, dampingFraction: 0.7)) {
                scale = 1.0
                opacity = 1.0
            }
        }
    }
}
