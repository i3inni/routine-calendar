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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** OpenAI Chat Completions로 Structured Output(JSON)·Tool Calling을 처리한다. */
@Component
public class OpenAiClient implements LlmClient {

    // 도구 루프가 무한히 돌지 않도록 하는 상한(안전장치).
    private static final int MAX_TOOL_ITERATIONS = 5;

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

    @Override
    public String runToolLoop(List<Map<String, Object>> seedMessages, String toolsJson, ToolExecutor executor) {
        JsonNode toolsNode = parseSchema(toolsJson);   // 도구 정의도 결국 JSON → readTree 재사용

        // 시작 메시지(system+히스토리+새 입력)를 복사해 시작. 이후 도구 호출/결과(JsonNode)가 섞여 쌓인다.
        List<Object> messages = new ArrayList<>(seedMessages);

        for (int iter = 0; iter < MAX_TOOL_ITERATIONS; iter++) {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "messages", messages,
                    "tools", toolsNode,
                    "tool_choice", "auto"
            );

            String raw = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(body)
                    .retrieve()
                    .onStatus(status -> status.isError(), (req, r) -> {
                        throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
                    })
                    .body(String.class);

            JsonNode message = readMessage(raw);
            JsonNode toolCalls = message.path("tool_calls");

            // 도구를 더 안 부르면 → 최종 텍스트 답변 → 루프 종료
            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                return message.path("content").asText();
            }

            // 도구 호출 요청됨: assistant 메시지(그대로)를 넣고, 각 도구를 실행해 결과를 tool 메시지로 추가
            messages.add(message);
            for (JsonNode call : toolCalls) {
                String id = call.path("id").asText();
                String name = call.path("function").path("name").asText();
                String args = call.path("function").path("arguments").asText();
                String result = executor.execute(name, args);
                messages.add(Map.of("role", "tool", "tool_call_id", id, "content", result));
            }
        }
        // 상한까지 돌았는데 결론이 안 남 → 실패 처리
        throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
    }

    /** 응답 raw JSON에서 choices[0].message 노드를 꺼낸다. */
    private JsonNode readMessage(String rawResponse) {
        try {
            JsonNode choices = objectMapper.readTree(rawResponse).path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
            }
            return choices.get(0).path("message");
        } catch (JsonProcessingException e) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }
    }
}
