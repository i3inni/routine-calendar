import SwiftUI

struct AppBannerView: View {
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        HStack(spacing: 9) {
            InterlockingRingsIcon(color: Color.rcText(scheme))
                .frame(width: 34, height: 23)

            Text("같이해")
                .font(.custom("Ownglyph_PDH-Rg", size: 26))
                .foregroundStyle(Color.rcText(scheme))
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 16)
        .padding(.vertical, 14)
        .background(Color.rcBg(scheme))
    }
}

// MARK: - 링 두 개만 (배경 없음)

struct InterlockingRingsIcon: View {
    let color: Color

    var body: some View {
        GeometryReader { geo in
            let w      = geo.size.width
            let h      = geo.size.height
            let d      = h                    // 원 지름 = 높이
            let stroke = d * 0.155            // 선 굵기
            let dist   = w - d                // 두 중심 사이 거리

            ZStack {
                Circle()
                    .stroke(color, lineWidth: stroke)
                    .frame(width: d, height: d)
                    .position(x: d / 2, y: h / 2)

                Circle()
                    .stroke(color, lineWidth: stroke)
                    .frame(width: d, height: d)
                    .position(x: d / 2 + dist, y: h / 2)
            }
        }
    }
}
