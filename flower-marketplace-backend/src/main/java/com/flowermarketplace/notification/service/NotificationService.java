package com.flowermarketplace.notification.service;

import com.flowermarketplace.common.enums.NotificationType;
import com.flowermarketplace.common.exception.ResourceNotFoundException;
import com.flowermarketplace.common.response.PagedResponse;
import com.flowermarketplace.notification.dto.NotificationDto;
import com.flowermarketplace.notification.dto.NotificationEvent;
import com.flowermarketplace.notification.entity.Notification;
import com.flowermarketplace.notification.mapper.NotificationMapper;
import com.flowermarketplace.notification.repository.NotificationRepository;
import com.flowermarketplace.user.entity.User;
import com.flowermarketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository  notifRepo;
    private final UserRepository          userRepo;
    private final NotificationMapper      mapper;
    private final SimpMessagingTemplate   ws;

    // ── Create ───────────────────────────────────────────────────────────────

    @Transactional
    public NotificationDto createNotification(Long userId,
                                              NotificationType type,
                                              String title,
                                              String message,
                                              String referenceId) {
        return createNotification(userId, type, title, message, referenceId, null);
    }

    @Transactional
    public NotificationDto createNotification(Long userId,
                                              NotificationType type,
                                              String title,
                                              String message,
                                              String referenceId,
                                              String referenceType) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        Notification notif = Notification.builder()
                .user(user)
                .type(type)
                .title(title)
                .message(message)
                .referenceId(referenceId)
                .referenceType(referenceType)
                .read(false)
                .build();

        notif = notifRepo.save(notif);
        NotificationDto dto = mapper.toDto(notif);

        // Push via WebSocket to the user's private channel
        try {
            ws.convertAndSendToUser(user.getEmail(), "/queue/notifications", dto);
        } catch (Exception e) {
            log.warn("WebSocket push failed for user {}: {}", userId, e.getMessage());
        }

        log.debug("Notification created for user {}: {}", userId, title);
        return dto;
    }

    /** Called from RabbitMQ consumer with a pre-built event object. */
    @Transactional
    public void handleEvent(NotificationEvent event) {
        createNotification(
                event.getUserId(),
                event.getType(),
                event.getTitle(),
                event.getMessage(),
                event.getReferenceId(),
                event.getReferenceType()
        );
    }

    // ── Query ────────────────────────────────────────────────────────────────

    public PagedResponse<NotificationDto> getNotifications(Long userId, int page, int size) {
        Page<Notification> result = notifRepo.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        return PagedResponse.of(result.map(mapper::toDto));
    }

    public PagedResponse<NotificationDto> getUnread(Long userId, int page, int size) {
        Page<Notification> result = notifRepo.findByUserIdAndReadFalseOrderByCreatedAtDesc(
                userId, PageRequest.of(page, size));
        return PagedResponse.of(result.map(mapper::toDto));
    }

    public long getUnreadCount(Long userId) {
        return notifRepo.countByUserIdAndReadFalse(userId);
    }

    // ── Mark read ────────────────────────────────────────────────────────────

    @Transactional
    public int markAllAsRead(Long userId) {
        int updated = notifRepo.markAllAsRead(userId, LocalDateTime.now());
        log.debug("Marked {} notifications as read for user {}", updated, userId);
        return updated;
    }

    @Transactional
    public NotificationDto markOneAsRead(Long notifId, Long userId) {
        notifRepo.markOneAsRead(notifId, userId, LocalDateTime.now());
        Notification n = notifRepo.findById(notifId)
                .orElseThrow(() -> new ResourceNotFoundException("Notification", "id", notifId));
        return mapper.toDto(n);
    }

    // ── Scheduled maintenance ────────────────────────────────────────────────

    /** Delete read notifications older than 90 days — runs every day at 03:00. */
    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeOldNotifications() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
        int deleted = notifRepo.deleteOldRead(cutoff);
        log.info("Purged {} old read notifications (cutoff={})", deleted, cutoff);
    }
}
