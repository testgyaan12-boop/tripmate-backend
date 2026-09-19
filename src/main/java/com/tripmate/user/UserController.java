package com.tripmate.user;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.member.entity.TripMember;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.place.repository.PlaceRepository;
import com.tripmate.trip.repository.TripRepository;
import com.tripmate.user.entity.User;
import com.tripmate.user.repository.UserRepository;
import com.tripmate.vote.repository.PlaceVoteRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository users;
    private final TripRepository trips;
    private final TripMemberRepository members;
    private final PlaceRepository places;
    private final PlaceVoteRepository votes;

    @GetMapping("/me")
    public ApiResponse<User> me() {
        Long id = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        return users.findById(id).map(ApiResponse::ok).orElseThrow();
    }

    @PatchMapping("/me")
    public ApiResponse<User> update(@RequestBody UpdateReq req) {
        Long id = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        User u = users.findById(id).orElseThrow();
        if (req.getName() != null) u.setName(req.getName());
        if (req.getMobile() != null) u.setMobile(req.getMobile());
        if (req.getProfileImage() != null) u.setProfileImage(req.getProfileImage());
        if (req.getCity() != null) u.setCity(req.getCity());
        if (req.getTravelStyle() != null) u.setTravelStyle(req.getTravelStyle());
        if (req.getFavoritePlaces() != null) u.setFavoritePlaces(req.getFavoritePlaces());
        if (req.getVehicle() != null) u.setVehicle(req.getVehicle());
        if (req.getBudgetType() != null) u.setBudgetType(req.getBudgetType());
        return ApiResponse.ok(users.save(u));
    }

    /** Profile statistics for the travel-identity page. */
    @GetMapping("/me/stats")
    public ApiResponse<Map<String, Object>> stats() {
        Long id = Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
        List<TripMember> mine = members.findByUserId(id);
        List<Long> tripIds = mine.stream().map(TripMember::getTripId).toList();
        long friendsMet = 0;
        long placesVisited = 0;
        if (!tripIds.isEmpty()) {
            friendsMet = members.findByTripIdIn(tripIds).stream()
                    .map(TripMember::getUserId)
                    .filter(u -> !u.equals(id))
                    .distinct().count();
            placesVisited = places.findByTripIdIn(tripIds).size();
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("tripsCreated", trips.countByCreatedByUserId(id));
        out.put("tripsJoined", mine.size());
        out.put("placesVisited", placesVisited);
        out.put("friendsMet", friendsMet);
        out.put("votesGiven", votes.countByUserId(id));
        return ApiResponse.ok(out);
    }

    @Data
    public static class UpdateReq {
        private String name;
        private String mobile;
        private String profileImage;
        private String city;
        private String travelStyle;
        private String favoritePlaces;
        private String vehicle;
        private String budgetType;
    }
}
