package com.routinecalendar.server.friend.domain;

/** 친구 요청이 수락됨. 요청을 보냈던 사람(toUserId)에게 푸시로 알린다. */
public record FriendRequestAcceptedEvent(Long toUserId, String accepterNickname) {
}
