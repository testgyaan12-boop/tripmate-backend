package com.tripmate.ai.client;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.List;
import java.util.Map;

/** Groq (OpenAI-compatible /chat/completions, json_object mode). */
@Component
public class GroqClient implements AiClient {

    @Override
    public String name() {
        return "groq";
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String systemPrompt, String userPrompt, String model, String key, int timeoutMs) {
        if (model == null || model.isBlank()) model = "llama-3.3-70b-versatile";
        if (key == null || key.isBlank() || key.startsWith("REPLACE")) {
            throw new AiException(name(), 400, "missing key (ai.groq.api.key)");
        }
        String url = "https://api.groq.com/openai/v1/chat/completions";
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.7,
                "max_tokens", 4096,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.setBearerAuth(key);
        try {
            Map<String, Object> res = rest(timeoutMs)
                    .exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), Map.class)
                    .getBody();
            if (res == null) throw new AiException(name(), 502, "empty response");
            List<Object> choices = (List<Object>) res.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new AiException(name(), 502, "no choices: " + clip(res.toString()));
            }
            Map<String, Object> message =
                    (Map<String, Object>) ((Map<String, Object>) choices.get(0)).get("message");
            String text = String.valueOf(message.get("content")).trim();
            if (text.isEmpty() || "null".equals(text)) {
                throw new AiException(name(), 502, "empty text");
            }
            return text;
        } catch (HttpStatusCodeException e) {
            throw new AiException(name(), e.getStatusCode().value(),
                    "HTTP " + e.getStatusCode().value() + ": " + clip(e.getResponseBodyAsString()));
        } catch (AiException e) {
            throw e;
        } catch (Exception e) {
            throw new AiException(name(), 0, e.getClass().getSimpleName() + ": " + clip(e.getMessage()));
        }
    }

    private static RestTemplate rest(int timeoutMs) {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(timeoutMs);
        f.setReadTimeout(timeoutMs);
        return new RestTemplate(f);
    }

    private static String clip(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) : s;
    }
}
