package com.gridveritas.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin client for a local Ollama server. Used only for natural-language audit
 * assistance (RAG over retrieved facts) — never for per-event anomaly scoring
 * (ADR-004). Fails soft: isAvailable() lets callers degrade gracefully.
 */
@Component
public class OllamaClient {

    private final boolean enabled;
    private final String baseUrl;
    private final String model;
    private final double temperature;
    private final long timeoutMs;
    private final HttpClient http;
    private final ObjectMapper mapper;

    public OllamaClient(@Value("${gridveritas.ai.ollama.enabled:true}") boolean enabled,
                        @Value("${gridveritas.ai.ollama.url:http://localhost:11434}") String baseUrl,
                        @Value("${gridveritas.ai.ollama.model:llama3.2:1b}") String model,
                        @Value("${gridveritas.ai.ollama.temperature:0.2}") double temperature,
                        @Value("${gridveritas.ai.ollama.timeout-ms:60000}") long timeoutMs,
                        ObjectMapper mapper) {
        this.enabled = enabled;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.temperature = temperature;
        this.timeoutMs = timeoutMs;
        this.mapper = mapper;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getModel() {
        return model;
    }

    /** Quick reachability check (server up and responding). */
    public boolean isAvailable() {
        if (!enabled) {
            return false;
        }
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /** Single-turn chat with a system (grounding) and user (question) message. */
    public String chat(String system, String user) throws Exception {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", model);
        root.put("stream", false);
        ArrayNode messages = root.putArray("messages");
        messages.add(mapper.createObjectNode().put("role", "system").put("content", system));
        messages.add(mapper.createObjectNode().put("role", "user").put("content", user));
        root.putObject("options").put("temperature", temperature);

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofMillis(timeoutMs))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(root)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Ollama HTTP " + resp.statusCode() + ": " + resp.body());
        }
        JsonNode node = mapper.readTree(resp.body());
        return node.path("message").path("content").asText("").trim();
    }
}
