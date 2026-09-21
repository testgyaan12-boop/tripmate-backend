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
@Table(name = "gallery_like", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"gallery_id", "user_id"})
})
public class GalleryLike extends BaseEntity {

    @Column(name = "gallery_id", nullable = false)
    private Long galleryId;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
