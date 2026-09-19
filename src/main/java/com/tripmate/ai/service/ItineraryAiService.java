package com.tripmate.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripmate.ai.entity.AiProposal;
import com.tripmate.ai.repository.AiProposalRepository;
import com.tripmate.ai.service.AiRouterService.AiResult;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.common.exception.ResourceNotFoundException;
import com.tripmate.config.service.ConfigService;
import com.tripmate.geo.GeoService;
import com.tripmate.itinerary.entity.ItineraryItem;
import com.tripmate.itinerary.repository.ItineraryItemRepository;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.place.entity.Place;
import com.tripmate.place.repository.PlaceRepository;
import com.tripmate.trip.entity.Trip;
import com.tripmate.trip.repository.TripRepository;
import com.tripmate.vote.repository.PlaceVoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI itinerary: propose (draft, quota-counted) -> user edits -> confirm (saves).
 * Editing and confirming are free; only successful proposals consume quota.
 */
@Service
@RequiredArgsConstructor
public class ItineraryAiService {

    private static final List<String> COUNTED = List.of("PROPOSED", "CONFIRMED");

    private final AiProposalRepository proposals;
    private final TripRepository trips;
    private final TripMemberRepository members;
    private final PlaceRepository places;
    private final PlaceVoteRepository votes;
    private final ItineraryItemRepository items;
    private final ConfigService config;
    private final AiRouterService ai;
    private final GeoService geo;
    private final ObjectMapper objectMapper;

    public record Quota(long used, long limit, long remaining) {
    }

