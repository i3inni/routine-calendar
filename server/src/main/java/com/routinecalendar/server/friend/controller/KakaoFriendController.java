package com.routinecalendar.server.friend.controller;

import com.routinecalendar.server.friend.dto.FriendDtos.KakaoFriendCandidate;
import com.routinecalendar.server.friend.dto.FriendDtos.KakaoTokenRequest;
import com.routinecalendar.server.friend.service.KakaoFriendService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class KakaoFriendController {

    private final KakaoFriendService kakaoFriendService;

    public KakaoFriendController(KakaoFriendService kakaoFriendService) {
        this.kakaoFriendService = kakaoFriendService;
    }

    /** 카카오 친구 중 앱 사용자 찾기 (+ 내 카카오 연동). 친구 요청은 기존 POST /friend-requests 재사용 */
    @PostMapping("/me/kakao/friends")
    public List<KakaoFriendCandidate> kakaoFriends(
            @AuthenticationPrincipal Long meId,
            @Valid @RequestBody KakaoTokenRequest request) {
        return kakaoFriendService.findAppFriends(meId, request.kakaoAccessToken());
    }
}
