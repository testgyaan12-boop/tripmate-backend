package com.tripmate.trip.service;

import com.tripmate.common.exception.BadRequestException;
import com.tripmate.member.entity.TripMember;
import com.tripmate.member.repository.TripMemberRepository;
import com.tripmate.notification.entity.Notification;
import com.tripmate.notification.repository.NotificationRepository;
import com.tripmate.trip.entity.Trip;
import com.tripmate.trip.entity.TripInvite;
import com.tripmate.trip.repository.TripInviteRepository;
import com.tripmate.trip.repository.TripRepository;
import com.tripmate.user.entity.User;
import com.tripmate.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InviteService {

    private final TripInviteRepository invites;
    private final TripMemberRepository members;
    private final TripRepository trips;
    private final UserRepository users;
    private final NotificationRepository notifications;
    private final ActivityService activity;

    /** All connected users (shared trip history) with profile info. */
    public List<Map<String, Object>> getConnections(Long userId) {
        List<Long> ids = invites.findConnectionUserIds(userId);
        if (ids.isEmpty()) return List.of();
        return users.findAllById(ids).stream().map(u -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("userId", u.getId());
            out.put("name", u.getName());
            out.put("email", u.getEmail());
            out.put("profileImage", u.getProfileImage());
            return out;
        }).toList();
    }

    /** Send an in-app invite to a connected user. */
    public void sendInvite(Long tripId, Long inviterId, Long inviteeId) {
        if (inviterId.equals(inviteeId))
            throw new BadRequestException("Cannot invite yourself");
        if (members.existsByTripIdAndUserId(tripId, inviteeId))
            throw new BadRequestException("User is already a member");
        if (invites.existsByTripIdAndInviteeUserIdAndStatus(tripId, inviteeId, "PENDING"))
            throw new BadRequestException("Invite already pending");
        Trip t = trips.findById(tripId)
                .orElseThrow(() -> new BadRequestException("Trip not found"));

        TripInvite inv = new TripInvite();
        inv.setTripId(tripId);
        inv.setInviterUserId(inviterId);
        inv.setInviteeUserId(inviteeId);
        inv.setStatus("PENDING");
        invites.save(inv);

        User inviter = users.findById(inviterId).orElse(null);
        String inviterName = inviter != null ? inviter.getName() : "Someone";

        Notification n = new Notification();
        n.setUserId(inviteeId);
        n.setType("TRIP_INVITE");
        n.setTitle("Trip Invite");
        n.setBody(inviterName + " invited you to \"" + t.getTripName() + "\"");
        n.setData("{\"tripId\":" + tripId + ",\"inviteId\":" + inv.getId() + "}");
        notifications.save(n);

        activity.log(tripId, inviterId, "INVITED", "Invited user " + inviteeId);
    }

    /** Pending invites received by the current user. */
    public List<Map<String, Object>> pendingInvites(Long userId) {
        return invites.findByInviteeUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING")
                .stream().map(inv -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("inviteId", inv.getId());
                    out.put("tripId", inv.getTripId());
                    out.put("inviterUserId", inv.getInviterUserId());
                    out.put("createdAt", inv.getCreatedAt());
                    Trip t = trips.findById(inv.getTripId()).orElse(null);
                    if (t != null) {
                        out.put("tripName", t.getTripName());
                        out.put("startName", t.getStartName());
                        out.put("destName", t.getDestName());
                    }
                    User inviter = users.findById(inv.getInviterUserId()).orElse(null);
                    if (inviter != null) {
                        out.put("inviterName", inviter.getName());
                        out.put("inviterImage", inviter.getProfileImage());
                    }
                    return out;
                }).toList();
    }

    /** Accept a pending invite — join the trip. */
    public void acceptInvite(Long inviteId, Long userId) {
        TripInvite inv = invites.findById(inviteId)
                .orElseThrow(() -> new BadRequestException("Invite not found"));
        if (!inv.getInviteeUserId().equals(userId))
            throw new BadRequestException("Not your invite");
        if (!"PENDING".equals(inv.getStatus()))
            throw new BadRequestException("Invite already handled");

        inv.setStatus("ACCEPTED");
        invites.save(inv);

        if (!members.existsByTripIdAndUserId(inv.getTripId(), userId)) {
            TripMember m = new TripMember();
            m.setTripId(inv.getTripId());
            m.setUserId(userId);
            m.setRole("MEMBER");
            m.setRsvp("GOING");
            members.save(m);
        }

        activity.log(inv.getTripId(), userId, "JOINED", "Accepted invite");

        User user = users.findById(userId).orElse(null);
        String userName = user != null ? user.getName() : "Someone";
        Trip t = trips.findById(inv.getTripId()).orElse(null);
        String tripName = t != null ? t.getTripName() : "a trip";

        Notification n = new Notification();
        n.setUserId(inv.getInviterUserId());
        n.setType("INVITE_ACCEPTED");
        n.setTitle("Invite Accepted");
        n.setBody(userName + " accepted your invite to \"" + tripName + "\"");
        n.setData("{\"tripId\":" + inv.getTripId() + "}");
        notifications.save(n);
    }

    /** Reject a pending invite. */
    public void rejectInvite(Long inviteId, Long userId) {
        TripInvite inv = invites.findById(inviteId)
                .orElseThrow(() -> new BadRequestException("Invite not found"));
        if (!inv.getInviteeUserId().equals(userId))
            throw new BadRequestException("Not your invite");
        if (!"PENDING".equals(inv.getStatus()))
            throw new BadRequestException("Invite already handled");

        inv.setStatus("REJECTED");
        invites.save(inv);

        User user = users.findById(userId).orElse(null);
        String userName = user != null ? user.getName() : "Someone";
        Trip t = trips.findById(inv.getTripId()).orElse(null);
        String tripName = t != null ? t.getTripName() : "a trip";

        Notification n = new Notification();
        n.setUserId(inv.getInviterUserId());
        n.setType("INVITE_REJECTED");
        n.setTitle("Invite Declined");
        n.setBody(userName + " declined your invite to \"" + tripName + "\"");
        n.setData("{\"tripId\":" + inv.getTripId() + "}");
        notifications.save(n);
    }
}
