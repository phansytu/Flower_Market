package com.flowermarketplace.chat.service;

import com.flowermarketplace.chat.dto.ChatMessageDto;
import com.flowermarketplace.chat.dto.ConversationDto;
import com.flowermarketplace.chat.dto.SendMessageRequest;
import com.flowermarketplace.chat.entity.ChatMessage;
import com.flowermarketplace.chat.entity.Conversation;
import com.flowermarketplace.chat.mapper.ChatMapper;
import com.flowermarketplace.chat.mapper.ConversationMapper;
import com.flowermarketplace.chat.repository.ChatMessageRepository;
import com.flowermarketplace.chat.repository.ConversationRepository;
import com.flowermarketplace.common.exception.BadRequestException;
import com.flowermarketplace.common.exception.ResourceNotFoundException;
import com.flowermarketplace.common.exception.UnauthorizedException;
import com.flowermarketplace.user.entity.User;
import com.flowermarketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository  messageRepository;
    private final UserRepository         userRepository;
    private final SimpMessagingTemplate  messagingTemplate;
    private final ChatMapper             chatMapper;
    private final ConversationMapper     conversationMapper;

    // ─────────────────────────────────────────────────────────────────────────
    // Conversations
    // ─────────────────────────────────────────────────────────────────────────

    public List<ConversationDto> getConversations(String email) {
        User me = findUser(email);
        return conversationRepository.findByUserId(me.getId())
                .stream()
                .map(conv -> conversationMapper.toDto(conv, me.getId()))
                .toList();
    }

    @Transactional
    public ConversationDto getOrCreateConversation(String email, Long receiverId) {
        User me       = findUser(email);
        User receiver = findUserById(receiverId);

        Conversation conv = conversationRepository
                .findBetween(me.getId(), receiver.getId())
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder()
                                .user1(me)
                                .user2(receiver)
                                .build()
                ));

        return conversationMapper.toDto(conv, me.getId());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Messages
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public Page<ChatMessageDto> getMessages(Long convId, String email, int page, int size) {
        User me   = findUser(email);
        Conversation conv = findConversation(convId);
        assertParticipant(conv, me.getId());

        messageRepository.markAsRead(convId, me.getId());

        return messageRepository
                .findByConversationIdOrderByCreatedAtDesc(convId, PageRequest.of(page, size))
                .map(chatMapper::toDto);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send  (dùng chung cho REST và WebSocket)
    // ─────────────────────────────────────────────────────────────────────────

    @Transactional
    public ChatMessageDto sendMessage(String senderEmail, SendMessageRequest request) {
        User sender = findUser(senderEmail);
        Conversation conv = resolveConversation(sender, request);

        ChatMessage msg = messageRepository.save(
                ChatMessage.builder()
                        .conversation(conv)
                        .sender(sender)
                        .content(request.getContent())
                        .messageType(resolveType(request.getMessageType()))
                        .build()
        );

        updateSummary(conv, sender.getId(), request.getContent(), msg);

        ChatMessageDto dto = chatMapper.toDto(msg);
        pushToReceiver(conv, sender.getId(), dto);

        log.info("Message {} sent in conv {} by user {}", msg.getId(), conv.getId(), sender.getId());
        return dto;
    }

    @Transactional
    public void handleWebSocket(String senderEmail, SendMessageRequest request) {
        sendMessage(senderEmail, request);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Conversation resolveConversation(User sender, SendMessageRequest request) {
        if (request.getConversationId() != null) {
            Conversation conv = findConversation(request.getConversationId());
            assertParticipant(conv, sender.getId());
            return conv;
        }
        if (request.getReceiverId() == null) {
            throw new BadRequestException("Phải cung cấp conversationId hoặc receiverId.");
        }
        User receiver = findUserById(request.getReceiverId());
        return conversationRepository
                .findBetween(sender.getId(), receiver.getId())
                .orElseGet(() -> conversationRepository.save(
                        Conversation.builder().user1(sender).user2(receiver).build()
                ));
    }

    private void updateSummary(Conversation conv, Long senderId, String content, ChatMessage msg) {
        conv.setLastMessage(content);
        conv.setLastMessageAt(msg.getCreatedAt());
        if (conv.getUser1().getId().equals(senderId)) {
            conv.setUnreadCountUser2(conv.getUnreadCountUser2() + 1);
        } else {
            conv.setUnreadCountUser1(conv.getUnreadCountUser1() + 1);
        }
        conversationRepository.save(conv);
    }

    private void pushToReceiver(Conversation conv, Long senderId, ChatMessageDto dto) {
        User receiver = conv.getUser1().getId().equals(senderId)
                ? conv.getUser2() : conv.getUser1();
        messagingTemplate.convertAndSendToUser(receiver.getEmail(), "/queue/messages", dto);
    }

    private void assertParticipant(Conversation conv, Long userId) {
        if (!conv.getUser1().getId().equals(userId) && !conv.getUser2().getId().equals(userId)) {
            throw new UnauthorizedException("Bạn không phải thành viên của cuộc hội thoại này.");
        }
    }

    private String resolveType(String type) {
        return (type != null && !type.isBlank()) ? type : "TEXT";
    }

    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("Không tìm thấy user: " + email));
    }

    private User findUserById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private Conversation findConversation(Long id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", id));
    }
}
