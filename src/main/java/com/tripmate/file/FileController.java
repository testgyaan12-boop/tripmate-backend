package com.tripmate.file;

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
