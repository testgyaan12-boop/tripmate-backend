package com.tripmate.notification.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "notifications", indexes = @Index(name = "idx_notif_user", columnList = "user_id,created_at"))
public class Notification extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "title")
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "data", columnDefinition = "TEXT")
    private String data;

    @Column(name = "is_read", nullable = false)
    private Boolean read = false;
}
