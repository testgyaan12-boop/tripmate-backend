package com.tripmate.itinerary;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.common.exception.ResourceNotFoundException;
import com.tripmate.itinerary.entity.ItineraryItem;
import com.tripmate.itinerary.repository.ItineraryItemRepository;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.place.entity.Place;
import com.tripmate.place.repository.PlaceRepository;
import com.tripmate.trip.entity.Trip;
import com.tripmate.trip.repository.TripRepository;
import com.tripmate.vote.repository.PlaceVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/trips/{tripId}/itinerary")
@RequiredArgsConstructor
public class ItineraryController {

    private final ItineraryItemRepository items;
    private final PlaceRepository places;
    private final TripRepository trips;
    private final PlaceVoteRepository votes;
    private final TripMemberRepository members;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping
    public ApiResponse<List<ItineraryItem>> list(@PathVariable Long tripId) {
        requireMember(tripId);
        return ApiResponse.ok(items.findByTripIdOrderByDayNoAscSortOrderAsc(tripId));
    }

    /**
     * Auto-generate: top VISIT-voted places distributed round-robin across trip days.
     */
    @PutMapping("/generate")
    public ApiResponse<List<ItineraryItem>> generate(@PathVariable Long tripId,
                                                     @RequestParam(defaultValue = "3") int days) {
        requireMember(tripId);
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        int d = Math.min(30, Math.max(1, days));
        List<ItineraryItem> existing = items.findByTripIdOrderByDayNoAscSortOrderAsc(tripId);
        for (ItineraryItem e : existing) {
            e.softDelete();
            items.save(e);
        }
        List<Place> all = places.findByTripId(tripId);
        if (all.isEmpty()) {
            return ApiResponse.ok("Generated route skeleton", skeleton(tripId, trip, d));
        }
        Map<Long, Long> visitCounts = new HashMap<>();
        for (Place p : all) {
            long c = 0;
            for (Object[] row : votes.countByPlaceIdGrouped(p.getId())) {
                if ("VISIT".equals(row[0])) c = (Long) row[1];
            }
            visitCounts.put(p.getId(), c);
        }
        all.sort((a, b) -> Long.compare(visitCounts.getOrDefault(b.getId(), 0L),
                visitCounts.getOrDefault(a.getId(), 0L)));
        List<ItineraryItem> out = new ArrayList<>();
        Place prev = null;
        for (int i = 0; i < all.size(); i++) {
            Place p = all.get(i);
            ItineraryItem it = new ItineraryItem();
            it.setTripId(tripId);
            it.setDayNo((i % d) + 1);
            it.setPlaceId(p.getId());
            it.setTitle(p.getName());
            it.setSortOrder(i / d);
            if (prev != null && prev.getLatitude() != null && prev.getLongitude() != null
                    && p.getLatitude() != null && p.getLongitude() != null) {
                double km = haversine(prev.getLatitude(), prev.getLongitude(),
                        p.getLatitude(), p.getLongitude());
                it.setDistanceKm(Math.round(km * 10.0) / 10.0);
                it.setDriveMins((int) Math.round(km / 50.0 * 60.0));
            }
            prev = p;
            out.add(items.save(it));
        }
        return ApiResponse.ok("Generated", out);
    }

