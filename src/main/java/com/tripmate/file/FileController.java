package com.tripmate.file;

import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.utils.ObjectUtils;
import com.tripmate.common.dto.ApiResponse;
import com.tripmate.config.service.ConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.*;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final ConfigService config;
    private final Cloudinary cloudinary;

    @PostMapping("/upload")
    public ApiResponse<?> upload(@RequestParam("file") MultipartFile file) throws Exception {
        long max = Long.parseLong(config.get("storage.max-file-size", "20971520"));
        if (file.getSize() > max) {
            return ApiResponse.fail("File too large (max " + max + " bytes)");
        }
        Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "tripmate-uploads");
        Files.createDirectories(dir);
        String name = UUID.randomUUID() + "-" + file.getOriginalFilename();
        Files.copy(file.getInputStream(), dir.resolve(name), StandardCopyOption.REPLACE_EXISTING);
        return ApiResponse.ok(Map.of("url", "/api/files/" + name, "name", name));
    }

    @PostMapping("/upload-cloudinary")
    public ApiResponse<?> uploadCloudinary(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "folder", defaultValue = "tripmate") String folder) throws Exception {
        long max = Long.parseLong(config.get("storage.max-file-size", "52428800"));
        if (file.getSize() > max) {
            return ApiResponse.fail("File too large (max 50 MB)");
        }
        String contentType = file.getContentType() != null ? file.getContentType() : "";
        String resourceType = contentType.startsWith("video/") ? "video" : "image";
        String publicId = UUID.randomUUID().toString();

        // Optimized variants, generated synchronously so URLs are ready immediately:
        // photos -> 1920px bounded copy (detail) + 400px thumb (grid),
        // videos -> auto-transcoded copy (2 Mbps cap) + 400px jpg poster.
        // f_auto/q_auto = modern format delivery (WebP/AVIF) at zero extra storage.
        java.util.Map<String, Object> params = new java.util.HashMap<>(ObjectUtils.asMap(
                "folder", folder,
                "public_id", publicId,
                "resource_type", resourceType));
        if ("image".equals(resourceType)) {
            params.put("eager", java.util.List.of(
                    ObjectUtils.asMap("width", 1920, "crop", "limit",
                            "quality", "auto", "fetch_format", "auto"),
                    ObjectUtils.asMap("width", 400, "crop", "limit",
                            "quality", "auto", "fetch_format", "auto")));
        } else {
            params.put("eager", java.util.List.of(
                    ObjectUtils.asMap("width", 1920, "crop", "limit",
                            "quality", "auto", "video_codec", "auto",
                            "bit_rate", "2M", "fetch_format", "auto"),
                    ObjectUtils.asMap("format", "jpg", "width", 400, "crop", "limit")));
        }

        Uploader uploader = cloudinary.uploader();
        Map<String, Object> result = uploader.upload(file.getBytes(), params);

        String url = (String) result.get("secure_url");
        String thumbUrl = (String) result.get("thumbnail_url");
        if (thumbUrl == null) thumbUrl = url;
        // Prefer the generated variants: photos -> [1920, thumb],
        // videos -> [transcoded, jpg poster]. Fall back to originals.
        Object eagerObj = result.get("eager");
        if (eagerObj instanceof java.util.List<?> eagerList && !eagerList.isEmpty()) {
            if ("image".equals(resourceType) && eagerList.size() >= 2) {
                url = eagerUrl(eagerList.get(0), url);
                thumbUrl = eagerUrl(eagerList.get(1), thumbUrl);
            } else if ("video".equals(resourceType)) {
                url = eagerUrl(eagerList.get(0), url);
                for (Object e : eagerList) {
                    if (e instanceof java.util.Map<?, ?> m
                            && m.get("secure_url") instanceof String s
                            && (s.contains(".jpg") || s.contains("f_jpg"))) {
                        thumbUrl = s;
                        break;
                    }
                }
            }
        }

        return ApiResponse.ok(Map.of(
                "url", url,
                "thumbnailUrl", thumbUrl,
                "publicId", result.get("public_id"),
                "format", result.get("format"),
                "resourceType", resourceType,
                "bytes", result.get("bytes")
        ));
    }

    private static String eagerUrl(Object eagerEntry, String fallback) {
        if (eagerEntry instanceof java.util.Map<?, ?> m
                && m.get("secure_url") instanceof String s && !s.isBlank()) {
            return s;
        }
        return fallback;
    }

    @GetMapping("/{name:.+}")
    public ResponseEntity<Resource> serve(@PathVariable String name) throws Exception {
        Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "tripmate-uploads");
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir) || !Files.exists(file)) {
            return ResponseEntity.notFound().build();
        }
        String contentType = Files.probeContentType(file);
        if (contentType == null) contentType = "application/octet-stream";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(new FileSystemResource(file));
    }
}
