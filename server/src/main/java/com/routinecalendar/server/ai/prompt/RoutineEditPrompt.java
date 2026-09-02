package com.routinecalendar.server.ai.prompt;

import java.time.LocalDate;

/** ④ 자연어 루틴 수정용 프롬프트 + Tool(Function) 정의. */
public final class RoutineEditPrompt {

    private RoutineEditPrompt() {}

    /** 오늘 날짜가 필요해 상수가 아니라 메서드. */
    public static String system(LocalDate today) {
        return """
            너는 루틴 앱 '같이해'의 수정 도우미다.
            사용자가 자연어로 루틴 수정을 요청한다. 다음 절차를 지켜라.

            1) 먼저 list_routines 를 호출해 현재 루틴 목록을 확인한다.
            2) 요청과 일치하는 루틴을 찾아 propose_routine_update 를 호출한다.
               - 바뀌는 필드만 새 값으로, 나머지는 기존 값을 그대로 모두 채운다.
            3) 어떤 루틴인지 모호하거나 일치하는 루틴이 없으면,
               도구를 부르지 말고 사용자에게 한국어로 짧게 되묻는다.

            [값 규칙]
            - 요일: 0=일 1=월 2=화 3=수 4=목 5=금 6=토. weekdays=월~금.
            - reminder: "HH:MM" 24시간제(예 "오후 8시"→"20:00"). 없으면 null.
            - type: check(하루 1번) / count(횟수 목표).

            오늘: %s, 타임존 Asia/Seoul.
            """.formatted(today);
    }

    /** OpenAI tools 배열(JSON 문자열). list_routines(읽기), propose_routine_update(제안). */
    public static final String TOOLS = """
        [
          {
            "type": "function",
            "function": {
              "name": "list_routines",
              "description": "사용자의 현재 루틴 목록(id, 이름, 설정)을 조회한다. 어떤 루틴을 어떻게 바꿀지 정하려면 반드시 먼저 호출한다.",
              "parameters": { "type": "object", "properties": {}, "additionalProperties": false }
            }
          },
          {
            "type": "function",
            "function": {
              "name": "propose_routine_update",
              "description": "루틴 1개를 새 설정으로 바꾸는 '제안'을 만든다. 실제 저장은 사용자 확인 후 이뤄진다. 바뀌지 않는 필드도 기존 값 그대로 모두 채운다.",
              "parameters": {
                "type": "object",
                "properties": {
                  "routineId":  { "type": "string", "description": "list_routines로 얻은 대상 루틴 id" },
                  "name":       { "type": "string" },
                  "type":       { "type": "string", "enum": ["check","count"] },
                  "target":     { "type": "integer" },
                  "unit":       { "type": "string" },
                  "repeatMode": { "type": "string", "enum": ["daily","weekdays","custom"] },
                  "repeatDays": { "type": "array", "items": { "type": "integer" } },
                  "reminder":   { "type": ["string","null"] },
                  "anytime":    { "type": "boolean" }
                },
                "required": ["routineId","name","type","target","unit","repeatMode","repeatDays","reminder","anytime"],
                "additionalProperties": false
              }
            }
          }
        ]
        """;
}
