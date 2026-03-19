package com.flowermarketplace.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data @NoArgsConstructor @AllArgsConstructor
public class SendMessageRequest {
    private Long   conversationId;   // null = start new conversation
    private Long   receiverId;       // required if conversationId is null
    @NotBlank
    private String content;
    private String messageType = "TEXT";
}
