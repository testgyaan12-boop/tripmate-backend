package com.tripmate.gallery;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.gallery.entity.GalleryItem;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/trips/{tripId}/gallery")
@RequiredArgsConstructor
public class GalleryController {

    private final GalleryService gallery;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(
            @PathVariable Long tripId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String album,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return ApiResponse.ok(gallery.getGallery(tripId, me(), type, album, q, page, size));
    }

    @GetMapping("/{itemId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable Long tripId, @PathVariable Long itemId) {
        return ApiResponse.ok(gallery.getDetail(itemId, me()));
    }

    @PostMapping
    public ApiResponse<GalleryItem> upload(
            @PathVariable Long tripId,
            @RequestBody Map<String, String> body) {
        Long bytes = null;
        try {
            if (body.get("fileSizeBytes") != null) {
                bytes = Long.parseLong(body.get("fileSizeBytes"));
            }
        } catch (NumberFormatException ignored) {
        }
        return ApiResponse.ok(gallery.upload(
                me(), tripId,
                body.get("cloudinaryUrl"),
                body.get("thumbnailUrl"),
                body.get("fileType"),
                body.get("caption"),
                body.get("locationName"),
                body.get("albumName"),
                bytes));
    }

    @DeleteMapping("/{itemId}")
    public ApiResponse<String> delete(@PathVariable Long tripId, @PathVariable Long itemId) {
        gallery.delete(itemId, me());
        return ApiResponse.ok("Deleted");
    }

    @PostMapping("/{itemId}/like")
    public ApiResponse<Map<String, Object>> toggleLike(@PathVariable Long tripId, @PathVariable Long itemId) {
        boolean liked = gallery.toggleLike(itemId, me());
        return ApiResponse.ok(Map.of("liked", liked));
    }

    @PostMapping("/{itemId}/comments")
    public ApiResponse<Map<String, Object>> addComment(
            @PathVariable Long tripId, @PathVariable Long itemId,
            @RequestBody Map<String, String> body) {
        return ApiResponse.ok(gallery.addComment(itemId, me(), body.get("text")));
    }

    @GetMapping("/albums")
    public ApiResponse<List<Map<String, Object>>> albums(@PathVariable Long tripId) {
        return ApiResponse.ok(gallery.getAlbums(tripId, me()));
    }

    @PutMapping("/{itemId}/album")
    public ApiResponse<String> setAlbum(
            @PathVariable Long tripId, @PathVariable Long itemId,
            @RequestBody Map<String, String> body) {
        gallery.updateAlbum(itemId, me(), body.get("albumName"));
        return ApiResponse.ok("Updated");
    }

    @GetMapping("/contributors")
    public ApiResponse<List<Map<String, Object>>> contributors(@PathVariable Long tripId) {
        return ApiResponse.ok(gallery.getContributors(tripId, me()));
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(@PathVariable Long tripId) {
        return ApiResponse.ok(gallery.getStats(tripId, me()));
    }
}