package com.flowermarketplace.notification.dto;

import com.flowermarketplace.common.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private Long             id;
    private NotificationType type;
    private String           title;
    private String           message;
    private String           referenceId;
    private String           referenceType;
    private boolean          read;
    private LocalDateTime    readAt;
    private LocalDateTime    createdAt;
}
