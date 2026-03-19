package com.flowermarketplace.notification.dto;

import com.flowermarketplace.common.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/** Serialised onto the RabbitMQ notification exchange. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent implements Serializable {
    private Long             userId;
    private String           userEmail;
    private String           userName;
    private NotificationType type;
    private String           title;
    private String           message;
    private String           referenceId;
    private String           referenceType;
}
