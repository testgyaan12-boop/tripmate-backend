package com.tripmate.photo;

import com.tripmate.common.exception.BadRequestException;
import com.tripmate.common.exception.ResourceNotFoundException;
import com.tripmate.config.service.ConfigService;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.place.entity.Place;
import com.tripmate.place.repository.PlaceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.util.*;

/**
 * Real place photos from Wikimedia Commons (free, no key): geo-searches
 * images near the place coordinates. Short in-memory cache; failures
 * degrade to an empty list so pages never break.
 */
@Service
@RequiredArgsConstructor
public class PhotoService {

    private static final int CACHE_MAX = 500;
    private static final long CACHE_TTL_MS = 3600_000L;

    private final PlaceRepository places;
    private final TripMemberRepository members;
    private final ConfigService config;

    private record Cached(long at, List<Map<String, Object>> photos) {
    }

    private final Map<Long, Cached> cache = Collections.synchronizedMap(
            new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, Cached> e) {
                    return size() > CACHE_MAX;
                }
            });

    public List<Map<String, Object>> forPlace(Long userId, Long placeId) {
        Place p = places.findById(placeId)
                .orElseThrow(() -> new ResourceNotFoundException("Place not found"));
        if (!members.existsByTripIdAndUserId(p.getTripId(), userId)) {
            throw new BadRequestException("Not a trip member");
        }
        if (p.getLatitude() == null || p.getLongitude() == null) return List.of();
        Cached c = cache.get(placeId);
        if (c != null && System.currentTimeMillis() - c.at() < CACHE_TTL_MS) {
            return c.photos();
        }
        List<Map<String, Object>> photos = fetchCommons(p.getLatitude(), p.getLongitude());
        cache.put(placeId, new Cached(System.currentTimeMillis(), photos));
        return photos;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> fetchCommons(double lat, double lng) {
        int radius = config.getInt("photos.radius.m", 10000);
        int limit = Math.min(20, Math.max(1, config.getInt("photos.limit", 8)));
        String url = "https://commons.wikimedia.org/w/api.php?action=query&format=json"
                + "&generator=geosearch&ggscoord=" + lat + "|" + lng
                + "&ggsradius=" + radius + "&ggsnamespace=6&ggslimit=50"
                + "&prop=imageinfo&iiprop=url|size&iiurlwidth=800";
        HttpHeaders h = new HttpHeaders();
        h.set("User-Agent", "TripMate/1.0 (collaborative-trip-planner)");
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(8000);
        f.setReadTimeout(12000);
        try {
            Map<String, Object> res = new RestTemplate(f)
                    .exchange(url, HttpMethod.GET, new HttpEntity<>(h), Map.class)
                    .getBody();
            if (res == null || !(res.get("query") instanceof Map)) return List.of();
            Object pages = ((Map<String, Object>) res.get("query")).get("pages");
            if (!(pages instanceof Map)) return List.of();
            List<Map<String, Object>> out = new ArrayList<>();
            for (Object o : ((Map<String, Object>) pages).values()) {
                if (!(o instanceof Map)) continue;
                Map<String, Object> pg = (Map<String, Object>) o;
                String title = String.valueOf(pg.get("title"));
                if (!title.matches("(?i).+\\.(jpg|jpeg|png|webp)$")) continue;
                Object ii = pg.get("imageinfo");
                if (!(ii instanceof List) || ((List<?>) ii).isEmpty()) continue;
                Map<String, Object> info = (Map<String, Object>) ((List<?>) ii).get(0);
                Object thumb = info.get("thumburl");
                Object full = info.get("url");
                if (thumb == null || full == null) continue;
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("url", thumb.toString());
                m.put("fullUrl", full.toString());
                m.put("title", title.replaceFirst("(?i)^File:", "").replace('_', ' '));
                m.put("source", "wikimedia");
                out.add(m);
                if (out.size() >= limit) break;
            }
            return out;
        } catch (Exception e) {
            return List.of();
        }
    }
}
