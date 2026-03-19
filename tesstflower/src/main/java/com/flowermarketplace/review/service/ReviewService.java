package com.flowermarketplace.review.service;

import com.flowermarketplace.common.exception.BadRequestException;
import com.flowermarketplace.common.exception.ResourceNotFoundException;
import com.flowermarketplace.common.exception.UnauthorizedException;
import com.flowermarketplace.common.response.PagedResponse;
import com.flowermarketplace.listing.entity.Listing;
import com.flowermarketplace.listing.repository.ListingRepository;
import com.flowermarketplace.review.dto.CreateReviewRequest;
import com.flowermarketplace.review.dto.RatingStatsDto;
import com.flowermarketplace.review.dto.ReviewDto;
import com.flowermarketplace.review.dto.SellerReplyRequest;
import com.flowermarketplace.review.entity.Review;
import com.flowermarketplace.review.mapper.ReviewMapper;
import com.flowermarketplace.review.repository.ReviewRepository;
import com.flowermarketplace.user.entity.User;
import com.flowermarketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final ReviewRepository    reviewRepository;
    private final ListingRepository   listingRepository;
    private final UserRepository      userRepository;
    private final ReviewMapper        reviewMapper;

    // ------------------------------------------------------------------ create

    @Transactional
    public ReviewDto createReview(Long reviewerId, CreateReviewRequest request) {
        if (reviewRepository.existsByReviewerIdAndListingId(reviewerId, request.getListingId())) {
            throw new BadRequestException("You have already reviewed this listing.");
        }

        User reviewer = userRepository.findById(reviewerId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", reviewerId));

        Listing listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing", "id", request.getListingId()));

        if (listing.getSeller().getId().equals(reviewerId)) {
            throw new BadRequestException("You cannot review your own listing.");
        }

        Review review = Review.builder()
                .reviewer(reviewer)
                .listing(listing)
                .seller(listing.getSeller())
                .rating(request.getRating())
                .comment(request.getComment())
                .active(true)
                .build();

        review = reviewRepository.save(review);
        refreshListingRatingCache(listing);

        log.info("Review {} created by user {} for listing {}", review.getId(), reviewerId, listing.getId());
        return reviewMapper.toDto(review);
    }

    // ------------------------------------------------------------------ read

    public ReviewDto getReviewById(Long id) {
        Review review = reviewRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", id));
        return reviewMapper.toDto(review);
    }

    public PagedResponse<ReviewDto> getListingReviews(Long listingId, int page, int size, String sort) {
        Sort sorting = resolveSort(sort);
        Page<Review> reviews = reviewRepository.findByListingIdAndActiveTrue(
                listingId, PageRequest.of(page, size, sorting));
        return PagedResponse.of(reviews.map(reviewMapper::toDto));
    }

    public PagedResponse<ReviewDto> getSellerReviews(Long sellerId, int page, int size, String sort) {
        Sort sorting = resolveSort(sort);
        Page<Review> reviews = reviewRepository.findBySellerIdAndActiveTrue(
                sellerId, PageRequest.of(page, size, sorting));
        return PagedResponse.of(reviews.map(reviewMapper::toDto));
    }

    public PagedResponse<ReviewDto> getMyReviews(Long reviewerId, int page, int size) {
        Page<Review> reviews = reviewRepository.findByReviewerIdAndActiveTrue(
                reviewerId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PagedResponse.of(reviews.map(reviewMapper::toDto));
    }

    // ------------------------------------------------------------------ stats

    public RatingStatsDto getListingRatingStats(Long listingId) {
        double avg   = reviewRepository.findAverageRatingByListingId(listingId).orElse(0.0);
        long   total = reviewRepository.countActiveByListingId(listingId);
        Map<Integer, Long> dist = buildDistribution(
                reviewRepository.findRatingDistributionByListingId(listingId));
        return RatingStatsDto.builder()
                .targetId(listingId).targetType("LISTING")
                .averageRating(Math.round(avg * 10.0) / 10.0)
                .totalReviews(total).distribution(dist).build();
    }

    public RatingStatsDto getSellerRatingStats(Long sellerId) {
        double avg   = reviewRepository.findAverageRatingBySellerId(sellerId).orElse(0.0);
        long   total = reviewRepository.countActiveBySellerId(sellerId);
        Map<Integer, Long> dist = buildDistribution(
                reviewRepository.findRatingDistributionBySellerId(sellerId));
        return RatingStatsDto.builder()
                .targetId(sellerId).targetType("SELLER")
                .averageRating(Math.round(avg * 10.0) / 10.0)
                .totalReviews(total).distribution(dist).build();
    }

    // ------------------------------------------------------------------ seller reply

    @Transactional
    public ReviewDto addSellerReply(Long sellerId, Long reviewId, SellerReplyRequest request) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));

        if (!review.getSeller().getId().equals(sellerId)) {
            throw new UnauthorizedException("You can only reply to reviews on your own listings.");
        }
        if (review.getSellerReply() != null) {
            throw new BadRequestException("You have already replied to this review.");
        }

        review.setSellerReply(request.getReply());
        review.setSellerRepliedAt(LocalDateTime.now());
        return reviewMapper.toDto(reviewRepository.save(review));
    }

    // ------------------------------------------------------------------ moderation

    @Transactional
    public void deactivateReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));
        review.setActive(false);
        reviewRepository.save(review);

        // Recalculate listing rating after removal
        refreshListingRatingCache(review.getListing());
        log.info("Review {} deactivated by admin", reviewId);
    }

    @Transactional
    public void reactivateReview(Long reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ResourceNotFoundException("Review", "id", reviewId));
        review.setActive(true);
        reviewRepository.save(review);
        refreshListingRatingCache(review.getListing());
    }

    // ------------------------------------------------------------------ helpers

    private void refreshListingRatingCache(Listing listing) {
        double avg   = reviewRepository.findAverageRatingByListingId(listing.getId()).orElse(0.0);
        long   count = reviewRepository.countActiveByListingId(listing.getId());
        listing.setAverageRating(Math.round(avg * 10.0) / 10.0);
        listing.setReviewCount((int) count);
        listingRepository.save(listing);
    }

    private Sort resolveSort(String sort) {
        return switch (sort == null ? "newest" : sort) {
            case "highest" -> Sort.by("rating").descending();
            case "lowest"  -> Sort.by("rating").ascending();
            default        -> Sort.by("createdAt").descending();
        };
    }

    private Map<Integer, Long> buildDistribution(List<Object[]> rows) {
        Map<Integer, Long> dist = new HashMap<>();
        for (int i = 1; i <= 5; i++) dist.put(i, 0L);
        for (Object[] row : rows) {
            dist.put((Integer) row[0], (Long) row[1]);
        }
        return dist;
    }
}