    public Quota quota(Long tripId) {
        long limit = Math.max(0, config.getInt("ai.free.generations.per.trip", 2));
        long used = proposals.countByTripIdAndStatusIn(tripId, COUNTED);
        return new Quota(used, limit, Math.max(0, limit - used));
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> propose(Long tripId, Long userId, Map<String, Object> answers) {
        requireMember(tripId, userId);
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        Quota q = quota(tripId);
        if (q.remaining() <= 0) {
            throw new BadRequestException(
                    "Free AI plans used (" + q.used() + "/" + q.limit() + " for this trip).");
        }
        int days = trip.getDaysCount() != null && trip.getDaysCount() > 0
                ? Math.min(30, trip.getDaysCount()) : 3;
        List<Place> known = places.findByTripId(tripId);
        String system = "You are TripMate's road-trip planner. Output STRICT JSON only, "
                + "no markdown fences, no commentary, exactly this shape: "
                + "{\"days\":[{\"dayNo\":1,\"title\":\"...\",\"stops\":[\"...\"],"
                + "\"distanceKm\":12.5,\"driveMins\":30,\"stay\":\"...\",\"food\":\"...\"}]}. "
                + "dayNo runs 1..N with no gaps. distanceKm/driveMins are rough estimates "
                + "(numbers) or null. Titles short. Never invent precise distances.";
        String user = buildUserPrompt(trip, days, known, answers == null ? Map.of() : answers);
        AiResult result = ai.generate(system, user);
        List<Map<String, Object>> dayMaps = parseDays(result.getText(), days);
        AiProposal p = new AiProposal();
        p.setTripId(tripId);
        p.setProvider(result.getProvider());
        p.setModel(result.getModel());
        try {
            p.setQuestions(objectMapper.writeValueAsString(answers == null ? Map.of() : answers));
            p.setPayload(objectMapper.writeValueAsString(Map.of("days", dayMaps)));
        } catch (Exception e) {
            throw new BadRequestException("AI proposal could not be stored");
        }
        p.setStatus("PROPOSED");
        AiProposal saved = proposals.save(p);
        Quota after = quota(tripId);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("proposalId", saved.getId());
        out.put("provider", saved.getProvider());
        out.put("days", dayMaps);
        out.put("used", after.used());
        out.put("remaining", after.remaining());
        return out;
    }

    public Map<String, Object> latest(Long tripId, Long userId) {
        requireMember(tripId, userId);
        return proposals.findFirstByTripIdAndStatusOrderByCreatedAtDesc(tripId, "PROPOSED")
                .map(this::toMap).orElse(null);
    }

    public Map<String, Object> one(Long tripId, Long userId, Long proposalId) {
        requireMember(tripId, userId);
        AiProposal p = proposals.findByIdAndTripId(proposalId, tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Proposal not found"));
        return toMap(p);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> confirm(Long tripId, Long userId, Long proposalId,
                                       List<Map<String, Object>> editedDays) {
        requireMember(tripId, userId);
        AiProposal p = proposals.findByIdAndTripId(proposalId, tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Proposal not found"));
        if (!"PROPOSED".equals(p.getStatus())) {
            throw new BadRequestException("Proposal is already confirmed");
        }
        if (editedDays == null || editedDays.isEmpty()) {
            throw new BadRequestException("Nothing to save: days list is empty");
        }
        Map<String, Place> byName = new HashMap<>();
        for (Place place : places.findByTripId(tripId)) {
            byName.putIfAbsent(norm(place.getName()), place);
        }
        Trip trip = trips.findById(tripId)
                .orElseThrow(() -> new ResourceNotFoundException("Trip not found"));
        int verifyKm = config.getInt("geo.verify.max.km", 3000);
        Double refLat = trip.getStartLat() != null ? trip.getStartLat() : trip.getDestLat();
        Double refLng = trip.getStartLng() != null ? trip.getStartLng() : trip.getDestLng();
        for (ItineraryItem e : items.findByTripIdOrderByDayNoAscSortOrderAsc(tripId)) {
            e.softDelete();
            items.save(e);
        }
        int cap = Math.max(0, config.getInt("ai.confirm.max.new.places", 8));
        int created = 0, linked = 0;
        List<ItineraryItem> saved = new ArrayList<>();
        int order = 0;
        for (Map<String, Object> d : editedDays) {
            Object dayNo = d.get("dayNo");
            Object title = d.get("title");
            if (!(dayNo instanceof Number) || title == null
                    || title.toString().isBlank()) {
                throw new BadRequestException("Each day needs dayNo and title");
            }
            int day = ((Number) dayNo).intValue();
            if (day < 1 || day > 60) throw new BadRequestException("Bad dayNo: " + day);
            List<String> stops = strList(d.get("stops"));
            Long pid = null;
            Place hit = byName.get(norm(stripLegSuffix(title.toString().trim())));
            if (hit != null) {
                pid = hit.getId();
            } else {
                for (String s : stops) {
                    Place h = byName.get(norm(s));
                    if (h != null) {
                        pid = h.getId();
                        break;
                    }
                }
            }
            // auto-create unmatched stops (never leg titles), capped
            for (String s : stops) {
                if (created >= cap) break;
                String t = s.trim();
                if (t.isEmpty() || byName.containsKey(norm(t))) continue;
                Place np = createAiPlace(tripId, userId, t, refLat, refLng, verifyKm);
                if (np != null) {
                    byName.put(norm(t), np);
                    if (pid == null) pid = np.getId();
                    created++;
                }
            }
            if (pid != null) linked++;
            ItineraryItem it = new ItineraryItem();
            it.setTripId(tripId);
            it.setDayNo(day);
            it.setTitle(title.toString().trim());
            it.setPlaceId(pid);
            it.setDistanceKm(num(d.get("distanceKm")));
            Object mins = d.get("driveMins");
            it.setDriveMins(mins instanceof Number ? ((Number) mins).intValue() : null);
            it.setStayNotes(str(d.get("stay")));
            it.setFoodNotes(str(d.get("food")));
            it.setStopsJson(stops.isEmpty() ? null : toJson(stops));
            it.setSortOrder(order++);
            saved.add(items.save(it));
        }
        try {
            p.setPayload(objectMapper.writeValueAsString(Map.of("days", editedDays)));
        } catch (Exception ignored) {
        }
        p.setStatus("CONFIRMED");
        proposals.save(p);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("proposalId", p.getId());
        out.put("saved", saved.size());
        out.put("placesCreated", created);
        out.put("placesLinked", linked);
        return out;
    }

    private Place createAiPlace(Long tripId, Long userId, String name,
                                  Double refLat, Double refLng, int maxKm) {
        try {
            Double[] ll = geo.verifyCoords(name, refLat, refLng, maxKm);
            Place place = new Place();
            place.setTripId(tripId);
            place.setName(name);
            place.setCategory("Other");
            if (ll != null) {
                place.setLatitude(ll[0]);
                place.setLongitude(ll[1]);
            }
            place.setDescription("AI planned stop");
            place.setRemarks("ai-generated");
            place.setAddedBy(userId);
            return places.save(place);
        } catch (Exception e) {
            return null;
        }
    }

    private static String norm(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]", "").trim();
    }

    /** Strips backend skeleton suffixes so "Mumbai — journey begins" matches place "Mumbai". */
    private static String stripLegSuffix(String t) {
        String s = t;
        for (String suffix : List.of(" — journey begins", " — arrival", " · on the road")) {
            if (s.endsWith(suffix)) s = s.substring(0, s.length() - suffix.length());
        }
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile("^(.*) · day \\d+$").matcher(s);
        if (m.matches()) s = m.group(1);
        return s.trim();
    }

    private static List<String> strList(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List) {
            for (Object e : (List<?>) o) {
                if (e != null && !e.toString().isBlank()) out.add(e.toString().trim());
            }
        }
        return out;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- helpers ----------

    private void requireMember(Long tripId, Long userId) {
        if (!members.existsByTripIdAndUserId(tripId, userId)) {
            throw new BadRequestException("Not a trip member");
        }
    }

    private String buildUserPrompt(Trip trip, int days, List<Place> known,
                                   Map<String, Object> answers) {
        StringBuilder sb = new StringBuilder();
        sb.append("Trip: ").append(nvl(trip.getTripName(), "Road trip"));
        sb.append(" from ").append(nvl(trip.getStartName(), "?"));
        sb.append(" to ").append(nvl(trip.getDestName(), "?"));
        sb.append(", exactly ").append(days).append(" days.\n");
        sb.append("Known places on route (name | address | lat,lng | visit votes):\n");
        if (known.isEmpty()) {
            sb.append("(none yet — suggest sensible road-trip stops between start and destination)\n");
        } else {
            for (Place p : known) {
                long v = 0;
                for (Object[] row : votes.countByPlaceIdGrouped(p.getId())) {
                    if ("VISIT".equals(row[0])) v = (Long) row[1];
                }
                sb.append("- ").append(p.getName()).append(" | ")
                        .append(nvl(p.getAddress(), "")).append(" | ")
                        .append(p.getLatitude()).append(",").append(p.getLongitude())
                        .append(" | votes=").append(v).append("\n");
            }
        }
        sb.append("Traveler preferences: pace=").append(answers.getOrDefault("pace", "balanced"));
        sb.append(", interests=").append(answers.getOrDefault("interests", List.of()));
        sb.append(", budget=").append(answers.getOrDefault("budget", "medium"));
        sb.append(", stay=").append(answers.getOrDefault("stay", "any"));
        sb.append(", food=").append(answers.getOrDefault("food", "any"));
        sb.append(", mustSee=").append(answers.getOrDefault("mustSee", "")).append("\n");
        sb.append("Prefer voted places. Distribute across exactly ").append(days).append(" days.");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseDays(String text, int days) {
        String clean = text.trim();
        if (clean.startsWith("```")) {
            int first = clean.indexOf('\n');
            int last = clean.lastIndexOf("```");
            clean = (first >= 0 && last > first) ? clean.substring(first, last).trim() : clean;
        }
        try {
            Map<String, Object> root = objectMapper.readValue(clean, Map.class);
            Object d = root.get("days");
            if (!(d instanceof List) || ((List<?>) d).isEmpty()) {
                throw new BadRequestException("AI returned no days — please regenerate");
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : (List<?>) d) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> m = new LinkedHashMap<>((Map<String, Object>) o);
                Object dayNo = m.get("dayNo");
                if (!(dayNo instanceof Number)) continue;
                int day = ((Number) dayNo).intValue();
                if (day < 1 || day > 60) continue;
                m.put("dayNo", day);
                m.putIfAbsent("title", "Day " + day);
                m.putIfAbsent("stops", List.of());
                m.putIfAbsent("stay", "");
                m.putIfAbsent("food", "");
                if (m.get("distanceKm") != null && !(m.get("distanceKm") instanceof Number)) {
                    m.put("distanceKm", null);
                }
                if (m.get("driveMins") != null && !(m.get("driveMins") instanceof Number)) {
                    m.put("driveMins", null);
                }
                out.add(m);
            }
            if (out.isEmpty()) {
                throw new BadRequestException("AI returned no usable days — please regenerate");
            }
            out.sort(Comparator.comparingInt(m -> ((Number) m.get("dayNo")).intValue()));
            return out;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("AI returned an unreadable plan — please regenerate");
        }
    }

    private Map<String, Object> toMap(AiProposal p) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("proposalId", p.getId());
        out.put("provider", p.getProvider());
        out.put("status", p.getStatus());
        try {
            Map<String, Object> payload =
                    objectMapper.readValue(p.getPayload(), Map.class);
            out.put("days", payload.getOrDefault("days", List.of()));
            out.put("questions", objectMapper.readValue(p.getQuestions(), Map.class));
        } catch (Exception e) {
            out.put("days", List.of());
        }
        return out;
    }

    private static String nvl(String s, String fb) {
        return s == null || s.isBlank() ? fb : s;
    }

    private static Double num(Object o) {
        return o instanceof Number ? ((Number) o).doubleValue() : null;
    }

    private static String str(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }
}
