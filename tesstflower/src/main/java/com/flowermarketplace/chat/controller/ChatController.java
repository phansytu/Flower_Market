package com.flowermarketplace.chat.controller;

import com.flowermarketplace.chat.dto.ChatMessageDto;
import com.flowermarketplace.chat.dto.ConversationDto;
import com.flowermarketplace.chat.dto.SendMessageRequest;
import com.flowermarketplace.chat.service.ChatService;
import com.flowermarketplace.common.response.ApiResponse;
import com.flowermarketplace.common.response.PagedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/chat")
@RequiredArgsConstructor
@Tag(name = "Chat", description = "Real-time messaging (REST + WebSocket STOMP)")
public class ChatController {

    private final ChatService chatService;

    // ── REST ──────────────────────────────────────────────────────────────────

    @GetMapping("/conversations")
    @Operation(summary = "Get all conversations of current user")
    public ResponseEntity<ApiResponse<List<ConversationDto>>> getConversations(
            @AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success(
                chatService.getConversations(userDetails.getUsername())));
    }

    @GetMapping("/conversations/{convId}/messages")
    @Operation(summary = "Get paginated messages for a conversation")
    public ResponseEntity<ApiResponse<Page<ChatMessageDto>>> getMessages(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long convId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "30") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                chatService.getMessages(convId, userDetails.getUsername(), page, size)));
    }

    @PostMapping("/send")
    @Operation(summary = "Send a message (REST fallback)")
    public ResponseEntity<ApiResponse<ChatMessageDto>> send(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Message sent",
                chatService.sendMessage(userDetails.getUsername(), request)));
    }

    // ── WebSocket (STOMP) ─────────────────────────────────────────────────────

    @GetMapping("/conversations/with/{receiverId}")
    @Operation(summary = "Lấy hoặc tạo conversation 1-1 với một user")
    public ResponseEntity<ApiResponse<ConversationDto>> getOrCreate(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long receiverId) {
        return ResponseEntity.ok(ApiResponse.success(
                chatService.getOrCreateConversation(userDetails.getUsername(), receiverId)));
    }

    // ── WebSocket (STOMP) ─────────────────────────────────────────────────────

    @MessageMapping("/chat.send")
    public void handleWebSocket(@Payload SendMessageRequest request, Principal principal) {
        chatService.handleWebSocket(principal.getName(), request);
    }
}
