package com.tripmate.place;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.common.exception.ResourceNotFoundException;
import com.tripmate.config.service.ConfigService;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.place.entity.Place;
import com.tripmate.place.repository.PlaceRepository;
import com.tripmate.vote.repository.PlaceVoteRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequiredArgsConstructor
public class PlaceController {

    private final PlaceRepository places;
    private final PlaceVoteRepository votes;
    private final TripMemberRepository members;
    private final ConfigService config;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @PostMapping("/api/trips/{tripId}/places")
    public ApiResponse<Place> add(@PathVariable Long tripId, @Valid @RequestBody PlaceReq req) {
        requireMember(tripId);
        int max = config.getInt("trip.max.places", 50);
        if (places.findByTripId(tripId).size() >= max) {
            throw new BadRequestException("Place limit reached (" + max + ")");
        }
        Place p = new Place();
        p.setTripId(tripId);
        p.setName(req.getName());
        p.setLatitude(req.getLatitude());
        p.setLongitude(req.getLongitude());
        p.setAddress(req.getAddress());
        p.setImageUrl(req.getImageUrl());
        p.setDescription(req.getDescription());
        p.setCategory(req.getCategory());
        p.setAddedBy(me());
        return ApiResponse.ok("Place added", places.save(p));
    }

    @GetMapping("/api/trips/{tripId}/places")
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable Long tripId,
                                                       @RequestParam(defaultValue = "all") String tab) {
        requireMember(tripId);
        List<Place> all = places.findByTripId(tripId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Place p : all) {
            Map<String, Long> summary = summary(p.getId());
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("place", p);
            m.put("votes", summary);
            m.put("visitCount", summary.getOrDefault("VISIT", 0L));
            votes.findByPlaceIdAndUserId(p.getId(), me()).ifPresent(v -> m.put("myVote", v.getVoteStatus()));
            out.add(m);
        }
        if ("voted".equalsIgnoreCase(tab)) {
            out.sort((a, b) -> Long.compare((Long) b.get("visitCount"), (Long) a.get("visitCount")));
        } else if ("mine".equalsIgnoreCase(tab)) {
            out.removeIf(m -> !m.containsKey("myVote"));
        }
        return ApiResponse.ok(out);
    }

    @GetMapping("/api/places/{id}")
    public ApiResponse<Map<String, Object>> one(@PathVariable Long id) {
        Place p = places.findById(id).orElseThrow(() -> new ResourceNotFoundException("Place not found"));
        requireMember(p.getTripId());
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("place", p);
        m.put("votes", summary(id));
        votes.findByPlaceIdAndUserId(id, me()).ifPresent(v -> m.put("myVote", v.getVoteStatus()));
        return ApiResponse.ok(m);
    }

    @DeleteMapping("/api/places/{id}")
    public ApiResponse<?> delete(@PathVariable Long id) {
        Place p = places.findById(id).orElseThrow(() -> new ResourceNotFoundException("Place not found"));
        requireMember(p.getTripId());
        p.softDelete();
        places.save(p);
        return ApiResponse.ok("Deleted", null);
    }

    private Map<String, Long> summary(Long placeId) {
        Map<String, Long> m = new HashMap<>();
        for (Object[] row : votes.countByPlaceIdGrouped(placeId)) {
            m.put((String) row[0], (Long) row[1]);
        }
        return m;
    }

    private void requireMember(Long tripId) {
        if (!members.existsByTripIdAndUserId(tripId, me())) throw new BadRequestException("Not a trip member");
    }

    @Data
    public static class PlaceReq {
        @NotBlank private String name;
        private Double latitude;
        private Double longitude;
        private String address;
        private String imageUrl;
        private String description;
        private String category;
    }
}
