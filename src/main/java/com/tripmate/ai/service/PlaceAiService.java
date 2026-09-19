package com.tripmate.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripmate.ai.entity.AiSuggestion;
import com.tripmate.ai.repository.AiSuggestionRepository;
import com.tripmate.ai.service.AiRouterService.AiResult;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.common.exception.ResourceNotFoundException;
import com.tripmate.config.service.ConfigService;
import com.tripmate.geo.GeoService;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.place.entity.Place;
import com.tripmate.place.repository.PlaceRepository;
import com.tripmate.trip.entity.Trip;
import com.tripmate.trip.repository.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI place suggestions: propose ranked must-visit stops (quota-counted),
 * verify coordinates via the geocoder, then batch-add chosen ones.
 */
@Service
@RequiredArgsConstructor
public class PlaceAiService {

    private static final List<String> COUNTED = List.of("SUGGESTED", "ADDED");

    private final AiSuggestionRepository suggestions;
    private final TripRepository trips;
    private final TripMemberRepository members;
    private final PlaceRepository places;
    private final ConfigService config;
    private final AiRouterService ai;
    private final GeoService geo;
    private final ObjectMapper objectMapper;

    public record Quota(long used, long limit, long remaining) {
    }

    public Quota quota(Long tripId) {
        long limit = Math.max(0, config.getInt("ai.free.suggestions.per.trip", 5));
        long used = suggestions.countByTripIdAndStatusIn(tripId, COUNTED);
        return new Quota(used, limit, Math.max(0, limit - used));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> suggest(Long tripId, Long userId, Integer count,
                                       List<String> interests, Integer dayNo,
                                       String from, String to) {
        requireMember(tripId, userId);
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        Quota q = quota(tripId);
        if (q.remaining() <= 0) {
            throw new BadRequestException(
                    "Free suggestions used (" + q.used() + "/" + q.limit() + " for this trip).");
        }
        int n = count == null ? 5 : Math.min(8, Math.max(1, count));
        List<Place> known = places.findByTripId(tripId);
        Set<String> knownNames = new HashSet<>();
        for (Place p : known) knownNames.add(p.getName().toLowerCase(Locale.ROOT));

        String system = "You are TripMate's local expert. Suggest must-visit stops as STRICT JSON only, "
                + "no markdown fences, exactly: {\"places\":[{\"name\":\"...\",\"address\":\"...\","
                + "\"category\":\"...\",\"reason\":\"...\",\"lat\":12.3,\"lng\":45.6}]}. "
                + "Rank best-first with a one-line reason each. Coordinates are rough estimates. "
                + "Keep names short and map-searchable (e.g. 'Lonavala', not 'Lonavala - Rajmachi Trek'). "
                + "Never repeat the known places. Categories: Nature, Waterfall, Food, Stay, "
                + "Viewpoint, Temple, Adventure, Other.";
        StringBuilder user = new StringBuilder();
        user.append("Trip: ").append(nvl(trip.getTripName(), "Road trip"));
        user.append(" from ").append(nvl(trip.getStartName(), "?"));
        user.append(" to ").append(nvl(trip.getDestName(), "?")).append(".\n");
        user.append("Already on the trip: ");
        user.append(known.isEmpty() ? "(none yet)" : String.join(", ", knownNames)).append("\n");
        user.append("Interests: ").append(interests == null || interests.isEmpty()
                ? "mixed" : String.join(",", interests)).append("\n");
        if (from != null && !from.isBlank() && to != null && !to.isBlank()) {
            user.append("Focus on Day ").append(dayNo == null ? "?" : dayNo)
                    .append(": best stops ON THE WAY from ").append(from.trim())
                    .append(" to ").append(to.trim())
                    .append(", including good overnight-stay candidates. "
                            + "Rank the most stay-worthy highest.\n");
        }
        user.append("Suggest exactly ").append(n).append(" stops.");

        AiResult result = ai.generate(system, user.toString());
        List<Map<String, Object>> raw = parsePlaces(result.getText());
        List<Map<String, Object>> out = new ArrayList<>();
        for (Map<String, Object> s : raw) {
            String name = s.get("name") == null ? "" : s.get("name").toString().trim();
            if (name.isEmpty() || knownNames.contains(name.toLowerCase(Locale.ROOT))) continue;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", name);
            m.put("address", str(s.get("address")));
            m.put("category", str(s.get("category"), "Other"));
            m.put("reason", str(s.get("reason")));
            Double[] verified = verify(name, trip);
            if (verified != null) {
                m.put("latitude", verified[0]);
                m.put("longitude", verified[1]);
                m.put("verified", true);
            } else {
                m.put("latitude", num(s.get("lat")));
                m.put("longitude", num(s.get("lng")));
                m.put("verified", false);
            }
            out.add(m);
            if (out.size() >= n) break;
        }
        if (out.isEmpty()) {
            throw new BadRequestException("AI returned no usable stops — please retry");
        }
        AiSuggestion row = new AiSuggestion();
        row.setTripId(tripId);
        row.setProvider(result.getProvider());
        row.setModel(result.getModel());
        try {
            row.setPayload(objectMapper.writeValueAsString(Map.of("places", out)));
        } catch (Exception e) {
            throw new BadRequestException("Suggestions could not be stored");
        }
        row.setStatus("SUGGESTED");
        AiSuggestion saved = suggestions.save(row);
        Quota after = quota(tripId);
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("suggestionId", saved.getId());
        resp.put("provider", saved.getProvider());
        resp.put("places", out);
        resp.put("used", after.used());
        resp.put("remaining", after.remaining());
        return resp;
    }

    public Map<String, Object> one(Long tripId, Long userId, Long suggestionId) {
        requireMember(tripId, userId);
        AiSuggestion s = suggestions.findByIdAndTripId(suggestionId, tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Suggestions not found"));
        return toMap(s);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> add(Long tripId, Long userId, Long suggestionId,
                                   List<Integer> indexes, Integer dayNo) {
        requireMember(tripId, userId);
        AiSuggestion s = suggestions.findByIdAndTripId(suggestionId, tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Suggestions not found"));
        List<Map<String, Object>> all;
        try {
            Map<String, Object> payload =
                    objectMapper.readValue(s.getPayload(), Map.class);
            all = (List<Map<String, Object>>) payload.getOrDefault("places", List.of());
        } catch (Exception e) {
            throw new BadRequestException("Suggestions are unreadable");
        }
        Set<String> existing = new HashSet<>();
        for (Place p : places.findByTripId(tripId)) {
            existing.add(p.getName().toLowerCase(Locale.ROOT));
        }
        int added = 0, skipped = 0;
        if (indexes != null) {
            for (Integer i : indexes) {
                if (i == null || i < 0 || i >= all.size()) {
                    skipped++;
                    continue;
                }
                Map<String, Object> m = all.get(i);
                String name = m.get("name") == null ? "" : m.get("name").toString().trim();
                if (name.isEmpty() || existing.contains(name.toLowerCase(Locale.ROOT))) {
                    skipped++;
                    continue;
                }
                Place p = new Place();
                p.setTripId(tripId);
                p.setName(name);
                p.setAddress(str(m.get("address")));
                p.setCategory(str(m.get("category"), "Other"));
                p.setLatitude(num(m.get("latitude")));
                p.setLongitude(num(m.get("longitude")));
                p.setDescription(m.get("reason") == null ? null
                        : "AI pick: " + m.get("reason").toString());
                p.setAddedBy(userId);
                if (dayNo != null && dayNo > 0) {
                    p.setRemarks("ai-day:" + dayNo);
                }
                places.save(p);
                existing.add(name.toLowerCase(Locale.ROOT));
                added++;
            }
        }
        s.setStatus("ADDED");
        suggestions.save(s);
        return Map.of("added", added, "skipped", skipped);
    }

    // ---------- helpers ----------

    private void requireMember(Long tripId, Long userId) {
        if (!members.existsByTripIdAndUserId(tripId, userId)) {
            throw new BadRequestException("Not a trip member");
        }
    }

    /** Geocode-verify a suggested name near the trip; rejects other continents. */
    private Double[] verify(String name, Trip trip) {
        int maxKm = config.getInt("geo.verify.max.km", 3000);
        Double refLat = trip.getStartLat() != null ? trip.getStartLat() : trip.getDestLat();
        Double refLng = trip.getStartLng() != null ? trip.getStartLng() : trip.getDestLng();
        return geo.verifyCoords(name, refLat, refLng, maxKm);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parsePlaces(String text) {
        String clean = text.trim();
        if (clean.startsWith("```")) {
            int first = clean.indexOf('\n');
            int last = clean.lastIndexOf("```");
            clean = (first >= 0 && last > first) ? clean.substring(first, last).trim() : clean;
        }
        try {
            Map<String, Object> root = objectMapper.readValue(clean, Map.class);
            Object pl = root.get("places");
            if (!(pl instanceof List) || ((List<?>) pl).isEmpty()) {
                throw new BadRequestException("AI returned no stops — please retry");
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : (List<?>) pl) {
                if (o instanceof Map) out.add(new LinkedHashMap<>((Map<String, Object>) o));
            }
            if (out.isEmpty()) {
                throw new BadRequestException("AI returned no stops — please retry");
            }
            return out;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("AI returned an unreadable list — please retry");
        }
    }

    private Map<String, Object> toMap(AiSuggestion s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("suggestionId", s.getId());
        out.put("provider", s.getProvider());
        out.put("status", s.getStatus());
        try {
            Map<String, Object> payload =
                    objectMapper.readValue(s.getPayload(), Map.class);
            out.put("places", payload.getOrDefault("places", List.of()));
        } catch (Exception e) {
            out.put("places", List.of());
        }
        return out;
    }

    private static String nvl(String s, String fb) {
        return s == null || s.isBlank() ? fb : s;
    }

    private static String str(Object o) {
        return str(o, null);
    }

    private static String str(Object o, String fb) {
        if (o == null) return fb;
        String s = o.toString().trim();
        return s.isEmpty() ? fb : s;
    }

    private static Double num(Object o) {
        if (!(o instanceof Number)) return null;
        double v = ((Number) o).doubleValue();
        return Double.isFinite(v) ? v : null;
    }
}
