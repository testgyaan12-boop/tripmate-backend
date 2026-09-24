package com.tripmate.trip;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.common.exception.GoneException;
import com.tripmate.common.exception.ResourceNotFoundException;
import com.tripmate.billing.SubscriptionService;
import com.tripmate.config.service.ConfigService;
import com.tripmate.member.entity.TripMember;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.trip.entity.Trip;
import com.tripmate.trip.repository.TripRepository;
import com.tripmate.trip.service.ActivityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/trips")
@RequiredArgsConstructor
public class TripController {

    private final TripRepository trips;
    private final TripMemberRepository members;
    private final ConfigService config;
    private final ActivityService activity;
    private final SubscriptionService subs;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @PostMapping
    public ApiResponse<Trip> create(@Valid @RequestBody CreateTripReq req) {
        int maxMembers = config.getInt("trip.max.members", 20);
        if (maxMembers < 1) throw new BadRequestException("Trip member limit misconfigured");
        int tripLimit = subs.intLimit(me(), "TRIP_LIMIT", config.getInt("free.trip.limit", 3));
        long mine = trips.findByCreatedByUserId(me()).stream().filter(Trip::isAlive).count();
        if (mine >= tripLimit) {
            throw new BadRequestException("Trip limit reached (" + mine + "/" + tripLimit + " trips used). Upgrade to Pro for unlimited trips.");
        }
        Trip t = new Trip();
        t.setTripName(req.getTripName());
        t.setStartName(req.getStartName());
        checkTripPin("start", req.getStartLat(), req.getStartLng());
        t.setStartLat(req.getStartLat());
        t.setStartLng(req.getStartLng());
        t.setDestName(req.getDestName());
        checkTripPin("destination", req.getDestLat(), req.getDestLng());
        t.setDestLat(req.getDestLat());
        t.setDestLng(req.getDestLng());
        t.setStartDate(req.getStartDate());
        t.setEndDate(req.getEndDate());
        t.setDaysCount(req.getDaysCount());
        t.setCreatedByUserId(me());
        t.setStatus("PLANNING");
        t.setInviteCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        Trip saved = trips.save(t);
        TripMember owner = new TripMember();
        owner.setTripId(saved.getId());
        owner.setUserId(me());
        owner.setRole("OWNER");
        owner.setRsvp("GOING");
        members.save(owner);
        activity.log(saved.getId(), me(), "CREATED", "Trip created");
        return ApiResponse.ok("Trip created", saved);
    }

    @GetMapping("/mine")
    public ApiResponse<List<Trip>> mine() {
        List<Long> ids = members.findByUserId(me()).stream().map(TripMember::getTripId).toList();
        return ApiResponse.ok(trips.findAllById(ids));
    }

    @GetMapping("/{id}")
    public ApiResponse<Trip> one(@PathVariable Long id) {
        Trip t = trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        requireMember(id);
        return ApiResponse.ok(t);
    }

    /** Public trip info: safe to show non-members (for join / not-member card). */
    @GetMapping("/{id}/info")
    public ApiResponse<Map<String, Object>> publicInfo(@PathVariable Long id) {
        Trip t = trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", t.getId());
        out.put("tripName", t.getTripName());
        out.put("startName", t.getStartName());
        out.put("destName", t.getDestName());
        out.put("startDate", t.getStartDate());
        out.put("endDate", t.getEndDate());
        out.put("daysCount", t.getDaysCount());
        out.put("inviteCode", t.getInviteCode());
        out.put("memberCount", members.findByTripId(t.getId()).size());
        return ApiResponse.ok(out);
    }

