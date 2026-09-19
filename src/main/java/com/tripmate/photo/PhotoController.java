package com.tripmate.photo;

import com.tripmate.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/places/{placeId}/photos")
@RequiredArgsConstructor
public class PhotoController {

    private final PhotoService photos;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@PathVariable Long placeId) {
        return ApiResponse.ok(photos.forPlace(me(), placeId));
    }
}
