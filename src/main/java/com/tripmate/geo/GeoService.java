package com.tripmate.geo;

import com.tripmate.config.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Location search: ORS geocoding first, OSM Nominatim fallback.
 * Shared by the REST proxy and server-side flows (AI place verification).
 */
@Service
@RequiredArgsConstructor
public class GeoService {

    private final ConfigService config;
    private final RestTemplate restTemplate = new RestTemplate();

    public Object search(String q) {
        String base = config.get("ors.base.url", "https://api.openrouteservice.org");
        String key = config.get("ors.api.key", "");
        if (!key.isBlank() && !key.startsWith("REPLACE")) {
            String url = base + "/geocode/search?api_key=" + key + "&text=" + q + "&size=5";
            return restTemplate.getForObject(url, Object.class);
        }
        String url = "https://nominatim.openstreetmap.org/search?q="
                + URLEncoder.encode(q, StandardCharsets.UTF_8)
                + "&format=geojson&limit=5&addressdetails=1";
        HttpHeaders h = new HttpHeaders();
        h.set("User-Agent", "TripMate/1.0 (collaborative-trip-planner)");
        h.setAccept(List.of(MediaType.APPLICATION_JSON));
        ResponseEntity<Object> res = restTemplate.exchange(
                url, HttpMethod.GET, new HttpEntity<>(h), Object.class);
        return res.getBody();
    }

    /**
     * Geocode-verify a name to [lat, lng], preferring hits near the reference
     * point (trip start). Rejects other-continent mismatches outright instead
     * of pinning them. Tries a simplified variant on miss.
     */
    public Double[] verifyCoords(String name, Double refLat, Double refLng, double maxKm) {
        Double[] hit = nearestWithin(name, refLat, refLng, maxKm);
        if (hit != null) return hit;
        String simple = name.split("[–\\-,]")[0].trim();
        if (!simple.equalsIgnoreCase(name) && simple.length() >= 3) {
            return nearestWithin(simple, refLat, refLng, maxKm);
        }
        return null;
    }

    /**
     * First geocode hit within maxKm of the reference (or the first hit when
     * no reference is known). Null when nothing acceptable found.
     */
    @SuppressWarnings("unchecked")
    public Double[] nearestWithin(String q, Double refLat, Double refLng, double maxKm) {
        try {
            Object raw = search(q);
            if (!(raw instanceof Map)) return null;
            Object feats = ((Map<String, Object>) raw).get("features");
            if (!(feats instanceof List) || ((List<?>) feats).isEmpty()) return null;
            Double[] first = null;
            for (Object o : (List<?>) feats) {
                Double[] ll = coordsOf(o);
                if (ll == null) continue;
                if (first == null) first = ll;
                if (refLat != null && refLng != null
                        && haversineKm(refLat, refLng, ll[0], ll[1]) <= maxKm) {
                    return ll;
                }
            }
            return (refLat == null || refLng == null) ? first : null;
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Double[] coordsOf(Object feature) {
        try {
            Map<String, Object> f = (Map<String, Object>) feature;
            Map<String, Object> geometry = (Map<String, Object>) f.get("geometry");
            List<?> coords = (List<?>) geometry.get("coordinates");
            double lng = ((Number) coords.get(0)).doubleValue();
            double lat = ((Number) coords.get(1)).doubleValue();
            if (!Double.isFinite(lat) || !Double.isFinite(lng)) return null;
            return new Double[]{lat, lng};
        } catch (Exception e) {
            return null;
        }
    }

    private static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * r * Math.asin(Math.sqrt(a));
    }

    /**
     * Public great-circle distance in km. Null-safe: returns null when any
     * coordinate is missing or non-finite.
     */
    public static Double distanceKm(Double lat1, Double lng1, Double lat2, Double lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) return null;
        if (!Double.isFinite(lat1) || !Double.isFinite(lng1)
                || !Double.isFinite(lat2) || !Double.isFinite(lng2)) return null;
        return haversineKm(lat1, lng1, lat2, lng2);
    }
}
