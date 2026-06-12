import WidgetKit
import SwiftUI

@main
struct RoutineWidgetBundle: WidgetBundle {
    var body: some Widget {
        HomeRoutineWidget()
        CalendarWidget()
        RoutineListWidget()
        FriendsWidget()
        LockScreenListWidget()
        LockScreenCircularWidget()
    }
}
