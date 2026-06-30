import WidgetKit
import SwiftUI

// MARK: - Timeline Provider

struct LockScreenProvider: TimelineProvider {
    func placeholder(in context: Context) -> LockScreenEntry {
        .sample
    }

    func getSnapshot(in context: Context, completion: @escaping (LockScreenEntry) -> Void) {
        // 위젯 갤러리(미리보기)에선 샘플로 예쁘게, 실제 배치 시엔 실데이터.
        completion(context.isPreview ? .sample : WidgetDataReader.readEntry())
    }

    func getTimeline(in context: Context, completion: @escaping (Timeline<LockScreenEntry>) -> Void) {
        let entry = WidgetDataReader.readEntry()
        // 데이터 변경 시엔 앱이 reloadAllTimelines()로 즉시 갱신.
        // 날짜가 바뀌는 시점은 자정이 아니라 '리셋 시각'(새벽 N시)이므로 그때 한 번 갱신한다.
        completion(Timeline(entries: [entry], policy: .after(Self.nextDayBoundary())))
    }

    /// 다음 하루 경계(리셋 시각). resetHour=0이면 자정과 동일.
    private static func nextDayBoundary(from now: Date = Date()) -> Date {
        let cal = Calendar.gregorianSunday
        var comps = cal.dateComponents([.year, .month, .day], from: now)
        comps.hour = DayBoundary.resetHour
        let todayReset = cal.date(from: comps) ?? now
        return todayReset > now ? todayReset : (cal.date(byAdding: .day, value: 1, to: todayReset) ?? now)
    }
}

// MARK: - 잠금화면 직사각형 위젯 (루틴 목록)

struct LockScreenListWidget: Widget {
    let kind = "LockScreenListWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: LockScreenProvider()) { entry in
            LockScreenListView(entry: entry)
                .containerBackground(.fill, for: .widget)
                .widgetURL(URL(string: "routinecalendar://today"))
        }
        .configurationDisplayName("오늘 루틴 목록")
        .description("잠금화면에서 오늘 남은 루틴을 확인하세요.")
        .supportedFamilies([.accessoryRectangular])
    }
}

// MARK: - 잠금화면 원형 위젯 (진행률)

struct LockScreenCircularWidget: Widget {
    let kind = "LockScreenCircularWidget"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: kind, provider: LockScreenProvider()) { entry in
            LockScreenCircularView(entry: entry)
                .containerBackground(.fill, for: .widget)
                .widgetURL(URL(string: "routinecalendar://today"))
        }
        .configurationDisplayName("오늘 진행률")
        .description("잠금화면에서 오늘 루틴 진행률을 확인하세요.")
        .supportedFamilies([.accessoryCircular])
    }
}
