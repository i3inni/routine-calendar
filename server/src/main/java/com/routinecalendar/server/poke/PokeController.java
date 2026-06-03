package com.routinecalendar.server.poke;

import com.routinecalendar.server.friend.FriendDtos.PokeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PokeController {

    private final PokeService pokeService;

    public PokeController(PokeService pokeService) {
        this.pokeService = pokeService;
    }

    /** 콕 찌르기 (친구만, 1시간 쿨다운) */
    @PostMapping("/pokes")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void poke(@AuthenticationPrincipal Long meId, @Valid @RequestBody PokeRequest request) {
        pokeService.poke(meId, request.toUserId());
    }
}
