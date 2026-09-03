package com.routinecalendar.server.ai.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * LLM 호출 추상화. 구현체(OpenAI/Gemini/Claude)를 갈아끼워도
 * 부르는 쪽 코드는 이 인터페이스만 알면 된다.
 */
public interface LlmClient {

    /**
     * system 지시 + user 입력을 보내고, 주어진 JSON 스키마로 강제된 결과(JSON 문자열)를 받는다.
     *
     * @param systemPrompt 모델 규칙(역할·출력형식)
     * @param userMessage  사용자 자연어 입력
     * @param jsonSchema   출력 강제용 JSON 스키마(문자열)
     * @return choices[0].message.content — 스키마를 따르는 JSON '문자열'
     */
    String completeJson(String systemPrompt, String userMessage, String jsonSchema);

    /**
     * Tool(Function) Calling 루프(기본형). seedMessages(system + 과거 대화 + 새 입력)로 시작해,
     * LLM이 도구 호출을 요청하면 executor로 실행하고 결과를 되먹여 재요청한다.
     * 더 이상 도구를 안 부르고 낸 최종 텍스트를 반환. (대화형 코치는 히스토리를 여기 넣는다)
     *
     * @param seedMessages 시작 메시지들(각 {role, content}). 이 뒤로 도구 호출/결과가 쌓인다.
     * @param toolsJson    도구 정의(JSON 배열 문자열)
     * @param executor     도구 실제 실행 콜백
     * @return LLM의 최종 텍스트 응답
     */
    String runToolLoop(List<Map<String, Object>> seedMessages, String toolsJson, ToolExecutor executor);

    /** system + 단일 user 메시지로 시작하는 간편 버전(④ 등 단발 요청용). 위 기본형에 위임. */
    default String runToolLoop(String systemPrompt, String userMessage, String toolsJson, ToolExecutor executor) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        messages.add(Map.of("role", "user", "content", userMessage));
        return runToolLoop(messages, toolsJson, executor);
    }
}
