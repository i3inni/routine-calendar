package com.routinecalendar.server.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** 카카오 GET /v1/api/talk/friends 응답 (앱 사용 친구만 반환됨) */
@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoFriendsResponse(
        @JsonProperty("total_count") int totalCount,
        List<Element> elements
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Element(
        Long id, // 친구의 카카오 회원번호 -> 우리 users.kakao_id 와 매칭
        @JsonProperty("profile_nickname") String profileNickname,
        @JsonProperty("profile_thumbnail_image") String profileThumbnail
    ){}
}