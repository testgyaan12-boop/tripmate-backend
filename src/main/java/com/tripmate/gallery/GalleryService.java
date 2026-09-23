package com.tripmate.gallery;

import com.tripmate.common.exception.BadRequestException;
import com.tripmate.common.exception.ResourceNotFoundException;
import com.tripmate.gallery.entity.GalleryComment;
import com.tripmate.gallery.entity.GalleryItem;
import com.tripmate.gallery.entity.GalleryLike;
import com.tripmate.gallery.entity.GalleryPermission;
import com.tripmate.gallery.repository.*;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.user.entity.User;
import com.tripmate.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GalleryService {

    private final GalleryItemRepository items;
    private final GalleryLikeRepository likes;
    private final GalleryCommentRepository comments;
    private final GalleryPermissionRepository permissions;
    private final TripMemberRepository members;
    private final UserRepository users;
    private final com.tripmate.billing.SubscriptionService subs;

    private void requireMember(Long tripId, Long userId) {
        if (!members.existsByTripIdAndUserId(tripId, userId)) {
            throw new BadRequestException("Not a trip member");
        }
    }

    private Map<String, Object> user简报(Long userId) {
        User u = users.findById(userId).orElse(null);
        if (u == null) return Map.of("id", userId, "name", "Unknown");
        return Map.of("id", u.getId(), "name",
                u.getName() != null ? u.getName() : "Unknown",
                "email", u.getEmail() != null ? u.getEmail() : "");
    }

    // ── Gallery CRUD ──────────────────────────────────────────────

    @Transactional
    public GalleryItem upload(Long userId, Long tripId, String url, String thumb,
                               String fileType, String caption, String location, String album,
                               Long fileSizeBytes) {
        requireMember(tripId, userId);
        long incoming = fileSizeBytes != null && fileSizeBytes > 0 ? fileSizeBytes : 0;
        int quotaMb = subs.intLimit(userId, "STORAGE_MB", 500);
        long quotaBytes = (long) quotaMb * 1024 * 1024;
        long used = subs.storageUsedBytes(userId);
        if (used + incoming > quotaBytes) {
            throw new BadRequestException("Gallery storage full (" + used / 1024 / 1024
                    + " MB of " + quotaMb + " MB used). Upgrade to Pro for more storage.");
        }
        GalleryItem g = new GalleryItem();
        g.setTripId(tripId);
        g.setUserId(userId);
        g.setCloudinaryUrl(url);
        g.setThumbnailUrl(thumb != null ? thumb : url);
        g.setFileType(fileType != null ? fileType : "PHOTO");
        g.setCaption(caption);
        g.setLocationName(location);
        g.setAlbumName(album);
        g.setLikeCount(0);
        g.setFileSizeBytes(incoming);
        return items.save(g);
    }

    public Map<String, Object> getGallery(Long tripId, Long userId, String type, String album, String q, int page, int size) {
        requireMember(tripId, userId);
        List<GalleryItem> list;
        if (q != null && !q.isBlank()) {
            list = items.search(tripId, q);
        } else if (album != null && !album.isBlank()) {
            list = items.findByTripIdAndAlbumNameAndIsDeletedOrderByCreatedAtDesc(tripId, album, 0);
        } else if (type != null && !type.isBlank()) {
            list = items.findByTripIdAndFileTypeAndIsDeletedOrderByCreatedAtDesc(tripId, type, 0);
        } else {
            list = items.findByTripIdAndIsDeletedOrderByCreatedAtDesc(tripId, 0);
        }

        int start = page * size;
        int end = Math.min(start + size, list.size());
        List<GalleryItem> pageItems = start < list.size() ? list.subList(start, end) : List.of();

        Set<Long> myLikedIds = new HashSet<>();
        if (!pageItems.isEmpty()) {
            List<Long> ids = pageItems.stream().map(GalleryItem::getId).toList();
            myLikedIds = new HashSet<>(likes.findLikedGalleryIds(ids, userId));
        }
        final Set<Long> finalLikedIds = myLikedIds;

        List<Map<String, Object>> data = pageItems.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", g.getId());
            m.put("cloudinaryUrl", g.getCloudinaryUrl());
            m.put("thumbnailUrl", g.getThumbnailUrl());
            m.put("fileType", g.getFileType());
            m.put("caption", g.getCaption());
            m.put("locationName", g.getLocationName());
            m.put("albumName", g.getAlbumName());
            m.put("likeCount", g.getLikeCount());
            m.put("likedByMe", finalLikedIds.contains(g.getId()));
            m.put("user", user简报(g.getUserId()));
            m.put("createdAt", g.getCreatedAt());
            return m;
        }).toList();

        long photoCount = items.countPhotos(tripId);
        long videoCount = items.countVideos(tripId);
        long totalCount = items.countAll(tripId);
        Map<String, Object> meta = Map.of(
                "photoCount", photoCount,
                "videoCount", videoCount,
                "totalCount", totalCount,
                "hasMore", end < list.size()
        );

        return Map.of("items", data, "meta", meta);
    }

    public Map<String, Object> getDetail(Long itemId, Long userId) {
        GalleryItem g = items.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        requireMember(g.getTripId(), userId);
        boolean liked = likes.existsByGalleryIdAndUserIdAndIsDeleted(itemId, userId, 0);
        List<Map<String, Object>> cmts = comments.findByGalleryIdAndIsDeletedOrderByCreatedAtAsc(itemId, 0)
                .stream().map(c -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", c.getId());
                    m.put("text", c.getCommentText());
                    m.put("user", user简报(c.getUserId()));
                    m.put("createdAt", c.getCreatedAt());
                    return m;
                }).toList();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", g.getId());
        m.put("cloudinaryUrl", g.getCloudinaryUrl());
        m.put("thumbnailUrl", g.getThumbnailUrl());
        m.put("fileType", g.getFileType());
        m.put("caption", g.getCaption());
        m.put("locationName", g.getLocationName());
        m.put("albumName", g.getAlbumName());
        m.put("likeCount", g.getLikeCount());
        m.put("likedByMe", liked);
        m.put("user", user简报(g.getUserId()));
        m.put("createdAt", g.getCreatedAt());
        m.put("comments", cmts);
        return m;
    }

    @Transactional
    public void delete(Long itemId, Long userId) {
        GalleryItem g = items.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        if (!g.getUserId().equals(userId)) throw new BadRequestException("Not your photo");
        g.softDelete();
        items.save(g);
    }

    // ── Likes ─────────────────────────────────────────────────────

    @Transactional
    public boolean toggleLike(Long itemId, Long userId) {
        GalleryItem g = items.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        requireMember(g.getTripId(), userId);
        Optional<GalleryLike> existing = likes.findByGalleryIdAndUserIdAndIsDeleted(itemId, userId, 0);
        if (existing.isPresent()) {
            existing.get().softDelete();
            likes.save(existing.get());
            g.setLikeCount(Math.max(0, g.getLikeCount() - 1));
            items.save(g);
            return false;
        } else {
            GalleryLike like = new GalleryLike();
            like.setGalleryId(itemId);
            like.setUserId(userId);
            likes.save(like);
            g.setLikeCount(g.getLikeCount() + 1);
            items.save(g);
            return true;
        }
    }

    // ── Comments ──────────────────────────────────────────────────

    @Transactional
    public Map<String, Object> addComment(Long itemId, Long userId, String text) {
        GalleryItem g = items.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        requireMember(g.getTripId(), userId);
        if (text == null || text.isBlank()) throw new BadRequestException("Comment text required");
        GalleryComment c = new GalleryComment();
        c.setGalleryId(itemId);
        c.setUserId(userId);
        c.setCommentText(text.trim());
        GalleryComment saved = comments.save(c);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", saved.getId());
        m.put("text", saved.getCommentText());
        m.put("user", user简报(userId));
        m.put("createdAt", saved.getCreatedAt());
        return m;
    }

    // ── Albums ────────────────────────────────────────────────────

    public List<Map<String, Object>> getAlbums(Long tripId, Long userId) {
        requireMember(tripId, userId);
        List<Object[]> raw = items.countByAlbum(tripId);
        return raw.stream().map(r -> Map.<String, Object>of(
                "name", r[0] != null ? r[0] : "Uncategorized",
                "count", ((Number) r[1]).longValue()
        )).toList();
    }

    @Transactional
    public void updateAlbum(Long itemId, Long userId, String albumName) {
        GalleryItem g = items.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Photo not found"));
        requireMember(g.getTripId(), userId);
        g.setAlbumName(albumName);
        items.save(g);
    }

    // ── Contributors ──────────────────────────────────────────────

    public List<Map<String, Object>> getContributors(Long tripId, Long userId) {
        requireMember(tripId, userId);
        List<Object[]> raw = items.countByUser(tripId);
        return raw.stream().map(r -> {
            Map<String, Object> m = new LinkedHashMap<>(user简报((Long) r[0]));
            m.put("photoCount", ((Number) r[1]).longValue());
            return m;
        }).toList();
    }

    // ── Stats ─────────────────────────────────────────────────────

    public Map<String, Object> getStats(Long tripId, Long userId) {
        requireMember(tripId, userId);
        return Map.of(
                "photoCount", items.countPhotos(tripId),
                "videoCount", items.countVideos(tripId),
                "totalCount", items.countAll(tripId)
        );
    }
}