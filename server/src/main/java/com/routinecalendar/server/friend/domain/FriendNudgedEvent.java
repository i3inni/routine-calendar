package com.routinecalendar.server.friend.domain;

/** 친구 자극하기, 푸시 리스너가 커밋 후 비동기로 처리한다. */
public record FriendNudgedEvent(Long toUserId, String fromNickname, String message){
    
}