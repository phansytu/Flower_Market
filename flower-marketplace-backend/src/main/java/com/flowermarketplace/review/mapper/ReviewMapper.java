package com.flowermarketplace.review.mapper;

import com.flowermarketplace.review.dto.ReviewDto;
import com.flowermarketplace.review.entity.Review;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ReviewMapper {

    @Mapping(target = "reviewerId",    source = "reviewer.id")
    @Mapping(target = "reviewerName",  expression = "java(review.getReviewer().getFirstName() + \" \" + review.getReviewer().getLastName())")
    @Mapping(target = "reviewerAvatar",source = "reviewer.profileImageUrl")
    @Mapping(target = "listingId",     source = "listing.id")
    @Mapping(target = "listingTitle",  source = "listing.title")
    @Mapping(target = "sellerId",      source = "seller.id")
    @Mapping(target = "sellerName",    expression = "java(review.getSeller().getFirstName() + \" \" + review.getSeller().getLastName())")
    ReviewDto toDto(Review review);
}
