package com.routinecalendar.server.friend;

/** 새 친구 요청 발생. 푸시 리스너가 커밋 후 비동기로 처리한다. */
public record FriendRequestedEvent(Long toUserId, String fromNickname) {
}
