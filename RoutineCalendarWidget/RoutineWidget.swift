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
        // 여기선 날짜가 바뀌는 자정에만 한 번 갱신하면 충분.
        let midnight = Calendar.gregorianSunday.startOfDay(
            for: Calendar.gregorianSunday.date(byAdding: .day, value: 1, to: Date()) ?? Date()
        )
        completion(Timeline(entries: [entry], policy: .after(midnight)))
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
