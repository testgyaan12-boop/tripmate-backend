package com.tripmate.user.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_alive", columnList = "is_deleted,is_active")
})
public class User extends BaseEntity {

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "profile_image")
    private String profileImage;

    @Column(name = "mobile", length = 20)
    private String mobile;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "travel_style", length = 30)
    private String travelStyle;

    @Column(name = "favorite_places", columnDefinition = "TEXT")
    private String favoritePlaces;

    @Column(name = "vehicle", length = 30)
    private String vehicle;

    @Column(name = "budget_type", length = 20)
    private String budgetType;

    @Column(name = "google_sub", unique = true)
    private String googleSub;

    @Column(name = "refresh_token_hash", length = 500)
    private String refreshTokenHash;
}
