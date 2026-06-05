package com.routinecalendar.server.poke.domain;

/** 콕 찌르기 발생. 푸시 리스너가 커밋 후 비동기로 처리한다. */
public record PokeEvent(Long toUserId, String fromNickname) {
}
