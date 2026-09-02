package com.routinecalendar.server.ai.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routinecalendar.server.common.error.BusinessException;
import com.routinecalendar.server.common.error.ErrorCode;
import com.routinecalendar.server.config.AiProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import java.util.List;
import java.util.Map;

/** OpenAI Chat Completions로 Structured Output(JSON)을 받아온다. */
@Component
public class OpenAiClient implements LlmClient {

    private final RestClient restClient;
    private final String apiKey;
    private final String model;
    private final ObjectMapper objectMapper;

    public OpenAiClient(AiProperties props, ObjectMapper objectMapper) {
        this.restClient = RestClient.builder().baseUrl(props.baseUrl()).build();
        this.apiKey = props.apiKey();
        this.model = props.model();
        this.objectMapper = objectMapper;
    }

    @Override
    public String completeJson(String systemPrompt, String userMessage, String jsonSchema) {
        JsonNode schemaNode = parseSchema(jsonSchema);

        // curl의 Body를 그대로 자바 Map으로 조립 → Jackson이 JSON으로 직렬화
        Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userMessage)
                ),
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", "structured_output",
                                "strict", true,
                                "schema", schemaNode
                        )
                ),
                "temperature", 0.2
        );

        OpenAiResponse res = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .body(body)
                .retrieve()
                .onStatus(status -> status.isError(), (req, r) -> {
                    throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
                })
                .body(OpenAiResponse.class);

        if (res == null || res.choices() == null || res.choices().isEmpty()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }
        return res.choices().get(0).message().content();
    }

    private JsonNode parseSchema(String jsonSchema) {
        try {
            return objectMapper.readTree(jsonSchema);
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }
    }
}
