package com.routinecalendar.server.ai.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/** OpenAI chat.completions 응답 중 우리가 쓰는 부분만 매핑 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OpenAiResponse(List<Choice> choices) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(Message message) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(String content) {}
}
