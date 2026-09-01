package com.routinecalendar.server.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinecalendar.server.ai.client.LlmClient;
import com.routinecalendar.server.ai.dto.AiDtos.RoutineDraftResponse;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiRoutineServiceTest {

    @Mock LlmClient llmClient;

    AiRoutineService service;

    @BeforeEach
    void setUp() {
        // ObjectMapper는 실제 파싱 로직을 검증해야 하므로 진짜 객체를 쓴다(mock 아님).
        service = new AiRoutineService(llmClient, new ObjectMapper());
    }

    @Test
    void 자연어를_파싱해_루틴_초안으로_매핑한다() {
        // LLM이 스키마대로 준 JSON '문자열'을 흉내낸다(실제 호출 X → 결정적·무료).
        when(llmClient.completeJson(anyString(), anyString(), anyString()))
                .thenReturn("""
                    {"name":"운동","type":"check","target":1,"unit":"",
                     "repeatMode":"custom","repeatDays":[1,3,5],
                     "reminder":"20:00","anytime":false,
                     "warnings":["저녁 8시를 20:00으로 해석"]}
                    """);

        RoutineDraftResponse res = service.draftFromText(1L, "월수금 저녁 8시 운동");

        assertThat(res.draft().name()).isEqualTo("운동");
        assertThat(res.draft().type()).isEqualTo("check");
        assertThat(res.draft().target()).isEqualTo(1);
        assertThat(res.draft().repeatMode()).isEqualTo("custom");
        assertThat(res.draft().repeatDays()).containsExactly(1, 3, 5);
        assertThat(res.draft().reminder()).isEqualTo("20:00");
        // 초안 단계: id/createdAt/endDate는 비어있어야 한다(사용자 확인 단계에서 채움).
        assertThat(res.draft().id()).isNull();
        assertThat(res.draft().createdAt()).isNull();
        assertThat(res.draft().endDate()).isNull();
        assertThat(res.warnings()).containsExactly("저녁 8시를 20:00으로 해석");
    }

    @Test
    void LLM이_잘못된_JSON을_주면_AI_PROVIDER_ERROR로_감싼다() {
        when(llmClient.completeJson(anyString(), anyString(), anyString()))
                .thenReturn("이건 JSON이 아님");

        assertThatThrownBy(() -> service.draftFromText(1L, "아무거나"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.AI_PROVIDER_ERROR);
    }
}
