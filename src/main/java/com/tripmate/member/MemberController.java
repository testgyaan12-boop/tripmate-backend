package com.tripmate.member;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.member.entity.TripMember;
import com.tripmate.member.repository.TripMemberRepository;
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
        m.setRsvp(req.getRsvp());
        return ApiResponse.ok(members.save(m));
    }

    @Data
    public static class RsvpReq {
        private String rsvp;
    }
}
