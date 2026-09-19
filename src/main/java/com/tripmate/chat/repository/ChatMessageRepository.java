package com.tripmate.chat.repository;

import com.tripmate.chat.entity.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findByTripIdOrderByCreatedAtAsc(Long tripId);
}
