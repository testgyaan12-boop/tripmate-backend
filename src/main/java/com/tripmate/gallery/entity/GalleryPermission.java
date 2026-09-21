package com.tripmate.gallery.entity;

import com.tripmate.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Getter
@Setter
@Entity
@SQLRestriction("is_deleted = 0 AND is_active = 1")
@Table(name = "gallery_permission")
public class GalleryPermission extends BaseEntity {

    @Column(name = "trip_id", nullable = false, unique = true)
    private Long tripId;

    @Column(name = "visibility", nullable = false, length = 20)
    private String visibility = "MEMBERS_ONLY";
}
