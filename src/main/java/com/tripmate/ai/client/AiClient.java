package com.tripmate.ai.client;

/** One AI backend (Gemini, Groq...). Keys stay server-side in app_config. */
public interface AiClient {
    /** Provider key, e.g. "gemini" / "groq". */
    String name();

    /**
     * Returns the model's raw text (expected: strict JSON).
     *
     * @throws AiException on transport / provider / rate-limit failures
     */
    String complete(String systemPrompt, String userPrompt, String model, String apiKey, int timeoutMs);
}
