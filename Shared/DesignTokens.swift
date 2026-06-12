import SwiftUI

// MARK: - Color tokens (README: Design Tokens)

extension Color {
    // Backgrounds
    static func rcBg(_ s: ColorScheme) -> Color            { s == .dark ? Color(hex: "000000") : Color(hex: "F2F2F7") }
    static func rcCard(_ s: ColorScheme) -> Color          { s == .dark ? Color(hex: "1C1C1E") : Color.white }
    static func rcCard2(_ s: ColorScheme) -> Color         { s == .dark ? Color(hex: "2C2C2E") : Color(hex: "F2F2F7") }
    // Text
    static func rcText(_ s: ColorScheme) -> Color          { s == .dark ? .white : .black }
    static func rcText2(_ s: ColorScheme) -> Color         { s == .dark ? .white.opacity(0.6)  : Color(r: 60, g: 60, b: 67).opacity(0.6) }
    static func rcText3(_ s: ColorScheme) -> Color         { s == .dark ? .white.opacity(0.3)  : Color(r: 60, g: 60, b: 67).opacity(0.3) }
    // Separator / fills
    static func rcSeparator(_ s: ColorScheme) -> Color     { s == .dark ? Color(r: 84, g: 84, b: 88).opacity(0.55) : Color(r: 60, g: 60, b: 67).opacity(0.16) }
    static func rcEmptyFill(_ s: ColorScheme) -> Color     { s == .dark ? .white.opacity(0.18) : Color(r: 60, g: 60, b: 67).opacity(0.14) }
    // Accent
    static func rcAccent(_ s: ColorScheme) -> Color        { s == .dark ? .white : Color(hex: "1C1C1E") }
    static func rcAccentText(_ s: ColorScheme) -> Color    { s == .dark ? .black : .white }
    // Destructive
    static let rcDestructive = Color(hex: "FF3B30")

    // init(hex:) is defined in Shared/ColorExtensions.swift (shared with widget target)

    fileprivate init(r: Double, g: Double, b: Double) {
        self.init(red: r / 255, green: g / 255, blue: b / 255)
    }
}

// MARK: - Typography helpers

extension Font {
    // README scale
    static let rcMonthTitle  = Font.system(size: 30, weight: .bold)
    static let rcYearLabel   = Font.system(size: 19, weight: .medium)
    static let rcDayHeader   = Font.system(size: 20, weight: .bold)
    static let rcRoutineName = Font.system(size: 16.5, weight: .medium)
    static let rcBody        = Font.system(size: 17, weight: .regular)
    static let rcBodyMedium  = Font.system(size: 17, weight: .medium)
    static let rcMeta        = Font.system(size: 12.5, weight: .regular)
    static let rcSectionLabel = Font.system(size: 13, weight: .semibold)
    static let rcWidgetHero  = Font.system(size: 52, weight: .heavy).monospacedDigit()
    static let rcWidgetRing  = Font.system(size: 26, weight: .heavy).monospacedDigit()
}

// MARK: - View modifiers

extension View {
    func rcCard(_ scheme: ColorScheme, radius: CGFloat = 20) -> some View {
        self
            .background(Color.rcCard(scheme))
            .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
    }
}
