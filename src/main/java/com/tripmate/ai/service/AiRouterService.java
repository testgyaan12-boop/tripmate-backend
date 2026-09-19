package com.tripmate.ai.service;

import com.tripmate.ai.client.AiClient;
import com.tripmate.ai.client.AiException;
import com.tripmate.ai.client.GeminiClient;
import com.tripmate.ai.client.GroqClient;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.config.service.ConfigService;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Tries the primary provider, falls back to the secondary on any failure. */
@Service
@RequiredArgsConstructor
public class AiRouterService {

    private final ConfigService config;
    private final GeminiClient gemini;
    private final GroqClient groq;

    @Getter
    public static class AiResult {
        private final String text;
        private final String provider;
        private final String model;

        public AiResult(String text, String provider, String model) {
            this.text = text;
            this.provider = provider;
            this.model = model;
        }
    }

    public AiResult generate(String systemPrompt, String userPrompt) {
        String primary = config.get("ai.provider", "gemini").trim().toLowerCase();
        String fallback = config.get("ai.fallback.provider", "groq").trim().toLowerCase();
        int timeout = config.getInt("ai.timeout.ms", 60000);
        AiException firstError = null;
        for (String name : new String[]{primary, fallback}) {
            AiClient client = resolve(name);
            if (client == null) continue;
            String model = config.get("ai." + name + ".model", "");
            String key = config.get("ai." + name + ".api.key", "");
            try {
                String text = client.complete(systemPrompt, userPrompt, model, key, timeout);
                return new AiResult(text, name, model);
            } catch (AiException e) {
                if (firstError == null) firstError = e;
            }
        }
        String why = firstError == null ? "no provider configured"
                : firstError.getProvider() + ": " + firstError.getMessage();
        throw new BadRequestException(
                "AI service is busy right now (" + why + "). Try Quick plan or retry in a minute.");
    }

    private AiClient resolve(String name) {
        if ("gemini".equals(name)) return gemini;
        if ("groq".equals(name)) return groq;
        return null;
    }
}
