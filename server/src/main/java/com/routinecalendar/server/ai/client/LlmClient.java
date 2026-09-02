package com.routinecalendar.server.ai.client;

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
     * Tool(Function) Calling 루프. LLM이 도구 호출을 요청하면 executor로 실행하고
     * 결과를 되먹여 재요청한다. LLM이 더 이상 도구를 안 부르고 낸 최종 텍스트를 반환.
     *
     * @param systemPrompt 규칙
     * @param userMessage  사용자 요청
     * @param toolsJson    도구 정의(JSON 배열 문자열)
     * @param executor     도구 실제 실행 콜백
     * @return LLM의 최종 텍스트 응답
     */
    String runToolLoop(String systemPrompt, String userMessage, String toolsJson, ToolExecutor executor);
}
