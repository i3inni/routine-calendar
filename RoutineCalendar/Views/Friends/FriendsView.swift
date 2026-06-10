import SwiftUI

struct FriendsView: View {
    @Environment(FriendsStore.self) private var friendsStore
    @Environment(DeepLinkRouter.self) private var deepLink
    @Environment(\.colorScheme) private var scheme

    @State private var showAddSheet = false
    @State private var prefillId: String?
    @State private var requestTab: RequestTab = .received
    @State private var showKakaoSheet = false
    @State private var loadingKakao = false
    @State private var kakaoAlert: String?

    private enum RequestTab { case received, sent }

    private var sortedFriends: [Friend] {
        friendsStore.friends.sorted { !$0.isAllDone && $1.isAllDone }
    }

    private var notDoneCount: Int {
        friendsStore.friends.filter { !$0.isAllDone }.count
    }

    var body: some View {
        ZStack {
            Color.rcBg(scheme).ignoresSafeArea()
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    AppBannerView()

                    // Header
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("친구")
                                .font(.system(size: 30, weight: .bold))
                                .foregroundStyle(Color.rcText(scheme))
                            Text(notDoneCount == 0
                                 ? "모두 오늘 루틴을 끝냈어요."
                                 : "\(notDoneCount)명이 아직 루틴을 끝내지 않았어요")
                                .font(.system(size: 15))
                                .foregroundStyle(Color.rcText2(scheme))
                        }
                        Spacer()
                        Button { showAddSheet = true } label: {
                            Image(systemName: "plus")
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(Color.rcAccent(scheme))
                                .frame(width: 36, height: 36)
                                .background(Color.rcCard(scheme), in: Circle())
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 16)
                    .padding(.bottom, 16)

                    // 친구 요청 (받은/보낸 탭)
                    if !friendsStore.incomingRequests.isEmpty || !friendsStore.outgoingRequests.isEmpty {
                        Picker("", selection: $requestTab) {
                            Text("받은 \(friendsStore.incomingRequests.count)").tag(RequestTab.received)
                            Text("보낸 \(friendsStore.outgoingRequests.count)").tag(RequestTab.sent)
                        }
                        .pickerStyle(.segmented)
                        .padding(.horizontal, 16)
                        .padding(.bottom, 12)

                        VStack(spacing: 10) {
                            if requestTab == .received {
                                if friendsStore.incomingRequests.isEmpty {
                                    emptyRequestText("받은 요청이 없어요")
                                } else {
                                    ForEach(friendsStore.incomingRequests) { request in
                                        RequestRowView(
                                            request: request,
                                            onAccept: { Task { await friendsStore.acceptRequest(request) } },
                                            onDecline: { Task { await friendsStore.declineRequest(request) } }
                                        )
                                    }
                                }
                            } else {
                                if friendsStore.outgoingRequests.isEmpty {
                                    emptyRequestText("보낸 요청이 없어요")
                                } else {
                                    ForEach(friendsStore.outgoingRequests) { request in
                                        SentRequestRowView(request: request)
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                        .padding(.bottom, 20)
                    }

                    // Friend cards with custom swipe-to-delete
                    VStack(spacing: 12) {
                        ForEach(sortedFriends) { friend in
                            SwipeToDeleteCard(
                                scheme: scheme,
                                onDelete: { friendsStore.removeFriend(friend) }
                            ) {
                                FriendCardView(friend: friend)
                            }
                        }

                        // + 친구 추가
                        Button { showAddSheet = true } label: {
                            HStack {
                                Image(systemName: "plus")
                                Text("친구 추가")
                            }
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Color.rcText2(scheme))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                            .overlay(
                                RoundedRectangle(cornerRadius: 16, style: .continuous)
                                    .stroke(Color.rcSeparator(scheme),
                                            style: StrokeStyle(lineWidth: 1.5, dash: [6, 4]))
                            )
                        }

                        // 카카오로 친구 찾기
                        Button {
                            Task { await loadKakaoFriends() }
                        } label: {
                            HStack(spacing: 8) {
                                if loadingKakao {
                                    ProgressView().tint(Color(hex: "191600"))
                                } else {
                                    Image(systemName: "message.fill")
                                    Text("카카오로 친구 찾기")
                                }
                            }
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(Color(hex: "191600"))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 15)
                            .background(Color(hex: "FEE500"),
                                        in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                        .disabled(loadingKakao)
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 100)
                }
            }
            .refreshable { await friendsStore.refresh() }
        }
        .sheet(isPresented: $showAddSheet) {
            AddFriendSheetView(prefillId: prefillId)
        }
        .sheet(isPresented: $showKakaoSheet) {
            KakaoFriendSheet()
        }
        .alert("카카오 친구 찾기", isPresented: .init(
            get: { kakaoAlert != nil },
            set: { if !$0 { kakaoAlert = nil } }
        )) {
            Button("확인", role: .cancel) {}
        } message: {
            Text(kakaoAlert ?? "")
        }
        .task { await friendsStore.refresh() }
        // 딥링크로 들어온 친구 ID → 시트 열고 자동 입력
        .onChange(of: deepLink.pendingFriendHandle) { _, new in
            consumeDeepLink(new)
        }
        .onAppear { consumeDeepLink(deepLink.pendingFriendHandle) }
    }

    private func consumeDeepLink(_ handle: String?) {
        guard let handle else { return }
        prefillId = handle
        showAddSheet = true
        deepLink.pendingFriendHandle = nil   // 1회 소비
    }

    private func loadKakaoFriends() async {
        loadingKakao = true
        defer { loadingKakao = false }
        switch await friendsStore.findKakaoFriends() {
        case .ok:
            if friendsStore.kakaoCandidates.isEmpty {
                kakaoAlert = "카카오 친구 중 이 앱을 쓰는 친구를 찾지 못했어요."
            } else {
                showKakaoSheet = true
            }
        case .alreadyLinked:
            kakaoAlert = "이 카카오는 이미 다른 계정에 연동돼 있어요."
        case .notConfigured:
            kakaoAlert = "카카오 설정이 필요해요."
        case .failed:
            kakaoAlert = "친구를 불러오지 못했어요. 잠시 후 다시 시도해 주세요."
        }
    }

    private func emptyRequestText(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 14))
            .foregroundStyle(Color.rcText3(scheme))
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
    }
}

