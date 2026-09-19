package com.tripmate.ai;

import com.tripmate.ai.service.PlaceAiService;
import com.tripmate.ai.service.PlaceAiService.Quota;
import com.tripmate.common.dto.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips/{tripId}/places/ai")
@RequiredArgsConstructor
public class AiPlaceController {

    private final PlaceAiService service;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping("/quota")
    public ApiResponse<Map<String, Object>> quota(@PathVariable Long tripId) {
        Quota q = service.quota(tripId);
        return ApiResponse.ok(Map.of(
                "used", q.used(), "limit", q.limit(), "remaining", q.remaining()));
    }

    @PostMapping("/suggest")
    public ApiResponse<Map<String, Object>> suggest(
            @PathVariable Long tripId,
            @RequestBody(required = false) SuggestReq req) {
        return ApiResponse.ok("AI suggestions ready",
                service.suggest(tripId, me(),
                        req == null ? null : req.getCount(),
                        req == null ? null : req.getInterests(),
                        req == null ? null : req.getDayNo(),
                        req == null ? null : req.getFrom(),
                        req == null ? null : req.getTo()));
    }

    @GetMapping("/suggestions/{suggestionId}")
    public ApiResponse<Map<String, Object>> one(
            @PathVariable Long tripId, @PathVariable Long suggestionId) {
        return ApiResponse.ok(service.one(tripId, me(), suggestionId));
    }

    @PostMapping("/suggestions/{suggestionId}/add")
    public ApiResponse<Map<String, Object>> add(
            @PathVariable Long tripId,
            @PathVariable Long suggestionId,
            @RequestBody AddReq req) {
        return ApiResponse.ok("Places added",
                service.add(tripId, me(), suggestionId,
                        req == null ? null : req.getIndexes(),
                        req == null ? null : req.getDayNo()));
    }

    @Data
    public static class SuggestReq {
        private Integer count;
        private List<String> interests;
        private Integer dayNo;
        private String from;
        private String to;
    }

    @Data
    public static class AddReq {
        private List<Integer> indexes;
        private Integer dayNo;
    }
}
