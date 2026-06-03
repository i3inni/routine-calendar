package com.routinecalendar.server.friend;

import com.routinecalendar.server.friend.FriendDtos.FriendRequestResponse;
import com.routinecalendar.server.friend.FriendDtos.FriendResponse;
import com.routinecalendar.server.friend.FriendDtos.SendFriendRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FriendController {

    private final FriendService friendService;

    public FriendController(FriendService friendService) {
        this.friendService = friendService;
    }

    /** 친구 목록 (각자의 오늘 요약 포함) */
    @GetMapping("/me/friends")
    public List<FriendResponse> listFriends(@AuthenticationPrincipal Long meId) {
        return friendService.listFriends(meId);
    }

    /** 친구 요청 보내기 (상대 handle) */
    @PostMapping("/friend-requests")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void sendRequest(@AuthenticationPrincipal Long meId,
                            @Valid @RequestBody SendFriendRequest request) {
        friendService.sendRequest(meId, request.handle());
    }

    /** 내가 받은 친구 요청 목록 */
    @GetMapping("/me/friend-requests")
    public List<FriendRequestResponse> listIncoming(@AuthenticationPrincipal Long meId) {
        return friendService.listIncomingRequests(meId);
    }

    /** 친구 요청 수락 */
    @PostMapping("/friend-requests/{id}/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(@AuthenticationPrincipal Long meId, @PathVariable Long id) {
        friendService.acceptRequest(meId, id);
    }

    /** 친구 요청 거절 */
    @PostMapping("/friend-requests/{id}/decline")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void decline(@AuthenticationPrincipal Long meId, @PathVariable Long id) {
        friendService.declineRequest(meId, id);
    }

    /** 친구 끊기 (멱등) */
    @DeleteMapping("/me/friends/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeFriend(@AuthenticationPrincipal Long meId, @PathVariable Long userId) {
        friendService.removeFriend(meId, userId);
    }
}
