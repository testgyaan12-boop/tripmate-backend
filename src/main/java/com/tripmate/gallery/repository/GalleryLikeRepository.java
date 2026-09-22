package com.tripmate.gallery.repository;

import com.tripmate.gallery.entity.GalleryLike;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface GalleryLikeRepository extends JpaRepository<GalleryLike, Long> {

    Optional<GalleryLike> findByGalleryIdAndUserIdAndIsDeleted(Long galleryId, Long userId, Integer isDeleted);

    boolean existsByGalleryIdAndUserIdAndIsDeleted(Long galleryId, Long userId, Integer isDeleted);

    long countByGalleryIdAndIsDeleted(Long galleryId, Integer isDeleted);

    @Query("SELECT l.galleryId FROM GalleryLike l WHERE l.galleryId IN :ids AND l.userId = :userId")
    List<Long> findLikedGalleryIds(@Param("ids") List<Long> ids, @Param("userId") Long userId);
}
