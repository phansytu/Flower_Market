package com.flowermarketplace.chat.mapper;
import com.flowermarketplace.chat.dto.ChatMessageDto;
import com.flowermarketplace.chat.entity.ChatMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChatMapper {

    @Mapping(target = "conversationId", source = "conversation.id")
    @Mapping(target = "senderId",       source = "sender.id")
    @Mapping(target = "senderName",
             expression = "java(msg.getSender().getFirstName() + \" \" + msg.getSender().getLastName())")
    @Mapping(target = "senderAvatar",   source = "sender.profileImageUrl")
    ChatMessageDto toDto(ChatMessage msg);
}
