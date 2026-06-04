import SwiftUI

struct SettingsView: View {
    @Environment(SettingsStore.self) private var settings
    @Environment(SessionStore.self) private var session
    @Environment(\.colorScheme) private var scheme

    @State private var nameDraft = ""
    @State private var isSavingName = false

    var body: some View {
        @Bindable var settings = settings
        ZStack {
            Color.rcBg(scheme).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    // 내 이름 (친구에게 표시됨) — 서버 닉네임과 연동
                    SectionLabel("내 이름")
                    SettingsCard(scheme: scheme) {
                        HStack(spacing: 8) {
                            TextField("친구에게 표시될 이름", text: $nameDraft)
                                .font(.rcBody)
                                .foregroundStyle(Color.rcText(scheme))
                                .submitLabel(.done)
                                .onSubmit { saveNickname() }
                            if isSavingName {
                                ProgressView()
                            }
                        }
                        .padding()
                    }
                    Text("친구 목록에서 다른 사람에게 보이는 이름이에요. 입력 후 완료를 누르면 저장돼요.")
                        .font(.system(size: 12.5))
                        .foregroundStyle(Color.rcText2(scheme))
                        .padding(.horizontal, 20)
                        .padding(.top, 6)

                    // 화면 테마
                    SectionLabel("화면 테마")
                    SettingsCard(scheme: scheme) {
                        ForEach(Array(AppTheme.allCases.enumerated()), id: \.element) { idx, opt in
                            if idx > 0 { Divider().padding(.leading, 16) }
                            SettingsRow(
                                label: opt.label,
                                subLabel: opt.subLabel,
                                isSelected: settings.theme == opt,
                                preview: nil
                            ) {
                                settings.theme = opt
                                settings.save()
                            }
                        }
                    }

                    // 캘린더 날짜 표시
                    SectionLabel("캘린더 날짜 표시")
                    SettingsCard(scheme: scheme) {
                        ForEach(Array(CalendarStyle.allCases.enumerated()), id: \.element) { idx, opt in
                            if idx > 0 { Divider().padding(.leading, 16) }
                            SettingsRow(
                                label: opt.label,
                                subLabel: opt.subLabel,
                                isSelected: settings.calendarStyle == opt,
                                preview: AnyView(calStylePreview(opt))
                            ) {
                                settings.calendarStyle = opt
                                settings.save()
                            }
                        }
                    }

                    // 완료 체크 모양
                    SectionLabel("완료 체크 모양")
                    SettingsCard(scheme: scheme) {
                        ForEach(Array(CheckStyle.allCases.enumerated()), id: \.element) { idx, opt in
                            if idx > 0 { Divider().padding(.leading, 16) }
                            SettingsRow(
                                label: opt.label,
                                subLabel: nil,
                                isSelected: settings.checkStyle == opt,
                                preview: AnyView(checkStylePreview(opt))
                            ) {
                                settings.checkStyle = opt
                                settings.save()
                            }
                        }
                    }

                    // Footer
                    Text("모든 설정과 루틴 기록은 이 기기에만 저장됩니다.\n로그인이나 동기화는 없습니다.")
                        .font(.system(size: 12.5))
                        .foregroundStyle(Color.rcText2(scheme))
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)
                        .padding(.horizontal, 16)
                        .padding(.top, 24)
                        .padding(.bottom, 40)
                }
            }
        }
        .navigationTitle("설정")
        .navigationBarTitleDisplayMode(.large)
        .onAppear {
            if nameDraft.isEmpty { nameDraft = session.currentUser?.nickname ?? "" }
        }
    }

    /// 입력한 닉네임을 서버에 저장 (변경 없으면 무시).
    private func saveNickname() {
        let name = nameDraft.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty, name != session.currentUser?.nickname else { return }
        isSavingName = true
        Task {
            await session.updateNickname(name)
            nameDraft = session.currentUser?.nickname ?? name
            isSavingName = false
        }
    }

    // MARK: Previews

    @ViewBuilder
    func calStylePreview(_ style: CalendarStyle) -> some View {
        switch style {
        case .dots:
            HStack(spacing: 2.5) {
                ForEach(0..<3, id: \.self) { i in
                    Circle().fill(i < 2 ? Color.rcAccent(scheme) : Color.rcEmptyFill(scheme))
                        .frame(width: 4.5, height: 4.5)
                }
            }
        case .bar:
            ZStack(alignment: .leading) {
                Capsule().fill(Color.rcEmptyFill(scheme)).frame(width: 20, height: 3.5)
                Capsule().fill(Color.rcAccent(scheme)).frame(width: 13, height: 3.5)
            }
        case .ring:
            RingView(size: 18, stroke: 2, fraction: 0.6,
                     color: Color.rcAccent(scheme), trackColor: Color.rcEmptyFill(scheme))
        }
    }

    @ViewBuilder
    func checkStylePreview(_ style: CheckStyle) -> some View {
        switch style {
        case .circle:
            ZStack {
                Circle().fill(Color.rcAccent(scheme))
                Image(systemName: "checkmark").font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Color.rcAccentText(scheme))
            }.frame(width: 22, height: 22)
        case .square:
            ZStack {
                RoundedRectangle(cornerRadius: 6, style: .continuous).fill(Color.rcAccent(scheme))
                Image(systemName: "checkmark").font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(Color.rcAccentText(scheme))
            }.frame(width: 22, height: 22)
        case .ring:
            RingView(size: 22, stroke: 2.5, fraction: 1.0,
                     color: Color.rcAccent(scheme), trackColor: Color.rcEmptyFill(scheme)) {
                Image(systemName: "checkmark").font(.system(size: 9, weight: .semibold))
                    .foregroundStyle(Color.rcAccent(scheme))
            }
        }
    }
}

// MARK: - Subviews

private struct SectionLabel: View {
    let text: String
    @Environment(\.colorScheme) private var scheme
    init(_ text: String) { self.text = text }
    var body: some View {
        Text(text.uppercased())
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(Color.rcText2(scheme))
            .tracking(0.3)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 20)
            .padding(.top, 20)
            .padding(.bottom, 7)
    }
}

private struct SettingsCard<Content: View>: View {
    let scheme: ColorScheme
    @ViewBuilder let content: () -> Content
    var body: some View {
        VStack(spacing: 0) { content() }
            .rcCard(scheme, radius: 16)
            .padding(.horizontal, 16)
    }
}

private struct SettingsRow: View {
    let label: String
    let subLabel: String?
    let isSelected: Bool
    let preview: AnyView?
    let action: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        Button(action: action) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text(label).font(.rcBody).foregroundStyle(Color.rcText(scheme))
                    if let sub = subLabel {
                        Text(sub).font(.system(size: 12.5)).foregroundStyle(Color.rcText2(scheme))
                    }
                }
                Spacer()
                if let preview { preview.frame(width: 28) }
                if isSelected {
                    Image(systemName: "checkmark")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Color.rcAccent(scheme))
                        .padding(.leading, 8)
                }
            }
            .padding()
        }
    }
}
