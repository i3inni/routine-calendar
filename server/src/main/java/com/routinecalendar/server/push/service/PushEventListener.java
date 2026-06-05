package com.routinecalendar.server.push.service;

import com.routinecalendar.server.friend.domain.FriendRequestedEvent;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 도메인 이벤트 → 푸시.
 * {@code AFTER_COMMIT} 이라 트랜잭션이 성공적으로 커밋된 뒤에만 발송하고,
 * {@code @Async} 라 요청 스레드를 막지 않는다. (푸시 실패가 본 트랜잭션에 영향 X)
 */
@Component
public class PushEventListener {

    private final PushService pushService;

    public PushEventListener(PushService pushService) {
        this.pushService = pushService;
    }

    @Async
    @TransactionalEventListener
    public void onFriendRequested(FriendRequestedEvent event) {
        pushService.sendToUser(event.toUserId(),
                "새 친구 요청", event.fromNickname() + "님이 친구 요청을 보냈어요");
    }
}
