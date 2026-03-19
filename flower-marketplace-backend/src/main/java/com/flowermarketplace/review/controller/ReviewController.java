package com.flowermarketplace.review.controller;

import com.flowermarketplace.common.response.ApiResponse;
import com.flowermarketplace.common.response.PagedResponse;
import com.flowermarketplace.review.dto.CreateReviewRequest;
import com.flowermarketplace.review.dto.RatingStatsDto;
import com.flowermarketplace.review.dto.ReviewDto;
import com.flowermarketplace.review.dto.SellerReplyRequest;
import com.flowermarketplace.review.service.ReviewService;
import com.flowermarketplace.user.dto.UserDto;
import com.flowermarketplace.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/reviews")
@RequiredArgsConstructor
@Tag(name = "Reviews", description = "Listing and seller reviews & ratings")
public class ReviewController {

    private final ReviewService reviewService;
    private final UserService   userService;

    // ── Create ──────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Submit a review for a listing")
    public ResponseEntity<ApiResponse<ReviewDto>> createReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody CreateReviewRequest request) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        ReviewDto review = reviewService.createReview(me.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Review submitted successfully", review));
    }

    // ── Read ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Get a review by ID")
    public ResponseEntity<ApiResponse<ReviewDto>> getReview(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getReviewById(id)));
    }

    @GetMapping("/listing/{listingId}")
    @Operation(summary = "Get paginated reviews for a listing")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewDto>>> getListingReviews(
            @PathVariable Long listingId,
            @RequestParam(defaultValue = "0")        int page,
            @RequestParam(defaultValue = "10")       int size,
            @RequestParam(defaultValue = "newest") String sort) {   // newest | highest | lowest

        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getListingReviews(listingId, page, size, sort)));
    }

    @GetMapping("/seller/{sellerId}")
    @Operation(summary = "Get paginated reviews for a seller")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewDto>>> getSellerReviews(
            @PathVariable Long sellerId,
            @RequestParam(defaultValue = "0")        int page,
            @RequestParam(defaultValue = "10")       int size,
            @RequestParam(defaultValue = "newest") String sort) {

        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getSellerReviews(sellerId, page, size, sort)));
    }

    @GetMapping("/my")
    @Operation(summary = "Get reviews written by me")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewDto>>> getMyReviews(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(
                reviewService.getMyReviews(me.getId(), page, size)));
    }

    // ── Stats ───────────────────────────────────────────────────────────────

    @GetMapping("/listing/{listingId}/stats")
    @Operation(summary = "Get rating stats (average + distribution) for a listing")
    public ResponseEntity<ApiResponse<RatingStatsDto>> getListingStats(@PathVariable Long listingId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getListingRatingStats(listingId)));
    }

    @GetMapping("/seller/{sellerId}/stats")
    @Operation(summary = "Get rating stats for a seller")
    public ResponseEntity<ApiResponse<RatingStatsDto>> getSellerStats(@PathVariable Long sellerId) {
        return ResponseEntity.ok(ApiResponse.success(reviewService.getSellerRatingStats(sellerId)));
    }

    // ── Seller Reply ─────────────────────────────────────────────────────────

    @PostMapping("/{id}/reply")
    @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
    @Operation(summary = "Seller replies to a review")
    public ResponseEntity<ApiResponse<ReviewDto>> replyToReview(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody SellerReplyRequest request) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Reply added",
                reviewService.addSellerReply(me.getId(), id, request)));
    }

    // ── Admin moderation (handled in AdminController too, kept here for convenience) ──

    @PatchMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: deactivate/hide a review")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        reviewService.deactivateReview(id);
        return ResponseEntity.ok(ApiResponse.success("Review deactivated", null));
    }

    @PatchMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: restore a deactivated review")
    public ResponseEntity<ApiResponse<Void>> reactivate(@PathVariable Long id) {
        reviewService.reactivateReview(id);
        return ResponseEntity.ok(ApiResponse.success("Review reactivated", null));
    }
}
