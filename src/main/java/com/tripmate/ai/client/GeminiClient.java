package com.tripmate.ai.client;

import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.List;
import java.util.Map;

/** Google Gemini (AI Studio free tier): generateContent with JSON MIME. */
@Component
public class GeminiClient implements AiClient {

    @Override
    public String name() {
        return "gemini";
    }

    @Override
    @SuppressWarnings("unchecked")
    public String complete(String systemPrompt, String userPrompt, String model, String key, int timeoutMs) {
        if (model == null || model.isBlank()) model = "gemini-2.0-flash";
        if (key == null || key.isBlank() || key.startsWith("REPLACE")) {
            throw new AiException(name(), 400, "missing key (ai.gemini.api.key)");
        }
        String url = "https://generativelanguage.googleapis.com/v1beta/models/"
                + model + ":generateContent?key=" + key;
        Map<String, Object> body = Map.of(
                "system_instruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", List.of(Map.of("parts", List.of(Map.of("text", userPrompt)))),
                "generationConfig", Map.of(
                        "responseMimeType", "application/json",
                        "temperature", 0.7,
                        "maxOutputTokens", 4096));
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        try {
            Map<String, Object> res = rest(timeoutMs)
                    .exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), Map.class)
                    .getBody();
            if (res == null) throw new AiException(name(), 502, "empty response");
            List<Object> candidates = (List<Object>) res.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                throw new AiException(name(), 502, "no candidates: " + clip(res.toString()));
            }
            Map<String, Object> content =
                    (Map<String, Object>) ((Map<String, Object>) candidates.get(0)).get("content");
            List<Object> parts = (List<Object>) content.get("parts");
            StringBuilder sb = new StringBuilder();
            for (Object p : parts) sb.append(((Map<String, Object>) p).get("text"));
            String text = sb.toString().trim();
            if (text.isEmpty()) throw new AiException(name(), 502, "empty text");
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
