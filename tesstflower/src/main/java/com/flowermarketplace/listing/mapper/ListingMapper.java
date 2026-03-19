package com.flowermarketplace.listing.mapper;

import com.flowermarketplace.listing.dto.ListingDto;
import com.flowermarketplace.listing.entity.Listing;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ListingMapper {

    @Mapping(target = "sellerId",    source = "seller.id")
    @Mapping(target = "sellerName",
             expression = "java(listing.getSeller().getFirstName() + \" \" + listing.getSeller().getLastName())")
    @Mapping(target = "sellerAvatar", source = "seller.profileImageUrl")
    @Mapping(target = "sellerCity",   source = "seller.city")
    ListingDto toDto(Listing listing);
}
