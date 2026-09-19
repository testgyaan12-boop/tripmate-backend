package com.tripmate.config;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.config.entity.AppConfig;
import com.tripmate.config.repository.AppConfigRepository;
import com.tripmate.config.service.ConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService config;
    private final AppConfigRepository repo;

    @GetMapping("/api/config/public")
    public ApiResponse<Map<String, String>> publicConfig() {
        return ApiResponse.ok(config.publicMap());
    }

    @GetMapping("/api/admin/config")
    public ApiResponse<List<Map<String, Object>>> all() {
        List<Map<String, Object>> out = repo.findAll().stream().map(c -> Map.<String, Object>of(
                "key", c.getConfigKey(),
                "value", Boolean.TRUE.equals(c.getSecret()) ? "****" : c.getConfigValue(),
                "category", c.getCategory(),
                "secret", c.getSecret(),
                "description", c.getDescription() == null ? "" : c.getDescription())).toList();
        return ApiResponse.ok(out);
    }

    @PutMapping("/api/admin/config/{key}")
    public ApiResponse<?> update(@PathVariable String key, @RequestBody UpdateReq req) {
        AppConfig c = repo.findByConfigKey(key)
                .orElseThrow(() -> new com.tripmate.common.exception.ResourceNotFoundException("Config not found: " + key));
        c.setConfigValue(req.getValue());
        repo.save(c);
        config.evictAll();
        return ApiResponse.ok("Updated", null);
    }

    @PostMapping("/api/admin/config/reload")
    public ApiResponse<?> reload() {
        config.evictAll();
        return ApiResponse.ok("Cache cleared", null);
    }

    @Data
    public static class UpdateReq {
        private String value;
    }
}