// MARK: - 받은 친구 요청 카드

private struct RequestRowView: View {
    let request: FriendRequest
    let onAccept: () -> Void
    let onDecline: () -> Void
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        HStack(spacing: 12) {
            // Avatar
            Circle()
                .fill(Color.rcCard2(scheme))
                .frame(width: 40, height: 40)
                .overlay(
                    Text(request.initial)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Color.rcText(scheme))
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(request.fromName)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.rcText(scheme))
                Text("친구 요청을 보냈어요")
                    .font(.rcMeta)
                    .foregroundStyle(Color.rcText2(scheme))
            }

            Spacer()

            // 거절
            Button(action: onDecline) {
                Text("거절")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.rcText2(scheme))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(Color.rcCard2(scheme), in: Capsule())
            }

            // 수락
            Button(action: onAccept) {
                Text("수락")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Color.rcAccentText(scheme))
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(Color.rcAccent(scheme), in: Capsule())
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.rcCard(scheme))
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

// MARK: - 보낸 친구 요청 카드 (조회 전용)

private struct SentRequestRowView: View {
    let request: FriendRequest   // from* 필드에 받는 사람 정보가 담겨 있음
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        HStack(spacing: 12) {
            Circle()
                .fill(Color.rcCard2(scheme))
                .frame(width: 40, height: 40)
                .overlay(
                    Text(request.initial)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Color.rcText(scheme))
                )

            VStack(alignment: .leading, spacing: 2) {
                Text(request.fromName)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Color.rcText(scheme))
                Text("수락 대기 중")
                    .font(.rcMeta)
                    .foregroundStyle(Color.rcText2(scheme))
            }

            Spacer()

            Text("보냄")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Color.rcText3(scheme))
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(Color.rcCard2(scheme), in: Capsule())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.rcCard(scheme))
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }
}

// MARK: - 카카오 친구 찾기 후보 시트

private struct KakaoFriendSheet: View {
    @Environment(FriendsStore.self) private var friendsStore
    @Environment(\.colorScheme) private var scheme

    var body: some View {
        ZStack {
            Color.rcBg(scheme).ignoresSafeArea()
            ScrollView {
                VStack(spacing: 10) {
                    Text("카카오 친구 중 이 앱을 쓰는 친구예요")
                        .font(.system(size: 14))
                        .foregroundStyle(Color.rcText2(scheme))
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.top, 20)
                        .padding(.bottom, 4)

                    ForEach(friendsStore.kakaoCandidates) { c in
                        HStack(spacing: 12) {
                            Circle()
                                .fill(Color.rcCard2(scheme))
                                .frame(width: 40, height: 40)
                                .overlay(
                                    Text(String(c.nickname.prefix(1)))
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundStyle(Color.rcText(scheme))
                                )
                            VStack(alignment: .leading, spacing: 2) {
                                Text(c.nickname)
                                    .font(.system(size: 16, weight: .semibold))
                                    .foregroundStyle(Color.rcText(scheme))
                                Text("@\(c.handle)")
                                    .font(.rcMeta)
                                    .foregroundStyle(Color.rcText2(scheme))
                            }
                            Spacer()
                            if friendsStore.requestedHandles.contains(c.handle) {
                                Text("요청됨")
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundStyle(Color.rcText3(scheme))
                                    .padding(.horizontal, 14).padding(.vertical, 7)
                                    .background(Color.rcCard2(scheme), in: Capsule())
                            } else {
                                Button {
                                    Task { await friendsStore.requestKakaoFriend(c) }
                                } label: {
                                    Text("요청")
                                        .font(.system(size: 13, weight: .semibold))
                                        .foregroundStyle(Color.rcAccentText(scheme))
                                        .padding(.horizontal, 16).padding(.vertical, 7)
                                        .background(Color.rcAccent(scheme), in: Capsule())
                                }
                            }
                        }
                        .padding(.horizontal, 16).padding(.vertical, 12)
                        .background(Color.rcCard(scheme))
                        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    }
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 40)
            }
        }
        .presentationDragIndicator(.visible)
    }
}

