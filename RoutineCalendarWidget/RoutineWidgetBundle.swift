import WidgetKit
import SwiftUI

@main
struct RoutineWidgetBundle: WidgetBundle {
    var body: some Widget {
        HomeRoutineWidget()
        RoutineListWidget()
        FriendsWidget()
        LockScreenListWidget()
        LockScreenCircularWidget()
    }
}
