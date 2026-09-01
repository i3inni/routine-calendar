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
}
