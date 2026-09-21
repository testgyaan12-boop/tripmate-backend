package com.tripmate.gallery.repository;

import com.tripmate.gallery.entity.GalleryComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GalleryCommentRepository extends JpaRepository<GalleryComment, Long> {

    List<GalleryComment> findByGalleryIdAndIsDeletedOrderByCreatedAtAsc(Long galleryId, Integer isDeleted);
}