    /** Public invite preview: limited fields, safe to show before login. */
    @GetMapping("/by-code/{code}")
    public ApiResponse<Map<String, Object>> byCode(@PathVariable String code) {
        Trip t = trips.findByInviteCode(code.toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Invalid invite link"));
        checkInviteFresh(t);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tripId", t.getId());
        out.put("tripName", t.getTripName());
        out.put("startName", t.getStartName());
        out.put("destName", t.getDestName());
        out.put("startDate", t.getStartDate());
        out.put("endDate", t.getEndDate());
        out.put("daysCount", t.getDaysCount());
        out.put("memberCount", members.findByTripId(t.getId()).size());
        return ApiResponse.ok(out);
    }

    /** Owner regenerates a dead/expired invite code. */
    @PostMapping("/{id}/invite/refresh")
    public ApiResponse<Map<String, Object>> refreshInvite(@PathVariable Long id) {
        Trip t = trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        requireOwner(id);
        t.setInviteCode(UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase());
        trips.save(t);
        return ApiResponse.ok("Invite refreshed", Map.of("inviteCode", t.getInviteCode()));
    }

    private void checkInviteFresh(Trip t) {
        int days = config.getInt("trip.invite.expiry.days", 7);
        if (days <= 0) return;
        if (t.getCreatedAt() == null) return;
        if (t.getCreatedAt().plusDays(days).isBefore(LocalDateTime.now())) {
            throw new GoneException("Invite link expired. Ask the owner for a fresh one.");
        }
    }

    @PostMapping("/{id}/join")
    public ApiResponse<?> join(@PathVariable Long id, @RequestBody JoinReq req) {
        Trip t = trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        if (!t.getInviteCode().equalsIgnoreCase(req.getInviteCode())) {
            throw new BadRequestException("Invalid invite code");
        }
        if (!members.existsByTripIdAndUserId(id, me())) {
            subs.checkMemberLimit(id, me());
            subs.checkJoinLimit(me());
            TripMember m = new TripMember();
            m.setTripId(id);
            m.setUserId(me());
            m.setRole("MEMBER");
            m.setRsvp("GOING");
            members.save(m);
            activity.log(id, me(), "JOINED", "Joined via invite code");
        }
        return ApiResponse.ok("Joined", null);
    }

    @PostMapping("/{id}/finalize")
    public ApiResponse<Trip> finalizeTrip(@PathVariable Long id) {
        Trip t = trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        requireOwner(id);
        t.setStatus("FINALIZED");
        return ApiResponse.ok("Finalized", trips.save(t));
    }

    @PutMapping("/{id}")
    public ApiResponse<Trip> update(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Trip t = trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        requireOwner(id);
        if (body.containsKey("tripName")) t.setTripName((String) body.get("tripName"));
        if (body.containsKey("startName")) t.setStartName((String) body.get("startName"));
        if (body.containsKey("destName")) t.setDestName((String) body.get("destName"));
        if (body.containsKey("startDate")) t.setStartDate(body.get("startDate") != null ? LocalDate.parse((String) body.get("startDate")) : null);
        if (body.containsKey("endDate")) t.setEndDate(body.get("endDate") != null ? LocalDate.parse((String) body.get("endDate")) : null);
        if (body.containsKey("daysCount")) t.setDaysCount(body.get("daysCount") != null ? Integer.parseInt(body.get("daysCount").toString()) : null);
        return ApiResponse.ok("Updated", trips.save(t));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        Trip t = trips.findById(id).orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        requireOwner(id);
        t.softDelete();
        trips.save(t);
        return ApiResponse.ok("Deleted", null);
    }

    private void requireMember(Long tripId) {
        if (!members.existsByTripIdAndUserId(tripId, me())) {
            throw new BadRequestException("Not a trip member");
        }
    }

    private void requireOwner(Long tripId) {
        TripMember m = members.findByTripIdAndUserId(tripId, me())
                .orElseThrow(() -> new BadRequestException("Not a trip member"));
        if (!"OWNER".equals(m.getRole())) throw new BadRequestException("Owner only");
    }

    /** Rejects impossible trip pins (out of range / non-finite). */
    private static void checkTripPin(String which, Double lat, Double lng) {
        if (lat == null || lng == null) return;
        if (!Double.isFinite(lat) || !Double.isFinite(lng)
                || lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new BadRequestException("Invalid " + which + " coordinates — out of range");
        }
    }

    @Data
    public static class CreateTripReq {
        @NotBlank private String tripName;
        private String startName;
        private Double startLat;
        private Double startLng;
        private String destName;
        private Double destLat;
        private Double destLng;
        private LocalDate startDate;
        private LocalDate endDate;
        private Integer daysCount;
    }

    @Data
    public static class JoinReq {
        @NotBlank private String inviteCode;
    }
}
