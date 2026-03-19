package com.flowermarketplace.order.mapper;

import com.flowermarketplace.order.dto.OrderDto;
import com.flowermarketplace.order.dto.OrderItemDto;
import com.flowermarketplace.order.entity.Order;
import com.flowermarketplace.order.entity.OrderItem;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "buyerId",   source = "buyer.id")
    @Mapping(target = "buyerName",
             expression = "java(order.getBuyer().getFirstName() + \" \" + order.getBuyer().getLastName())")
    @Mapping(target = "shippingAddressLine",
             expression = "java(order.getShippingAddress() != null ? order.getShippingAddress().getAddressLine() + \", \" + order.getShippingAddress().getCity() : null)")
    OrderDto toDto(Order order);

    @Mapping(target = "listingId",       source = "listing.id")
    @Mapping(target = "listingTitle",    source = "listing.title")
    @Mapping(target = "listingImageUrl",
             expression = "java(item.getListing().getImageUrls().isEmpty() ? null : item.getListing().getImageUrls().get(0))")
    OrderItemDto toItemDto(OrderItem item);
}
