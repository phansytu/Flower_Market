package com.flowermarketplace.payment.mapper;

import com.flowermarketplace.payment.dto.PaymentDto;
import com.flowermarketplace.payment.entity.Payment;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "orderId",     source = "order.id")
    @Mapping(target = "orderNumber", source = "order.orderNumber")
    @Mapping(target = "userId",      source = "user.id")
    PaymentDto toDto(Payment payment);
}
