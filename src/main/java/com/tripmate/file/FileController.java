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

import com.cloudinary.Transformation;
import com.cloudinary.Uploader;
import java.util.*;

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
	public ApiResponse<?> uploadCloudinary(@RequestParam("file") MultipartFile file,
			@RequestParam(value = "folder", defaultValue = "tripmate") String folder) throws Exception {

		long max = Long.parseLong(config.get("storage.max-file-size", "52428800"));

		if (file.isEmpty()) {
			return ApiResponse.fail("Please select a file");
		}

		if (file.getSize() > max) {
			return ApiResponse.fail("File too large (max 50 MB)");
		}

		String contentType = file.getContentType() != null ? file.getContentType() : "";

		String resourceType = contentType.startsWith("video/") ? "video" : "image";

		String publicId = UUID.randomUUID().toString();

		Map<String, Object> params = new HashMap<>();
		params.put("folder", folder);
		params.put("public_id", publicId);
		params.put("resource_type", resourceType);

		// Eager transformations
		List<Transformation> eager = new ArrayList<>();

		if ("image".equals(resourceType)) {

			// Detail image - max width 1920px
			eager.add(new Transformation().width(1920).crop("limit").quality("auto"));

			// Thumbnail - max width 400px
			eager.add(new Transformation().width(400).crop("limit").quality("auto"));

		} else {

			// Optimized video
			eager.add(new Transformation().width(1920).crop("limit").quality("auto").videoCodec("auto").bitRate("2m")
					.fetchFormat("mp4"));

			// Video poster thumbnail
			eager.add(new Transformation().width(400).crop("limit").fetchFormat("jpg"));
		}

		params.put("eager", eager);

		Uploader uploader = cloudinary.uploader();

		Map<String, Object> result = uploader.upload(file.getBytes(), params);

		String url = (String) result.get("secure_url");
		String thumbUrl = (String) result.get("thumbnail_url");

		if (thumbUrl == null) {
			thumbUrl = url;
		}

		// Read generated eager URLs
		Object eagerObj = result.get("eager");

		if (eagerObj instanceof List<?> eagerList && !eagerList.isEmpty()) {

			if ("image".equals(resourceType) && eagerList.size() >= 2) {

				url = eagerUrl(eagerList.get(0), url);
				thumbUrl = eagerUrl(eagerList.get(1), thumbUrl);

			} else if ("video".equals(resourceType)) {

				url = eagerUrl(eagerList.get(0), url);

				for (Object e : eagerList) {

					if (e instanceof Map<?, ?> m && m.get("secure_url") instanceof String s
							&& (s.contains(".jpg") || s.contains("f_jpg"))) {

						thumbUrl = s;
						break;
					}
				}
			}
		}

		return ApiResponse.ok(Map.of("url", url, "thumbnailUrl", thumbUrl, "publicId", result.get("public_id"),
				"format", result.get("format"), "resourceType", resourceType, "bytes", result.get("bytes")));
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
