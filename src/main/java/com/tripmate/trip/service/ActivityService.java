package com.tripmate.trip.service;

import com.tripmate.trip.entity.TripActivity;
import com.tripmate.trip.repository.TripActivityRepository;
import com.tripmate.user.entity.User;
import com.tripmate.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class ActivityService {

    private final TripActivityRepository activity;
    private final UserRepository users;

    public void log(Long tripId, Long userId, String action, String detail) {
        TripActivity a = new TripActivity();
        a.setTripId(tripId);
        a.setUserId(userId);
        a.setAction(action);
        a.setDetail(detail);
        activity.save(a);
    }

    public List<Map<String, Object>> getTimeline(Long tripId) {
        return activity.findByTripIdOrderByCreatedAtDesc(tripId).stream().map(a -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", a.getId());
            out.put("userId", a.getUserId());
            out.put("action", a.getAction());
            out.put("detail", a.getDetail());
            out.put("createdAt", a.getCreatedAt());
            users.findById(a.getUserId()).ifPresent(u -> {
                out.put("name", u.getName());
                out.put("profileImage", u.getProfileImage());
            });
            return out;
        }).toList();
    }

    /** Users who were ever members (active or soft-deleted) of this trip. */
    public List<Map<String, Object>> getPastMembers(Long tripId) {
        var all = activity.findByTripIdOrderByCreatedAtDesc(tripId);
        var seen = new java.util.LinkedHashSet<Long>();
        var result = new java.util.ArrayList<Map<String, Object>>();
        for (var a : all) {
            if (a.getUserId() == null || !seen.add(a.getUserId())) continue;
            var out = new LinkedHashMap<String, Object>();
            out.put("userId", a.getUserId());
            out.put("lastAction", a.getAction());
            out.put("lastAt", a.getCreatedAt());
            users.findById(a.getUserId()).ifPresent(u -> {
                out.put("name", u.getName());
                out.put("email", u.getEmail());
                out.put("profileImage", u.getProfileImage());
            });
            result.add(out);
        }
        return result;
    }
}
