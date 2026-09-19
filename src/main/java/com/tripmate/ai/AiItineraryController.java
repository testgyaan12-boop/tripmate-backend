package com.tripmate.ai;

import com.tripmate.ai.service.ItineraryAiService;
import com.tripmate.ai.service.ItineraryAiService.Quota;
import com.tripmate.common.dto.ApiResponse;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips/{tripId}/itinerary/ai")
@RequiredArgsConstructor
public class AiItineraryController {

    private final ItineraryAiService service;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping("/quota")
    public ApiResponse<Map<String, Object>> quota(@PathVariable Long tripId) {
        Quota q = service.quota(tripId);
        return ApiResponse.ok(Map.of(
                "used", q.used(), "limit", q.limit(), "remaining", q.remaining()));
    }

    @PostMapping("/propose")
    public ApiResponse<Map<String, Object>> propose(
            @PathVariable Long tripId,
            @RequestBody(required = false) Map<String, Object> answers) {
        return ApiResponse.ok("AI proposal ready",
                service.propose(tripId, me(), answers));
    }

    @GetMapping("/proposals/latest")
    public ApiResponse<Map<String, Object>> latest(@PathVariable Long tripId) {
        Map<String, Object> p = service.latest(tripId, me());
        return p == null ? ApiResponse.ok("No draft", null) : ApiResponse.ok(p);
    }

    @GetMapping("/proposals/{proposalId}")
    public ApiResponse<Map<String, Object>> one(
            @PathVariable Long tripId, @PathVariable Long proposalId) {
        return ApiResponse.ok(service.one(tripId, me(), proposalId));
    }

    @PutMapping("/proposals/{proposalId}/confirm")
    public ApiResponse<Map<String, Object>> confirm(
            @PathVariable Long tripId,
            @PathVariable Long proposalId,
            @RequestBody ConfirmReq req) {
        return ApiResponse.ok("Itinerary saved",
                service.confirm(tripId, me(), proposalId, req.getDays()));
    }

    @Data
    public static class ConfirmReq {
        private List<Map<String, Object>> days;
    }
}
