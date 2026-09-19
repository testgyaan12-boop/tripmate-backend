package com.tripmate.config.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "app_config", indexes = @Index(name = "idx_config_category", columnList = "category"))
public class AppConfig extends BaseEntity {

    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey;

    @Column(name = "config_value", nullable = false, columnDefinition = "TEXT")
    private String configValue;

    @Column(name = "category", nullable = false, length = 30)
    private String category;

    @Column(name = "is_secret", nullable = false)
    private Boolean secret = true;

    @Column(name = "description")
    private String description;
}
