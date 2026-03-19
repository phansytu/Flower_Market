package com.flowermarketplace.chat.mapper;

import com.flowermarketplace.chat.dto.ConversationDto;
import com.flowermarketplace.chat.entity.Conversation;
import com.flowermarketplace.user.entity.User;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ConversationMapper {

    /**
     * Map một Conversation sang ConversationDto theo góc nhìn của {@code currentUserId}.
     * <p>
     * Vì MapStruct không hỗ trợ conditional field từ @Context trực tiếp,
     * các field phụ thuộc vào currentUserId được tính qua default methods.
     */
    @Mapping(target = "otherUserId",     expression = "java(resolveOther(conv, currentUserId).getId())")
    @Mapping(target = "otherUserName",   expression = "java(resolveOther(conv, currentUserId).getFirstName() + \" \" + resolveOther(conv, currentUserId).getLastName())")
    @Mapping(target = "otherUserAvatar", expression = "java(resolveOther(conv, currentUserId).getProfileImageUrl())")
    @Mapping(target = "unreadCount",     expression = "java(resolveUnread(conv, currentUserId))")
    ConversationDto toDto(Conversation conv, @Context Long currentUserId);

    default User resolveOther(Conversation conv, Long currentUserId) {
        return conv.getUser1().getId().equals(currentUserId)
                ? conv.getUser2()
                : conv.getUser1();
    }

    default int resolveUnread(Conversation conv, Long currentUserId) {
        return conv.getUser1().getId().equals(currentUserId)
                ? conv.getUnreadCountUser1()
                : conv.getUnreadCountUser2();
    }
}
