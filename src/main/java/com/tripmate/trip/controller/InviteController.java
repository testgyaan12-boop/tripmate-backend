package com.tripmate.trip.controller;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.trip.service.InviteService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class InviteController {

    private final InviteService inviteService;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    /** Connected users (shared trip history) — for invite suggestions. */
    @GetMapping("/api/users/connections")
    public ApiResponse<List<Map<String, Object>>> connections() {
        return ApiResponse.ok(inviteService.getConnections(me()));
    }

    /** Send an in-app invite to a connected user for a specific trip. */
    @PostMapping("/api/trips/{id}/invite-user")
    public ApiResponse<?> sendInvite(@PathVariable Long id, @RequestBody InviteReq req) {
        inviteService.sendInvite(id, me(), req.getUserId());
        return ApiResponse.ok("Invite sent", null);
    }

    /** Pending invites I've received. */
    @GetMapping("/api/users/invites/pending")
    public ApiResponse<List<Map<String, Object>>> pending() {
        return ApiResponse.ok(inviteService.pendingInvites(me()));
    }

    /** Accept a trip invite. */
    @PostMapping("/api/users/invites/{inviteId}/accept")
    public ApiResponse<?> accept(@PathVariable Long inviteId) {
        inviteService.acceptInvite(inviteId, me());
        return ApiResponse.ok("Invite accepted", null);
    }

    /** Reject a trip invite. */
    @PostMapping("/api/users/invites/{inviteId}/reject")
    public ApiResponse<?> reject(@PathVariable Long inviteId) {
        inviteService.rejectInvite(inviteId, me());
        return ApiResponse.ok("Invite declined", null);
    }

    @Data
    public static class InviteReq {
        private Long userId;
    }
}
