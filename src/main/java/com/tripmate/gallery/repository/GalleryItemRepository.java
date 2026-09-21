package com.tripmate.gallery.repository;

import com.tripmate.gallery.entity.GalleryItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface GalleryItemRepository extends JpaRepository<GalleryItem, Long> {

    List<GalleryItem> findByTripIdAndIsDeletedOrderByCreatedAtDesc(Long tripId, Integer isDeleted);

    Page<GalleryItem> findByTripIdAndIsDeletedOrderByCreatedAtDesc(Long tripId, Integer isDeleted, Pageable pageable);

    List<GalleryItem> findByTripIdAndAlbumNameAndIsDeletedOrderByCreatedAtDesc(Long tripId, String albumName, Integer isDeleted);

    List<GalleryItem> findByTripIdAndFileTypeAndIsDeletedOrderByCreatedAtDesc(Long tripId, String fileType, Integer isDeleted);

    @Query("SELECT g.albumName, COUNT(g) FROM GalleryItem g WHERE g.tripId = :tripId AND g.albumName IS NOT NULL AND g.isDeleted = 0 AND g.isActive = 1 GROUP BY g.albumName ORDER BY MAX(g.createdAt) DESC")
    List<Object[]> countByAlbum(@Param("tripId") Long tripId);

    @Query("SELECT g.userId, COUNT(g) FROM GalleryItem g WHERE g.tripId = :tripId AND g.isDeleted = 0 AND g.isActive = 1 GROUP BY g.userId ORDER BY COUNT(g) DESC")
    List<Object[]> countByUser(@Param("tripId") Long tripId);

    @Query("SELECT COALESCE(SUM(CASE WHEN g.fileType = 'PHOTO' THEN 1 ELSE 0 END), 0), COALESCE(SUM(CASE WHEN g.fileType = 'VIDEO' THEN 1 ELSE 0 END), 0), COUNT(g) FROM GalleryItem g WHERE g.tripId = :tripId AND g.isDeleted = 0 AND g.isActive = 1")
    Object[] stats(@Param("tripId") Long tripId);

    @Query("SELECT g FROM GalleryItem g WHERE g.tripId = :tripId AND g.isDeleted = 0 AND g.isActive = 1 AND (LOWER(g.caption) LIKE LOWER(CONCAT('%',:q,'%')) OR LOWER(g.locationName) LIKE LOWER(CONCAT('%',:q,'%'))) ORDER BY g.createdAt DESC")
    List<GalleryItem> search(@Param("tripId") Long tripId, @Param("q") String query);
}
