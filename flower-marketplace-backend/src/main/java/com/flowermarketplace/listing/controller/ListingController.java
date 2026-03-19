package com.flowermarketplace.listing.controller;

import com.flowermarketplace.common.enums.ListingStatus;
import com.flowermarketplace.common.response.ApiResponse;
import com.flowermarketplace.common.response.PagedResponse;
import com.flowermarketplace.listing.dto.CreateListingRequest;
import com.flowermarketplace.listing.dto.ListingDto;
import com.flowermarketplace.listing.service.ListingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Listings", description = "Listing CRUD and search")
public class ListingController {

        private final ListingService listingService;

        // ── Public endpoints ──────────────────────────────────────────────────────

        @GetMapping("/listings/public")
        @Operation(summary = "Browse active listings (public)")
        public ResponseEntity<ApiResponse<PagedResponse<ListingDto>>> getPublic(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "12") int size,
                        @RequestParam(defaultValue = "createdAt,desc") String sort,
                        @RequestParam(required = false) String category,
                        @RequestParam(required = false) String condition) {
                return ResponseEntity.ok(ApiResponse.success(
                                listingService.getPublicListings(page, size, sort, category, condition)));
        }

        @GetMapping("/listings/public/{id}")
        @Operation(summary = "Get a single listing (public)")
        public ResponseEntity<ApiResponse<ListingDto>> getOne(@PathVariable Long id) {
                return ResponseEntity.ok(ApiResponse.success(listingService.getPublicListing(id)));
        }

        @GetMapping("/listings/search")
        @Operation(summary = "Full-text search listings (Elasticsearch)")
        public ResponseEntity<ApiResponse<PagedResponse<ListingDto>>> search(
                        @RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "12") int size) {
                return ResponseEntity.ok(ApiResponse.success(
                                listingService.search(keyword, page, size)));
        }

        // ── Seller endpoints ──────────────────────────────────────────────────────

        @GetMapping("/listings/my")
        @Operation(summary = "Get current seller's listings")
        public ResponseEntity<ApiResponse<PagedResponse<ListingDto>>> getMy(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size) {
                return ResponseEntity.ok(ApiResponse.success(
                                listingService.getMyListings(userDetails.getUsername(), page, size)));
        }

        @PostMapping(value = "/listings", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
        @Operation(summary = "Create a new listing (with images)")
        public ResponseEntity<ApiResponse<ListingDto>> create(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @Valid @RequestPart("data") CreateListingRequest request,
                        @RequestPart(value = "images", required = false) List<MultipartFile> images) {
                return ResponseEntity.status(HttpStatus.CREATED)
                                .body(ApiResponse.success("Listing created",
                                                listingService.createListing(userDetails.getUsername(), request,
                                                                images)));
        }

        @PutMapping(value = "/listings/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
        @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
        @Operation(summary = "Update a listing")
        public ResponseEntity<ApiResponse<ListingDto>> update(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable Long id,
                        @RequestPart("data") CreateListingRequest request,
                        @RequestPart(value = "images", required = false) List<MultipartFile> images) {
                return ResponseEntity.ok(ApiResponse.success("Listing updated",
                                listingService.updateListing(id, userDetails.getUsername(), request, images)));
        }

        @DeleteMapping("/listings/{id}")
        @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
        @Operation(summary = "Delete a listing")
        public ResponseEntity<ApiResponse<Void>> delete(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable Long id) {
                listingService.deleteListing(id, userDetails.getUsername());
                return ResponseEntity.ok(ApiResponse.success("Listing deleted", null));
        }

        @PatchMapping("/listings/{id}/status")
        @PreAuthorize("hasRole('SELLER') or hasRole('ADMIN')")
        @Operation(summary = "Update listing status")
        public ResponseEntity<ApiResponse<ListingDto>> updateStatus(
                        @AuthenticationPrincipal UserDetails userDetails,
                        @PathVariable Long id,
                        @RequestParam ListingStatus status) {
                return ResponseEntity.ok(ApiResponse.success("Status updated",
                                listingService.updateStatus(id, userDetails.getUsername(), status)));
        }
}
