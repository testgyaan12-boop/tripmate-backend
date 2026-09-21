package com.tripmate.gallery.repository;

import com.tripmate.gallery.entity.GalleryPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GalleryPermissionRepository extends JpaRepository<GalleryPermission, Long> {

    Optional<GalleryPermission> findByTripIdAndIsDeleted(Long tripId, Integer isDeleted);
}
