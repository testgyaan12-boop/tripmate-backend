package com.tripmate.ai.client;

/** Failure calling an AI provider (status + snippet for logs/messages). */
public class AiException extends RuntimeException {
    private final String provider;
    private final int status;

    public AiException(String provider, int status, String message) {
        super(message);
        this.provider = provider;
        this.status = status;
    }

    public String getProvider() {
        return provider;
    }

    public int getStatus() {
        return status;
    }
}
