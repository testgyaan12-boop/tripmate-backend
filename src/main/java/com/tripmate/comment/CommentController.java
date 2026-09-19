package com.tripmate.comment;

import com.tripmate.common.dto.ApiResponse;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.common.exception.ResourceNotFoundException;
import com.tripmate.comment.entity.Comment;
import com.tripmate.comment.repository.CommentRepository;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.place.entity.Place;
import com.tripmate.place.repository.PlaceRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class CommentController {

    private final CommentRepository comments;
    private final PlaceRepository places;
    private final TripMemberRepository members;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping("/api/places/{id}/comments")
    public ApiResponse<List<Comment>> list(@PathVariable Long id) {
        Place p = places.findById(id).orElseThrow(() -> new ResourceNotFoundException("Place not found"));
        if (!members.existsByTripIdAndUserId(p.getTripId(), me())) throw new BadRequestException("Not a trip member");
        return ApiResponse.ok(comments.findByPlaceIdOrderByCreatedAtAsc(id));
    }

    @PostMapping("/api/places/{id}/comments")
    public ApiResponse<Comment> add(@PathVariable Long id, @Valid @RequestBody MsgReq req) {
        Place p = places.findById(id).orElseThrow(() -> new ResourceNotFoundException("Place not found"));
        if (!members.existsByTripIdAndUserId(p.getTripId(), me())) throw new BadRequestException("Not a trip member");
        Comment c = new Comment();
        c.setPlaceId(id);
        c.setUserId(me());
        c.setMessage(req.getMessage());
        return ApiResponse.ok("Added", comments.save(c));
    }

    @Data
    public static class MsgReq {
        @NotBlank private String message;
    }
}