    private static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    /**
     * Day skeleton when the trip has no places yet: anchors Day 1 at the
     * start and the last day at the destination, splitting the aerial
     * distance into per-day estimates. Never invents named stops.
     */
    private List<ItineraryItem> skeleton(Long tripId, Trip trip, int days) {
        String start = trip.getStartName();
        String dest = trip.getDestName();
        boolean hasStart = start != null && !start.isBlank();
        boolean hasDest = dest != null && !dest.isBlank();
        if (!hasStart && !hasDest) {
            throw new BadRequestException(
                    "Add a starting point, destination or places before generating");
        }
        Double legKm = null;
        Integer legMins = null;
        if (trip.getStartLat() != null && trip.getStartLng() != null
                && trip.getDestLat() != null && trip.getDestLng() != null
                && days > 0) {
            double total = haversine(trip.getStartLat(), trip.getStartLng(),
                    trip.getDestLat(), trip.getDestLng());
            legKm = Math.round(total / days * 10.0) / 10.0;
            legMins = (int) Math.round(legKm / 50.0 * 60.0);
        }
        List<ItineraryItem> out = new ArrayList<>();
        for (int day = 1; day <= days; day++) {
            ItineraryItem it = new ItineraryItem();
            it.setTripId(tripId);
            it.setDayNo(day);
            if (days == 1 && hasStart && hasDest) {
                it.setTitle(start + " → " + dest);
            } else if (day == 1 && hasStart) {
                it.setTitle(start + " — journey begins");
            } else if (day == days && hasDest) {
                it.setTitle(dest + " — arrival");
            } else if (hasStart && hasDest) {
                it.setTitle(start + " → " + dest + " · on the road");
            } else if (hasStart) {
                it.setTitle(start + " · day " + day);
            } else {
                it.setTitle(dest + " · day " + day);
            }
            it.setDistanceKm(legKm);
            it.setDriveMins(legMins);
            it.setSortOrder(0);
            out.add(items.save(it));
        }
        return out;
    }

    @PostMapping
    public ApiResponse<ItineraryItem> create(@PathVariable Long tripId,
                                              @RequestBody ItineraryItem body) {
        requireMember(tripId);
        if (body.getTitle() == null || body.getTitle().isBlank()) {
            throw new BadRequestException("Title is required");
        }
        int dayNo = Math.max(1, body.getDayNo() != null ? body.getDayNo() : 1);
        int maxSort = 0;
        for (ItineraryItem e : items.findByTripIdOrderByDayNoAscSortOrderAsc(tripId)) {
            if (e.getDayNo() == dayNo && e.getSortOrder() != null && e.getSortOrder() >= maxSort) {
                maxSort = e.getSortOrder() + 1;
            }
        }
        ItineraryItem it = new ItineraryItem();
        it.setTripId(tripId);
        it.setDayNo(dayNo);
        it.setPlaceId(body.getPlaceId());
        it.setTitle(body.getTitle().trim());
        it.setDistanceKm(body.getDistanceKm());
        it.setDriveMins(body.getDriveMins());
        it.setStayNotes(body.getStayNotes());
        it.setStopsJson(body.getStopsJson());
        it.setFoodNotes(body.getFoodNotes());
        it.setSortOrder(maxSort);
        return ApiResponse.ok("Created", items.save(it));
    }

    @PutMapping("/{itemId}")
    public ApiResponse<ItineraryItem> update(@PathVariable Long tripId,
                                              @PathVariable Long itemId,
                                              @RequestBody ItineraryItem body) {
        requireMember(tripId);
        ItineraryItem it = items.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        if (!it.getTripId().equals(tripId)) throw new BadRequestException("Item not in this trip");
        if (body.getTitle() != null && !body.getTitle().isBlank()) it.setTitle(body.getTitle().trim());
        if (body.getDayNo() != null) it.setDayNo(Math.max(1, body.getDayNo()));
        if (body.getSortOrder() != null) it.setSortOrder(body.getSortOrder());
        if (body.getPlaceId() != null) it.setPlaceId(body.getPlaceId());
        if (body.getDistanceKm() != null) it.setDistanceKm(body.getDistanceKm());
        if (body.getDriveMins() != null) it.setDriveMins(body.getDriveMins());
        if (body.getStayNotes() != null) it.setStayNotes(body.getStayNotes());
        if (body.getStopsJson() != null) it.setStopsJson(body.getStopsJson());
        if (body.getFoodNotes() != null) it.setFoodNotes(body.getFoodNotes());
        return ApiResponse.ok("Updated", items.save(it));
    }

    @DeleteMapping("/{itemId}")
    public ApiResponse<Void> delete(@PathVariable Long tripId,
                                     @PathVariable Long itemId) {
        requireMember(tripId);
        ItineraryItem it = items.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Item not found"));
        if (!it.getTripId().equals(tripId)) throw new BadRequestException("Item not in this trip");
        it.softDelete();
        items.save(it);
        return ApiResponse.ok("Deleted", null);
    }

    private void requireMember(Long tripId) {
        if (!members.existsByTripIdAndUserId(tripId, me())) throw new BadRequestException("Not a trip member");
    }
}
