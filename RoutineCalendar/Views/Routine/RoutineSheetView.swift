import SwiftUI

enum RoutineSheetMode { case add, edit }

struct RoutineSheetView: View {
    let mode: RoutineSheetMode
    let routine: Routine?

    @Environment(RoutineStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var scheme

    @State private var name: String = ""
    @State private var type: RoutineType = .check
    @State private var target: Int = 8
    @State private var unit: String = ""
    @State private var anytime: Bool = true
    @State private var reminderOn: Bool = false
    @State private var reminderTime: Date = Calendar.current.startOfDay(for: Date()).addingTimeInterval(8 * 3600)
    @State private var repeatMode: RepeatMode = .daily
    @State private var customDays: Set<Int> = []
    @State private var showDeleteConfirm = false

    private let dayLabels = ["일", "월", "화", "수", "목", "금", "토"]

    var body: some View {
        NavigationStack {
            ZStack {
                Color.rcBg(scheme).ignoresSafeArea()

                ScrollView {
                    VStack(spacing: 0) {
                        // Name field
                        TextField("루틴 이름 (예: 물 마시기)", text: $name)
                            .font(.system(size: 17))
                            .padding()
                            .rcCard(scheme, radius: 16)
                            .padding(.horizontal, 16)
                            .padding(.top, 16)

                        // Type
                        sectionLabel("유형")
                        HStack(spacing: 8) {
                            typeButton(.check, label: "한 번 완료")
                            typeButton(.count, label: "횟수 세기")
                        }
                        .padding(.horizontal, 16)

                        // Count target + unit
                        if type == .count {
                            sectionLabel("목표")
                            VStack(spacing: 0) {
                                HStack {
                                    Text("목표 횟수")
                                        .font(.rcBody)
                                        .foregroundStyle(Color.rcText(scheme))
                                    Spacer()
                                    stepperView
                                }
                                .padding()
                                Rectangle().fill(Color.rcSeparator(scheme)).frame(height: 0.5).padding(.leading, 16)
                                HStack {
                                    Text("단위")
                                        .font(.rcBody)
                                        .foregroundStyle(Color.rcText(scheme))
                                    Spacer()
                                    TextField("잔, 회…", text: $unit)
                                        .font(.rcBody)
                                        .multilineTextAlignment(.trailing)
                                        .frame(maxWidth: 100)
                                }
                                .padding()
                            }
                            .rcCard(scheme, radius: 16)
                            .padding(.horizontal, 16)
                        }

                        // Repeat
                        sectionLabel("반복")
                        VStack(spacing: 0) {
                            ForEach(Array(RepeatMode.allCases.enumerated()), id: \.element) { idx, mode in
                                if idx > 0 {
                                    Rectangle().fill(Color.rcSeparator(scheme)).frame(height: 0.5).padding(.leading, 16)
                                }
                                Button { withAnimation(.easeInOut(duration: 0.2)) { repeatMode = mode } } label: {
                                    HStack {
                                        Text(mode.label)
                                            .font(.rcBody)
                                            .foregroundStyle(Color.rcText(scheme))
                                        Spacer()
                                        if repeatMode == mode {
                                            Image(systemName: "checkmark")
                                                .font(.system(size: 15, weight: .semibold))
                                                .foregroundStyle(Color.rcAccent(scheme))
                                        }
                                    }
                                    .padding()
                                }

                                // 직접 선택일 때 요일 버튼 표시
                                if mode == .custom && repeatMode == .custom {
                                    Rectangle().fill(Color.rcSeparator(scheme)).frame(height: 0.5).padding(.leading, 16)
                                    HStack(spacing: 8) {
                                        ForEach(0..<7, id: \.self) { day in
                                            let selected = customDays.contains(day)
                                            Button {
                                                if selected { customDays.remove(day) }
                                                else { customDays.insert(day) }
                                            } label: {
                                                Text(dayLabels[day])
                                                    .font(.system(size: 14, weight: .semibold))
                                                    .foregroundStyle(selected ? Color.rcAccentText(scheme) : Color.rcText2(scheme))
                                                    .frame(width: 36, height: 36)
                                                    .background(
                                                        selected ? Color.rcAccent(scheme) : Color.rcCard2(scheme),
                                                        in: Circle()
                                                    )
                                            }
                                        }
                                    }
                                    .frame(maxWidth: .infinity)
                                    .padding(.horizontal, 16)
                                    .padding(.vertical, 12)
                                }
                            }

                            Rectangle().fill(Color.rcSeparator(scheme)).frame(height: 0.5).padding(.leading, 16)
                            HStack {
                                Text("하루 중 아무때나")
                                    .font(.rcBody)
                                    .foregroundStyle(Color.rcText(scheme))
                                Spacer()
                                Toggle("", isOn: $anytime).labelsHidden()
                                    .toggleStyle(RCToggleStyle())
                            }
                            .padding()
                        }
                        .rcCard(scheme, radius: 16)
                        .padding(.horizontal, 16)

                        // Reminder
                        sectionLabel("알림")
                        VStack(spacing: 0) {
                            HStack {
                                Text("알림 받기")
                                    .font(.rcBody)
                                    .foregroundStyle(Color.rcText(scheme))
                                Spacer()
                                Toggle("", isOn: $reminderOn).labelsHidden()
                                    .toggleStyle(RCToggleStyle())
                            }
                            .padding()
                            if reminderOn {
                                Rectangle().fill(Color.rcSeparator(scheme)).frame(height: 0.5).padding(.leading, 16)
                                HStack {
                                    Text("시간")
                                        .font(.rcBody)
                                        .foregroundStyle(Color.rcText(scheme))
                                    Spacer()
                                    DatePicker("", selection: $reminderTime, displayedComponents: .hourAndMinute)
                                        .labelsHidden()
                                        .environment(\.locale, Locale(identifier: "ko_KR"))
                                }
                                .padding()
                            }
                        }
                        .rcCard(scheme, radius: 16)
                        .padding(.horizontal, 16)
                        .animation(.easeInOut(duration: 0.2), value: reminderOn)

                        // Delete (edit mode only)
                        if mode == .edit {
                            Button(role: .destructive) { showDeleteConfirm = true } label: {
                                Text("루틴 삭제")
                                    .font(.rcBody)
                                    .foregroundStyle(Color.rcDestructive)
                                    .frame(maxWidth: .infinity)
                                    .padding()
                                    .rcCard(scheme, radius: 16)
                            }
                            .padding(.horizontal, 16)
                            .padding(.top, 12)
                        }

                        Spacer(minLength: 34)
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("취소") { dismiss() }
                        .foregroundStyle(Color.rcText2(scheme))
                }
                ToolbarItem(placement: .principal) {
                    Text(mode == .add ? "새 루틴" : "루틴 편집")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(Color.rcText(scheme))
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("저장") { save() }
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(name.isEmpty ? Color.rcText3(scheme) : Color.rcAccent(scheme))
                        .disabled(name.isEmpty)
                }
            }
        }
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
        .onAppear { populateFromRoutine() }
        .alert("루틴을 삭제할까요?", isPresented: $showDeleteConfirm) {
            Button("아니요", role: .cancel) {}
            Button("예", role: .destructive) {
                if let r = routine { store.deleteRoutine(r); NotificationManager.shared.cancel(for: r.id) }
                dismiss()
            }
        } message: {
            Text("이 루틴과 기록이 삭제됩니다.")
        }
    }

    // MARK: Helpers

    private func typeButton(_ t: RoutineType, label: String) -> some View {
        Button { type = t } label: {
            Text(label)
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(type == t ? Color.rcAccent(scheme) : Color.rcText2(scheme))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .background(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .fill(type == t ? Color.rcAccent(scheme).opacity(0.08) : Color.rcCard(scheme))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(type == t ? Color.rcAccent(scheme) : Color.rcSeparator(scheme), lineWidth: 2)
                        )
                )
        }
    }

    private var stepperView: some View {
        HStack(spacing: 12) {
            Button { if target > 2 { target -= 1 } } label: {
                Image(systemName: "minus")
                    .font(.system(size: 22, weight: .medium))
                    .foregroundStyle(Color.rcAccent(scheme))
            }
            Text("\(target)")
                .font(.system(size: 17, weight: .semibold).monospacedDigit())
                .foregroundStyle(Color.rcText(scheme))
                .frame(minWidth: 32)
            Button { target += 1 } label: {
                Image(systemName: "plus")
                    .font(.system(size: 22, weight: .medium))
                    .foregroundStyle(Color.rcAccent(scheme))
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 6)
        .background(Color.rcCard2(scheme), in: RoundedRectangle(cornerRadius: 9, style: .continuous))
    }

    private func sectionLabel(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(Color.rcText2(scheme))
            .tracking(0.3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 7)
    }

    private func populateFromRoutine() {
        guard let r = routine else { return }
        name       = r.name
        type       = r.type
        target     = r.target
        unit       = r.unit
        anytime    = r.anytime
        repeatMode = r.repeatMode
        customDays = Set(r.repeatDays)
        if let rem = r.reminder {
            reminderOn = true
            let parts = rem.split(separator: ":").compactMap { Int($0) }
            if parts.count == 2 {
                var dc = DateComponents(); dc.hour = parts[0]; dc.minute = parts[1]
                reminderTime = Calendar.current.date(from: dc) ?? reminderTime
            }
        }
    }

    private func save() {
        guard !name.isEmpty else { return }
        // 직접 선택인데 아무것도 선택 안 했으면 매일로 폴백
        let finalRepeatMode = (repeatMode == .custom && customDays.isEmpty) ? .daily : repeatMode
        let finalDays = finalRepeatMode == .custom ? Array(customDays).sorted() : []

        let reminderString: String? = reminderOn
            ? String(format: "%02d:%02d",
                     Calendar.current.component(.hour, from: reminderTime),
                     Calendar.current.component(.minute, from: reminderTime))
            : nil

        let updated = Routine(
            id: routine?.id ?? UUID(),
            name: name, type: type, target: type == .check ? 1 : target,
            unit: unit, reminder: reminderString, anytime: anytime,
            repeatMode: finalRepeatMode, repeatDays: finalDays
        )
        if mode == .add {
            store.addRoutine(updated)
        } else {
            store.updateRoutine(updated)
        }
        NotificationManager.shared.schedule(for: updated)
        dismiss()
    }
}
