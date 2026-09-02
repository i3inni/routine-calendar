package com.routinecalendar.server.ai.client;

/** LLM이 요청한 도구를 실제 실행하는 콜백. 구현은 Service가 한다(도메인 접근). */
@FunctionalInterface
public interface ToolExecutor {
    /**
     * @param toolName      호출된 도구 이름 (list_routines / propose_routine_update)
     * @param argumentsJson 도구 인자(JSON 문자열)
     * @return 도구 실행 결과(JSON 문자열) — LLM에게 tool 메시지로 되돌려줌
     */
    String execute(String toolName, String argumentsJson);
}
