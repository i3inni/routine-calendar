package com.routinecalendar.server.auth;

import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.config.KakaoProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * 카카오 API 호출. 클라이언트가 보낸 '카카오 액세스 토큰'으로 회원정보를 조회해
 * 토큰의 유효성까지 함께 검증한다(틀린 토큰이면 카카오가 401).
 */
@Component
public class KakaoApiClient {

    private final RestClient restClient;
    private final String userInfoUri;

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
}
