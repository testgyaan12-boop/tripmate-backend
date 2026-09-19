package com.tripmate.chat;

import com.tripmate.chat.entity.ChatMessage;
import com.tripmate.chat.repository.ChatMessageRepository;
import com.tripmate.common.dto.ApiResponse;
import com.tripmate.common.exception.BadRequestException;
import com.tripmate.member.repository.TripMemberRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ChatRestController {

    private final ChatMessageRepository chat;
    private final TripMemberRepository members;
    private final SimpMessagingTemplate template;

    private Long me() {
        return Long.parseLong(SecurityContextHolder.getContext().getAuthentication().getName());
    }

    @GetMapping("/api/trips/{tripId}/chat/messages")
    public ApiResponse<List<ChatMessage>> history(@PathVariable Long tripId) {
        if (!members.existsByTripIdAndUserId(tripId, me())) throw new BadRequestException("Not a trip member");
        return ApiResponse.ok(chat.findByTripIdOrderByCreatedAtAsc(tripId));
    }

    /** REST fallback (used when the WS is unavailable, e.g. image/location sends). */
    @PostMapping("/api/trips/{tripId}/chat/messages")
    public ApiResponse<ChatMessage> sendRest(@PathVariable Long tripId, @RequestBody SendReq req) {
        if (!members.existsByTripIdAndUserId(tripId, me())) throw new BadRequestException("Not a trip member");
        ChatMessage c = new ChatMessage();
        c.setTripId(tripId);
        c.setSenderId(me());
        c.setContent(req.getContent());
        c.setImageUrl(req.getImageUrl());
        c.setSharedLat(req.getSharedLat());
        c.setSharedLng(req.getSharedLng());
        ChatMessage saved = chat.save(c);
        template.convertAndSend("/topic/trip." + tripId, saved);
        return ApiResponse.ok("Sent", saved);
    }

    @Data
    public static class SendReq {
        private String content;
        private String imageUrl;
        private Double sharedLat;
        private Double sharedLng;
    }

    @Controller
    @RequiredArgsConstructor
    public static class ChatWsController {
        private final ChatMessageRepository chat;
        private final TripMemberRepository members;
        private final SimpMessagingTemplate template;

        @MessageMapping("/trip.{tripId}.send")
        public void send(@DestinationVariable Long tripId, WsMsg msg) {
            if (msg.getSenderId() == null) return;
            if (!members.existsByTripIdAndUserId(tripId, msg.getSenderId())) return;
            ChatMessage c = new ChatMessage();
            c.setTripId(tripId);
            c.setSenderId(msg.getSenderId());
            c.setContent(msg.getContent());
            c.setImageUrl(msg.getImageUrl());
            c.setSharedLat(msg.getSharedLat());
            c.setSharedLng(msg.getSharedLng());
            ChatMessage saved = chat.save(c);
            template.convertAndSend("/topic/trip." + tripId, saved);
        }

        @Data
        public static class WsMsg {
            private Long senderId;
            private String content;
            private String imageUrl;
            private Double sharedLat;
            private Double sharedLng;
        }
    }
}
