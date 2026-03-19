package com.flowermarketplace.notification.mapper;

import com.flowermarketplace.notification.dto.NotificationDto;
import com.flowermarketplace.notification.entity.Notification;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface NotificationMapper {
    NotificationDto toDto(Notification notification);
}
