package com.tripmate.gallery.repository;

import com.tripmate.gallery.entity.GalleryLike;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GalleryLikeRepository extends JpaRepository<GalleryLike, Long> {

    Optional<GalleryLike> findByGalleryIdAndUserIdAndIsDeleted(Long galleryId, Long userId, Integer isDeleted);

    boolean existsByGalleryIdAndUserIdAndIsDeleted(Long galleryId, Long userId, Integer isDeleted);

    long countByGalleryIdAndIsDeleted(Long galleryId, Integer isDeleted);
}
