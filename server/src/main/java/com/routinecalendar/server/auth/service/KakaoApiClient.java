package com.routinecalendar.server.auth.service;

import com.routinecalendar.server.auth.dto.KakaoUserResponse;
import com.routinecalendar.server.auth.dto.KakaoFriendsResponse;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.config.KakaoProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;

/**
 * 카카오 API 호출. 클라이언트가 보낸 '카카오 액세스 토큰'으로 회원정보를 조회해
 * 토큰의 유효성까지 함께 검증한다(틀린 토큰이면 카카오가 401).
 */
@Component
public class KakaoApiClient {

    private final RestClient restClient;
    private final String userInfoUri;
    private static final String FRIENDS_URI = "https://kapi.kakao.com/v1/api/talk/friends";

    public KakaoApiClient(KakaoProperties props) {
        this.restClient = RestClient.create();
        this.userInfoUri = props.userInfoUri();
    }

    public KakaoUserResponse fetchUser(String kakaoAccessToken) {
        return restClient.get()
                .uri(userInfoUri)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, res) -> {
                    throw new BusinessException(ErrorCode.INVALID_KAKAO_TOKEN);
                })
                .body(KakaoUserResponse.class);
    }

    /** 내 카톡 친구 중 '이 앱 사용자'의 카카오 회원번호 목록 */
    public List<Long> fetchFriendKakaoIds(String kakaoAccessToken) {
        KakaoFriendsResponse res = restClient.get()
                .uri(FRIENDS_URI)   // 필요시 ?limit=100 페이지네이션
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + kakaoAccessToken)
                .retrieve()
                .onStatus(status -> status.value() == 401, (req, r) -> {
                    throw new BusinessException(ErrorCode.INVALID_KAKAO_TOKEN);
                })
                .onStatus(status -> status.value() == 403, (req, r) -> {
                    throw new BusinessException(ErrorCode.KAKAO_FRIENDS_CONSENT_REQUIRED);
                })
                .body(KakaoFriendsResponse.class);
        if (res == null || res.elements() == null) return List.of();
        return res.elements().stream().map(KakaoFriendsResponse.Element::id).toList();
    }
}