// MARK: - Custom swipe-to-delete wrapper (인라인 확인 UI)

private struct SwipeToDeleteCard<Content: View>: View {
    let scheme: ColorScheme
    let onDelete: () -> Void
    @ViewBuilder let content: () -> Content

    @State private var dragOffset: CGFloat = 0
    @State private var isOpen = false
    @State private var isConfirming = false  // 확인 단계

    // 테마 색상: 라이트 = 차콜(#2C2C2E), 다크 = 미디엄 그레이(#636366)
    private var deleteColor: Color {
        scheme == .dark ? Color(hex: "636366") : Color(hex: "2C2C2E")
    }

    private let buttonWidth: CGFloat = 90
    private let confirmWidth: CGFloat = 180  // 확인 단계에서 넓어짐

    private var currentRevealWidth: CGFloat {
        isConfirming ? confirmWidth : buttonWidth
    }

    var body: some View {
        ZStack(alignment: .trailing) {
            // 뒤쪽 액션 영역
            Group {
                if isConfirming {
                    // 인라인 확인: [취소] [끊기]
                    HStack(spacing: 0) {
                        Button {
                            closeAll()
                        } label: {
                            Text("취소")
                                .font(.system(size: 14, weight: .semibold))
                                .foregroundStyle(scheme == .dark ? Color.white.opacity(0.7) : Color(hex: "2C2C2E"))
                                .frame(width: confirmWidth / 2)
                                .frame(maxHeight: .infinity)
                        }
                        .background(scheme == .dark ? Color(hex: "3A3A3C") : Color(hex: "D1D1D6"))

                        Button {
                            closeAll()
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) { onDelete() }
                        } label: {
                            VStack(spacing: 3) {
                                Image(systemName: "person.fill.xmark")
                                    .font(.system(size: 16, weight: .medium))
                                Text("끊기")
                                    .font(.system(size: 12, weight: .bold))
                            }
                            .foregroundStyle(.white)
                            .frame(width: confirmWidth / 2)
                            .frame(maxHeight: .infinity)
                        }
                        .background(deleteColor)
                    }
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                } else {
                    // 1단계: 친구 끊기 버튼
                    Button {
                        enterConfirm()
                    } label: {
                        VStack(spacing: 5) {
                            Image(systemName: "person.fill.xmark")
                                .font(.system(size: 20, weight: .medium))
                            Text("친구 끊기")
                                .font(.system(size: 12, weight: .semibold))
                        }
                        .foregroundStyle(.white)
                        .frame(width: buttonWidth)
                        .frame(maxHeight: .infinity)
                    }
                    .background(deleteColor)
                    .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
                    .opacity(-dragOffset > 8 ? 1 : 0)
                    .scaleEffect(x: min(1, -dragOffset / buttonWidth), anchor: .trailing)
                }
            }

            // 카드 (드래그 가능)
            content()
                .offset(x: dragOffset)
                .simultaneousGesture(
                    DragGesture(minimumDistance: 15, coordinateSpace: .local)
                        .onChanged { v in
                            guard !isConfirming else { return }
                            let isHorizontal = abs(v.translation.width) > abs(v.translation.height) * 1.2
                            guard isHorizontal else { return }
                            let base: CGFloat = isOpen ? -buttonWidth : 0
                            dragOffset = max(-buttonWidth, min(0, base + v.translation.width))
                        }
                        .onEnded { v in
                            guard !isConfirming else { return }
                            let isHorizontal = abs(v.translation.width) > abs(v.translation.height) * 1.2
                            guard isHorizontal else { return }
                            v.translation.width < -(buttonWidth * 0.4) ? open() : closeAll()
                        }
                )
                // 확인 단계에서 카드 탭 → 닫기
                .onTapGesture { if isConfirming { closeAll() } }
        }
        .clipped()
    }

    private func open() {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
            dragOffset = -buttonWidth
            isOpen = true
        }
    }

    private func enterConfirm() {
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
            isConfirming = true
            dragOffset = -confirmWidth
        }
    }

    private func closeAll() {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.75)) {
            dragOffset = 0
            isOpen = false
            isConfirming = false
        }
    }
}
