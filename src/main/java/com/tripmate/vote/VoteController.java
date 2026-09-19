package com.tripmate.vote;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.common.exception.ResourceNotFoundException;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.place.entity.Place;
import com.tripmate.place.repository.PlaceRepository;
import com.tripmate.vote.entity.PlaceVote;
import com.tripmate.vote.repository.PlaceVoteRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class VoteController {

    private final PlaceVoteRepository votes;
    private final PlaceRepository places;
    private final TripMemberRepository members;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @PostMapping("/api/places/{id}/vote")
    public ApiResponse<Map<String, Long>> vote(@PathVariable Long id, @RequestBody VoteReq req) {
        Place p = places.findById(id).orElseThrow(() -> new ResourceNotFoundException("Place not found"));
        if (!members.existsByTripIdAndUserId(p.getTripId(), me())) {
            throw new BadRequestException("Not a trip member");
        }
        if (!List.of("VISIT", "SKIP", "MAYBE").contains(req.getVote())) {
            throw new BadRequestException("Invalid vote");
        }
        PlaceVote v = votes.findByPlaceIdAndUserId(id, me()).orElseGet(() -> {
            PlaceVote nv = new PlaceVote();
            nv.setPlaceId(id);
            nv.setUserId(me());
            return nv;
        });
        v.setVoteStatus(req.getVote());
        votes.save(v);
        Map<String, Long> summary = new HashMap<>();
        for (Object[] row : votes.countByPlaceIdGrouped(id)) summary.put((String) row[0], (Long) row[1]);
        return ApiResponse.ok("Voted", summary);
    }

    @Data
    public static class VoteReq {
        private String vote;
    }
}
