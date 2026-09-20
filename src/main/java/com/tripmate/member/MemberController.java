package com.tripmate.member;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.member.entity.TripMember;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.trip.service.ActivityService;
import com.tripmate.user.entity.User;
import com.tripmate.user.repository.UserRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class MemberController {

    private final TripMemberRepository members;
    private final UserRepository users;
    private final ActivityService activity;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping("/api/trips/{id}/members")
    public ApiResponse<List<TripMember>> list(@PathVariable Long id) {
        if (!members.existsByTripIdAndUserId(id, me())) throw new BadRequestException("Not a trip member");
        return ApiResponse.ok(members.findByTripId(id));
    }

    /** Members enriched with profile info for People screens. */
    @GetMapping("/api/trips/{id}/members/detailed")
    public ApiResponse<List<Map<String, Object>>> detailed(@PathVariable Long id) {
        if (!members.existsByTripIdAndUserId(id, me())) throw new BadRequestException("Not a trip member");
        return ApiResponse.ok(members.findByTripId(id).stream().map(m -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", m.getId());
            out.put("userId", m.getUserId());
            out.put("role", m.getRole());
            out.put("rsvp", m.getRsvp());
            users.findById(m.getUserId()).ifPresent(u -> {
                out.put("name", u.getName());
                out.put("email", u.getEmail());
                out.put("profileImage", u.getProfileImage());
            });
            return out;
        }).toList());
    }

    @PatchMapping("/api/trips/{id}/members/rsvp")
    public ApiResponse<TripMember> rsvp(@PathVariable Long id, @RequestBody RsvpReq req) {
        TripMember m = members.findByTripIdAndUserId(id, me())
                .orElseThrow(() -> new BadRequestException("Not a trip member"));
        if (!List.of("GOING", "MAYBE", "NOT_GOING").contains(req.getRsvp())) {
            throw new BadRequestException("Invalid RSVP");
        }
        String old = m.getRsvp();
        m.setRsvp(req.getRsvp());
        TripMember saved = members.save(m);
        activity.log(id, me(), "RSVP_CHANGED", old + " → " + req.getRsvp());
        return ApiResponse.ok(saved);
    }

    /** Trip activity timeline — who joined, when, what changed. */
    @GetMapping("/api/trips/{id}/activity")
    public ApiResponse<List<Map<String, Object>>> activity(@PathVariable Long id) {
        if (!members.existsByTripIdAndUserId(id, me())) throw new BadRequestException("Not a trip member");
        return ApiResponse.ok(activity.getTimeline(id));
    }

    /** Users who were ever part of this trip (for re-share). */
    @GetMapping("/api/trips/{id}/past-members")
    public ApiResponse<List<Map<String, Object>>> pastMembers(@PathVariable Long id) {
        if (!members.existsByTripIdAndUserId(id, me())) throw new BadRequestException("Not a trip member");
        return ApiResponse.ok(activity.getPastMembers(id));
    }

    @Data
    public static class RsvpReq {
        private String rsvp;
    }
}
