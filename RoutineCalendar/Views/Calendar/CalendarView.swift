import SwiftUI
import UIKit

struct CalendarView: View {
    @Environment(RoutineStore.self) private var store
    @Environment(SettingsStore.self) private var settings
    @Environment(DeepLinkRouter.self) private var deepLink
    @Environment(\.colorScheme) private var scheme

    @State private var displayYear: Int  = Calendar.gregorianSunday.component(.year,  from: Date())
    @State private var displayMonth: Int = Calendar.gregorianSunday.component(.month, from: Date())
    @State private var selectedDateKey: String = Date().dateKey
    @State private var routineToEdit: Routine?
    @State private var showAddSheet = false
    @State private var showSettings = false

    var body: some View {
        NavigationStack {
            ZStack(alignment: .top) {
                Color.rcBg(scheme).ignoresSafeArea()

                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 0) {
                        AppBannerView()

                        MonthHeaderView(
                            year: displayYear,
                            month: displayMonth,
                            onPrev: prevMonth,
                            onNext: nextMonth,
                            onToday: jumpToToday,
                            onSettings: { showSettings = true }
                        )
                        .padding(.top, 4)

                        WeekdayHeaderView()

                        MonthGridView(
                            year: displayYear,
                            month: displayMonth,
                            selectedDateKey: $selectedDateKey,
                            calendarStyle: settings.calendarStyle
                        )
                        // 좌우 스와이프로 달 이동 (가로 이동이 우세할 때만 → 세로 스크롤/날짜 탭과 충돌 방지)
                        .simultaneousGesture(
                            DragGesture(minimumDistance: 20)
                                .onEnded { value in
                                    let dx = value.translation.width
                                    let dy = value.translation.height
                                    guard abs(dx) > abs(dy) * 1.5, abs(dx) > 50 else { return }
                                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                                    if dx < 0 { nextMonth() } else { prevMonth() }
                                }
                        )

                        // Separator
                        Rectangle()
                            .fill(Color.rcSeparator(scheme))
                            .frame(height: 0.5)
                            .padding(.top, 8)

                        DayPanelView(
                            dateKey: selectedDateKey,
                            routineToEdit: $routineToEdit,
                            showAddSheet: $showAddSheet
                        )
                    }
                }
            }
            .navigationBarHidden(true)
            .navigationDestination(isPresented: $showSettings) {
                SettingsView()
            }
        }
        .sheet(isPresented: $showAddSheet) {
            RoutineSheetView(mode: .add, routine: nil)
        }
        .sheet(item: $routineToEdit) { routine in
            RoutineSheetView(mode: .edit, routine: routine)
        }
        .preferredColorScheme(colorSchemeOverride)
        // 위젯 "＋" 딥링크로 들어오면 추가 시트 열기
        .onChange(of: deepLink.pendingAddRoutine) { _, pending in
            if pending { showAddSheet = true; deepLink.pendingAddRoutine = false }
        }
        .onAppear {
            if deepLink.pendingAddRoutine { showAddSheet = true; deepLink.pendingAddRoutine = false }
        }
    }

    private var colorSchemeOverride: ColorScheme? {
        switch settings.theme {
        case .light:  return .light
        case .dark:   return .dark
        case .system: return nil
        }
    }

    private func prevMonth() {
        var dc = DateComponents(); dc.year = displayYear; dc.month = displayMonth; dc.day = 1
        if let d = Calendar.gregorianSunday.date(from: dc),
           let prev = Calendar.gregorianSunday.date(byAdding: .month, value: -1, to: d) {
            displayYear  = Calendar.gregorianSunday.component(.year,  from: prev)
            displayMonth = Calendar.gregorianSunday.component(.month, from: prev)
        }
    }

    private func nextMonth() {
        var dc = DateComponents(); dc.year = displayYear; dc.month = displayMonth; dc.day = 1
        if let d = Calendar.gregorianSunday.date(from: dc),
           let next = Calendar.gregorianSunday.date(byAdding: .month, value: 1, to: d) {
            displayYear  = Calendar.gregorianSunday.component(.year,  from: next)
            displayMonth = Calendar.gregorianSunday.component(.month, from: next)
        }
    }

    private func jumpToToday() {
        let today = Date()
        displayYear  = Calendar.gregorianSunday.component(.year,  from: today)
        displayMonth = Calendar.gregorianSunday.component(.month, from: today)
        selectedDateKey = today.dateKey
    }
}
