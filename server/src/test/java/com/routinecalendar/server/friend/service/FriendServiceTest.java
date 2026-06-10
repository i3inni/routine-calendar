package com.routinecalendar.server.friend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.friend.domain.FriendNudgedEvent;
import com.routinecalendar.server.friend.domain.Poke;
import com.routinecalendar.server.friend.repository.FriendRequestRepository;
import com.routinecalendar.server.friend.repository.FriendshipRepository;
import com.routinecalendar.server.friend.repository.PokeRepository;
import com.routinecalendar.server.summary.repository.DailySummaryRepository;
import com.routinecalendar.server.user.domain.User;
import com.routinecalendar.server.user.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class FriendServiceTest {

    @Mock UserRepository userRepository;
    @Mock FriendshipRepository friendshipRepository;
    @Mock FriendRequestRepository friendRequestRepository;
    @Mock DailySummaryRepository dailySummaryRepository;
    @Mock PokeRepository pokeRepository;
    @Mock ApplicationEventPublisher eventPublisher;

    FriendService friendService;

    @Mock User me;
    @Mock User friend;

    @BeforeEach
    void setUp() {
        friendService = new FriendService(userRepository, friendshipRepository,
                friendRequestRepository, dailySummaryRepository, pokeRepository, eventPublisher);
    }

    @Test
    void 친구를_자극하면_기록을_남기고_이벤트가_발행된다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
        when(friendshipRepository.existsBetween(me, friend)).thenReturn(true);
        when(pokeRepository.countByFromUserAndToUserAndCreatedAtAfter(eq(me), eq(friend), any(Instant.class)))
                .thenReturn(0L);   // 쿨다운 한도 미만
        when(me.getNickname()).thenReturn("철수");
        when(friend.getId()).thenReturn(2L);

        friendService.nudge(1L, 2L, "어서 루틴 해!");

        verify(pokeRepository).save(any(Poke.class));
        ArgumentCaptor<FriendNudgedEvent> captor = ArgumentCaptor.forClass(FriendNudgedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        FriendNudgedEvent event = captor.getValue();
        assertThat(event.toUserId()).isEqualTo(2L);
        assertThat(event.fromNickname()).isEqualTo("철수");
        assertThat(event.message()).isEqualTo("어서 루틴 해!");
    }

    @Test
    void 친구가_아니면_자극할_수_없고_이벤트도_발행되지_않는다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
        when(friendshipRepository.existsBetween(me, friend)).thenReturn(false);

        assertThatThrownBy(() -> friendService.nudge(1L, 2L, "어서 루틴 해!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NOT_FRIEND);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void 쿨다운_한도를_넘으면_자극할_수_없다() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(me));
        when(userRepository.findById(2L)).thenReturn(Optional.of(friend));
        when(friendshipRepository.existsBetween(me, friend)).thenReturn(true);
        when(pokeRepository.countByFromUserAndToUserAndCreatedAtAfter(eq(me), eq(friend), any(Instant.class)))
                .thenReturn(2L);   // 이미 한도(2회) 도달

        assertThatThrownBy(() -> friendService.nudge(1L, 2L, "또 보낸다"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.NUDGE_COOLDOWN);

        verify(pokeRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }
}
