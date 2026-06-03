import Foundation

private let _dateKeyFormatter: DateFormatter = {
    let f = DateFormatter()
    f.dateFormat = "yyyy-MM-dd"
    f.locale = Locale(identifier: "en_US_POSIX")
    return f
}()

extension Date {
    var dateKey: String { _dateKeyFormatter.string(from: self) }

    static func from(dateKey: String) -> Date? {
        _dateKeyFormatter.date(from: dateKey)
    }
}

extension Calendar {
    static let gregorianSunday: Calendar = {
        var cal = Calendar(identifier: .gregorian)
        cal.firstWeekday = 1  // Sunday
        cal.locale = Locale(identifier: "ko_KR")
        return cal
    }()
}
