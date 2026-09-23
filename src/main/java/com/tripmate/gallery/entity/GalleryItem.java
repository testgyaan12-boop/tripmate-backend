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
@Table(name = "trip_gallery", indexes = {
        @Index(name = "idx_gallery_trip", columnList = "trip_id,is_deleted,is_active"),
        @Index(name = "idx_gallery_user", columnList = "user_id"),
        @Index(name = "idx_gallery_album", columnList = "trip_id,album_name")
})
public class GalleryItem extends BaseEntity {

    @Column(name = "trip_id", nullable = false)
    private Long tripId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "cloudinary_url", nullable = false, columnDefinition = "TEXT")
    private String cloudinaryUrl;

    @Column(name = "thumbnail_url", columnDefinition = "TEXT")
    private String thumbnailUrl;

    @Column(name = "file_type", nullable = false, length = 20)
    private String fileType = "PHOTO";

    @Column(name = "caption", length = 500)
    private String caption;

    @Column(name = "location_name", length = 200)
    private String locationName;

    @Column(name = "album_name", length = 100)
    private String albumName;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount = 0;

    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes = 0L;
}
