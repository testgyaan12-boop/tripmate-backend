package com.tripmate.config.service;

import com.tripmate.config.repository.AppConfigRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central credential store. Secrets live in app_config (encrypted at rest in
 * future via CONFIG_MASTER_KEY); only JWT secret stays in env.
 * Only whitelisted non-secret keys are exposed via /api/config/public.
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private static final Set<String> PUBLIC_KEYS = Set.of(
            "free.trip.limit", "routing.provider", "map.provider",
            "map.tile.url", "map.attribution", "map.default.lat", "map.default.lng",
            "app.name", "app.env", "app.invite.base-url",
            "jwt.access.ttl.min", "jwt.refresh.ttl.days",
            "trip.max.members", "trip.max.places");

    private final AppConfigRepository repo;

    @Cacheable(value = "appConfig", key = "#key", unless = "#result == null")
    public String get(String key, String fallback) {
        return repo.findByConfigKey(key).map(c -> c.getConfigValue()).orElse(fallback);
    }

    public String get(String key) {
        return get(key, "");
    }

    public int getInt(String key, int fallback) {
        try {
            return Integer.parseInt(get(key, String.valueOf(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public Map<String, String> publicMap() {
        Map<String, String> out = new LinkedHashMap<>();
        for (String k : PUBLIC_KEYS) {
            out.put(k, get(k, ""));
        }
        return out;
    }

    @CacheEvict(value = "appConfig", allEntries = true)
    public void evictAll() {
    }
}
