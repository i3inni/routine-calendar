import SwiftUI

struct RCToggleStyle: ToggleStyle {
    @Environment(\.colorScheme) private var scheme

    func makeBody(configuration: Configuration) -> some View {
        Button {
            withAnimation(.easeInOut(duration: 0.2)) {
                configuration.isOn.toggle()
            }
        } label: {
            ZStack {
                // Track
                Capsule()
                    .fill(configuration.isOn
                          ? Color.rcAccent(scheme)
                          : Color(red: 120/255, green: 120/255, blue: 128/255).opacity(0.32))
                    .frame(width: 51, height: 31)

                // Knob — README: white normally, black in dark mode when ON
                Circle()
                    .fill(configuration.isOn && scheme == .dark ? Color.black : Color.white)
                    .frame(width: 27, height: 27)
                    .shadow(color: .black.opacity(0.15), radius: 2, x: 0, y: 1)
                    .offset(x: configuration.isOn ? 10 : -10)
                    .animation(.easeInOut(duration: 0.2), value: configuration.isOn)
            }
        }
        .buttonStyle(.plain)
    }
}
