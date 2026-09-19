package com.tripmate.common.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Base audit entity. Every table extends this.
 * Soft-delete rule: rows are alive only when isDeleted=0 AND isActive=1.
 * PG mapping: TINYINT -> SMALLINT, DATE/TIMESTAMP -> TIMESTAMPTZ.
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("is_deleted = 0 AND is_active = 1")
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "is_deleted", nullable = false)
    private Integer isDeleted = 0;

    @Column(name = "remarks", length = 150)
    private String remarks;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by")
    private Long createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_active", nullable = false)
    private Short isActive = 1;

    @Transient
    public boolean isAlive() {
        return Integer.valueOf(0).equals(isDeleted) && Short.valueOf((short) 1).equals(isActive);
    }

    public void softDelete() {
        this.isDeleted = 1;
        this.isActive = 0;
    }
}
