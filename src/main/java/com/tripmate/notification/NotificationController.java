package com.tripmate.notification;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.notification.entity.Notification;
import com.tripmate.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationRepository repo;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping
    public ApiResponse<List<Notification>> list() {
        return ApiResponse.ok(repo.findByUserIdOrderByCreatedAtDesc(me()));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<?> read(@PathVariable Long id) {
        repo.findById(id).ifPresent(n -> {
            if (n.getUserId().equals(me())) {
                n.setRead(true);
                repo.save(n);
            }
        });
        return ApiResponse.ok("Read", null);
    }
}
