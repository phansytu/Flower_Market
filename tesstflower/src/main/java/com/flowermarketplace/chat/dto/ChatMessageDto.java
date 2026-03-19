package com.flowermarketplace.chat.dto;

import lombok.*;
import java.time.LocalDateTime;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class ChatMessageDto {
    private Long   id;
    private Long   conversationId;
    private Long   senderId;
    private String senderName;
    private String senderAvatar;
    private String content;
    private String messageType;
    private boolean read;
    private LocalDateTime createdAt;
}
