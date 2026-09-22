package com.tripmate.geo;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.config.service.ConfigService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Location search proxy. Prefers OpenRouteService geocoding; falls back to
 * OSM Nominatim (geocoder only, not routing) so the app stays usable before
 * an ORS key is configured. Keys live in app_config, never in code.
 * Driving routes (/api/route) remain ORS-only by decision.
 */
@RestController
@RequiredArgsConstructor
public class GeoController {

    private final ConfigService config;
    private final GeoService geo;
    private final RestTemplate restTemplate = new RestTemplate();

    @GetMapping("/api/geo/search")
    public ApiResponse<?> search(@RequestParam String q) {
        try {
            return ApiResponse.ok(geo.search(q));
        } catch (HttpStatusCodeException e) {
            throw new BadRequestException(
                    "Search failed: " + snippet(e.getResponseBodyAsString()));
        }
    }

    /** ORS driving-car rejects approximated routes over 6,000,000 m. */
    private static final double ORS_MAX_APPROX_M = 6_000_000.0;

    @PostMapping("/api/route")
    public ApiResponse<?> route(@RequestBody RouteReq req) {
        String base = config.get("ors.base.url", "https://api.openrouteservice.org");
        String key = config.get("ors.api.key", "");
        if (key.isBlank() || key.startsWith("REPLACE")) {
            throw new BadRequestException("Routing not configured (ors.api.key missing in app_config)");
        }
        if (req.getCoordinates() == null || req.getCoordinates().size() < 2) {
            throw new BadRequestException("Need >= 2 coordinates [lng,lat]");
        }
        checkChainDistance(req.getCoordinates());
        String url = base + "/v2/directions/driving-car/geojson";
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        h.set("Authorization", key);
        Map<String, Object> body = new HashMap<>();
        body.put("coordinates", req.getCoordinates());
        try {
            ResponseEntity<Object> res = restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, h), Object.class);
            return ApiResponse.ok(res.getBody());
        } catch (HttpStatusCodeException e) {
            throw new BadRequestException(
                    "Routing failed: " + snippet(e.getResponseBodyAsString()));
        }
    }

    /**
     * Pre-flight check so a single far-off pin fails fast with a clear,
     * actionable message instead of ORS's cryptic 6000000 error. Only
     * triggers when ORS would reject anyway (chain over its hard limit).
     */
    private static void checkChainDistance(List<List<Double>> pts) {
        double totalM = 0;
        int badIdx = -1;
        double badLegKm = 0;
        for (int i = 0; i < pts.size(); i++) {
            List<Double> p = pts.get(i);
            if (p == null || p.size() < 2 || p.get(0) == null || p.get(1) == null
                    || !Double.isFinite(p.get(0)) || !Double.isFinite(p.get(1))) {
                throw new BadRequestException(
                        "Invalid coordinate at point #" + i + " — need [lng,lat] numbers");
            }
            if (i > 0) {
                Double legKm = GeoService.distanceKm(
                        pts.get(i - 1).get(1), pts.get(i - 1).get(0),
                        p.get(1), p.get(0));
                if (legKm == null) {
                    throw new BadRequestException("Invalid coordinate at point #" + i);
                }
                totalM += legKm * 1000;
                if (badIdx < 0 || legKm > badLegKm) {
                    badIdx = i;
                    badLegKm = legKm;
                }
            }
        }
        if (totalM > ORS_MAX_APPROX_M) {
            throw new BadRequestException("Route too long (~" + Math.round(totalM / 1000)
                    + " km, provider limit 6,000 km). Point #" + badIdx
                    + " is ~" + Math.round(badLegKm)
                    + " km from the previous point — probably a wrong pin. Fix it and retry.");
        }
    }

    private static String snippet(String s) {        if (s == null || s.isBlank()) return "provider error";
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("\"message\"\\s*:\\s*\"([^\"]+)\"").matcher(s);
        String msg = m.find() ? m.group(1)
                : s.replaceAll("\\s+", " ").trim();
        if (msg.length() > 200) msg = msg.substring(0, 200);
        if (msg.contains("6000000")) {
            msg += " One of the route points is probably far away — remove far-off pins and retry.";
        }
        return msg;
    }

    @Data
    public static class RouteReq {
        private List<List<Double>> coordinates;
    }
}
