package com.flowermarketplace.review.repository;

import com.flowermarketplace.review.entity.Review;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends JpaRepository<Review, Long> {

    Page<Review> findByListingIdAndActiveTrue(Long listingId, Pageable pageable);

    Page<Review> findBySellerIdAndActiveTrue(Long sellerId, Pageable pageable);

    Page<Review> findByReviewerIdAndActiveTrue(Long reviewerId, Pageable pageable);

    Optional<Review> findByReviewerIdAndListingId(Long reviewerId, Long listingId);

    // ---- Aggregates ----

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.listing.id = :listingId AND r.active = true")
    Optional<Double> findAverageRatingByListingId(@Param("listingId") Long listingId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.seller.id = :sellerId AND r.active = true")
    Optional<Double> findAverageRatingBySellerId(@Param("sellerId") Long sellerId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.listing.id = :listingId AND r.active = true")
    long countActiveByListingId(@Param("listingId") Long listingId);

    @Query("SELECT COUNT(r) FROM Review r WHERE r.seller.id = :sellerId AND r.active = true")
    long countActiveBySellerId(@Param("sellerId") Long sellerId);

    /** Star distribution: returns list of [rating, count] pairs */
    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.listing.id = :listingId AND r.active = true GROUP BY r.rating")
    List<Object[]> findRatingDistributionByListingId(@Param("listingId") Long listingId);

    @Query("SELECT r.rating, COUNT(r) FROM Review r WHERE r.seller.id = :sellerId AND r.active = true GROUP BY r.rating")
    List<Object[]> findRatingDistributionBySellerId(@Param("sellerId") Long sellerId);

    boolean existsByReviewerIdAndListingId(Long reviewerId, Long listingId);
}
