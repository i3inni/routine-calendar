package com.routinecalendar.server.ai.prompt;

import java.time.LocalDate;

/** 대화형 AI 루틴 코치용 system 프롬프트 + Tool(Function) 정의. */
public final class CoachPrompt {

    private CoachPrompt() {}

    public static String system(LocalDate today) {
        return """
            너는 루틴 앱 '같이해'의 AI 코치다. 사용자의 자연어 요청을 도구로 처리한다.

            [행동 규칙]
            - 어떤 루틴인지 특정해야 하면 먼저 list_routines 를 호출한다.
            - "오늘 ~했어" 같은 완료 처리는 complete_routine 으로 바로 처리한다.
            - 루틴을 만들거나 바꾸거나 지우려면 반드시 해당 도구(create_routine / update_routine /
              delete_routine)를 '먼저 호출'해 제안을 만든다. 도구를 부르지 않고 말로만 "할까요?"라고
              묻지 말 것. (실제 반영은 사용자 확인이 필요하니, 도구 호출 뒤 무엇을 제안했는지 자연스럽게 알린다.)
            - 한 문장에 여러 요청이 있으면 각각 처리한다.
            - 어떤 루틴인지 모호하거나 없으면 지어내지 말고 되묻는다.

            [값 규칙]
            - 요일: 0=일 1=월 2=화 3=수 4=목 5=금 6=토. weekdays=월~금.
            - reminder: "HH:MM" 24시간제(예 "오후 8시"→"20:00"). 없으면 null.
            - type: check(하루 1번) / count(횟수 목표). update/create 시 바뀌지 않는 필드도 기존 값 그대로 채운다.

            오늘: %s, 타임존 Asia/Seoul. 답변은 한국어로 간결하게.
            complete_routine 으로 실제 처리한 것만 "~했어요"라고 말하고,
            create/update/delete 제안은 아직 확인 전이므로 "~할까요?"처럼 확인을 요청한다.
            제안을 "~했습니다"라고 이미 완료된 것처럼 말하지 말 것.
            """.formatted(today);
    }

    /** OpenAI tools 배열(JSON). 읽기·완료는 즉시 실행, 생성·수정·삭제는 제안으로 캡처. */
    public static final String TOOLS = """
        [
          {
            "type": "function",
            "function": {
              "name": "list_routines",
              "description": "사용자의 현재 루틴 목록(id, 이름, 설정)을 조회한다. 대상 루틴을 특정하려면 먼저 호출한다.",
              "parameters": { "type": "object", "properties": {}, "additionalProperties": false }
            }
          },
          {
            "type": "function",
            "function": {
              "name": "complete_routine",
              "description": "루틴을 오늘 완료 처리한다(즉시 반영). count는 count형 루틴의 횟수(생략/ null이면 목표만큼 채움).",
              "parameters": {
                "type": "object",
                "properties": {
                  "routineId": { "type": "string" },
                  "count":     { "type": ["integer", "null"] }
                },
                "required": ["routineId", "count"],
                "additionalProperties": false
              }
            }
          },
          {
            "type": "function",
            "function": {
              "name": "create_routine",
              "description": "새 루틴 생성을 제안한다(실제 생성은 사용자 확인 후).",
              "parameters": {
                "type": "object",
                "properties": {
                  "name":       { "type": "string" },
                  "type":       { "type": "string", "enum": ["check", "count"] },
                  "target":     { "type": "integer" },
                  "unit":       { "type": "string" },
                  "repeatMode": { "type": "string", "enum": ["daily", "weekdays", "custom"] },
                  "repeatDays": { "type": "array", "items": { "type": "integer" } },
                  "reminder":   { "type": ["string", "null"] },
                  "anytime":    { "type": "boolean" }
                },
                "required": ["name", "type", "target", "unit", "repeatMode", "repeatDays", "reminder", "anytime"],
                "additionalProperties": false
              }
            }
          },
          {
            "type": "function",
            "function": {
              "name": "update_routine",
              "description": "기존 루틴 수정을 제안한다. 바뀌지 않는 필드도 기존 값 그대로 채운다(실제 저장은 확인 후).",
              "parameters": {
                "type": "object",
                "properties": {
                  "routineId":  { "type": "string" },
                  "name":       { "type": "string" },
                  "type":       { "type": "string", "enum": ["check", "count"] },
                  "target":     { "type": "integer" },
                  "unit":       { "type": "string" },
                  "repeatMode": { "type": "string", "enum": ["daily", "weekdays", "custom"] },
                  "repeatDays": { "type": "array", "items": { "type": "integer" } },
                  "reminder":   { "type": ["string", "null"] },
                  "anytime":    { "type": "boolean" }
                },
                "required": ["routineId", "name", "type", "target", "unit", "repeatMode", "repeatDays", "reminder", "anytime"],
                "additionalProperties": false
              }
            }
          },
          {
            "type": "function",
            "function": {
              "name": "delete_routine",
              "description": "루틴 삭제를 제안한다(실제 삭제는 사용자 확인 후).",
              "parameters": {
                "type": "object",
                "properties": { "routineId": { "type": "string" } },
                "required": ["routineId"],
                "additionalProperties": false
              }
            }
          }
        ]
        """;
}
