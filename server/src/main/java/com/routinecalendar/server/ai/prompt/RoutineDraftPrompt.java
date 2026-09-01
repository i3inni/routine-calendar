package com.routinecalendar.server.ai.prompt;

import java.time.LocalDate;

/** ① AI 루틴 초안 생성용 프롬프트/스키마 모음. */
public final class RoutineDraftPrompt {

    private RoutineDraftPrompt() {}

    /** 오늘 날짜가 필요해서 상수가 아니라 메서드(런타임 주입). */
    public static String system(LocalDate today) {
        return """
            너는 루틴 앱 '같이해'의 자연어 파서다.
            사용자의 한국어 문장을 아래 규칙에 맞는 JSON으로만 변환한다. 설명 문장은 쓰지 않는다.

            [필드 규칙]
            - name: 루틴 이름(간결하게, 예 "운동", "독서").
            - type: "check"(하루 1번 완료형) 또는 "count"(횟수 목표형). 애매하면 "check".
            - target: check면 1. count면 목표 횟수(예 "물 8잔"→8).
            - unit: count의 단위(예 "잔","쪽"). 없으면 "".
            - repeatMode: "daily"(매일) | "weekdays"(월~금) | "custom"(특정 요일).
            - repeatDays: custom일 때만 채운다. 0=일 1=월 2=화 3=수 4=목 5=금 6=토.
              daily/weekdays면 빈 배열 [].
            - reminder: 알림 시각 "HH:MM" 24시간제(예 "저녁 8시"→"20:00"). 시간 언급 없으면 null.
            - anytime: 시간 지정 없이 아무 때나 하는 루틴이면 true, 아니면 false.
            - warnings: 문장에 없어서 '추정'한 값이 있으면 한국어로 짧게 남긴다(예 "저녁 8시를 20:00으로 해석"). 없으면 [].

            [요일 해석]
            - "주말"은 custom + repeatDays [0,6](일=0, 토=6). "평일"/"주중"은 weekdays.
            - "일주일에 N번"처럼 반복 횟수만 있고 어떤 요일인지 정해지지 않았으면
              요일을 임의로 지어내지 말 것. repeatMode는 daily, repeatDays []로 두고
              warnings에 "요일 미지정: 원하는 요일을 직접 골라주세요"를 넣는다.

            [기준]
            - 오늘 날짜: %s, 타임존 Asia/Seoul.
            - 확실하지 않으면 지어내지 말고 warnings로 알린다.
            """.formatted(today);
    }

    /** Structured Output 강제용 JSON 스키마(문자열). ParsedRoutine과 필드가 일치해야 한다. */
    public static final String SCHEMA = """
        {
          "type": "object",
          "properties": {
            "name":       { "type": "string" },
            "type":       { "type": "string", "enum": ["check","count"] },
            "target":     { "type": "integer" },
            "unit":       { "type": "string" },
            "repeatMode": { "type": "string", "enum": ["daily","weekdays","custom"] },
            "repeatDays": { "type": "array", "items": { "type": "integer" } },
            "reminder":   { "type": ["string","null"] },
            "anytime":    { "type": "boolean" },
            "warnings":   { "type": "array", "items": { "type": "string" } }
          },
          "required": ["name","type","target","unit","repeatMode","repeatDays","reminder","anytime","warnings"],
          "additionalProperties": false
        }
        """;
}
