package com.flowermarketplace.listing.service;

import com.flowermarketplace.common.enums.ListingStatus;
import com.flowermarketplace.common.exception.BadRequestException;
import com.flowermarketplace.common.exception.ResourceNotFoundException;
import com.flowermarketplace.common.exception.UnauthorizedException;
import com.flowermarketplace.common.response.PagedResponse;
import com.flowermarketplace.listing.dto.CreateListingRequest;
import com.flowermarketplace.listing.dto.ListingDto;
import com.flowermarketplace.listing.entity.Listing;
import com.flowermarketplace.listing.entity.ListingDocument;
import com.flowermarketplace.listing.mapper.ListingMapper;
import com.flowermarketplace.listing.repository.ListingRepository;
import com.flowermarketplace.listing.repository.ListingSearchRepository;
import com.flowermarketplace.user.entity.User;
import com.flowermarketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ListingService {

    private final ListingRepository       listingRepository;
    private final ListingSearchRepository searchRepository;
    private final UserRepository          userRepository;
    private final ListingMapper           listingMapper;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public ListingDto createListing(String sellerEmail,
                                    CreateListingRequest request,
                                    List<MultipartFile> images) {
        User seller = userRepository.findByEmail(sellerEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + sellerEmail));

        Listing listing = Listing.builder()
                .seller(seller)
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .condition(request.getCondition())
                .price(request.getPrice())
                .stockQuantity(request.getStockQuantity())
                .location(request.getLocation())
                .tags(request.getTags())
                .freeDelivery(request.isFreeDelivery())
                .status(ListingStatus.ACTIVE)
                .build();

        if (images != null && !images.isEmpty()) {
            listing.setImageUrls(saveImages(images));
        }

        listing = listingRepository.save(listing);
        syncToElasticsearch(listing);

        log.info("Listing {} created by {}", listing.getId(), sellerEmail);
        return listingMapper.toDto(listing);
    }

    // ── Read (public) ─────────────────────────────────────────────────────────

    public PagedResponse<ListingDto> getPublicListings(int page, int size, String sort,
                                                        String category, String condition) {
        Sort s = parseSort(sort);
        Pageable pageable = PageRequest.of(page, size, s);

        Page<Listing> result;
        if (category != null && !category.isBlank()) {
            result = listingRepository.findByStatus(ListingStatus.ACTIVE, pageable)
                    .map(l -> l) // filter in memory for simplicity; add JPA method for perf
                    .filter(l -> category.equalsIgnoreCase(l.getCategory()))
                    .collect(java.util.stream.Collectors.toList())
                    .stream()
                    .collect(java.util.stream.Collectors.collectingAndThen(
                            java.util.stream.Collectors.toList(),
                            list -> new PageImpl<>(list, pageable, list.size())));
        } else {
            result = listingRepository.findByStatus(ListingStatus.ACTIVE, pageable);
        }

        return PagedResponse.of(result.map(listingMapper::toDto));
    }

    public ListingDto getPublicListing(Long id) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Listing", "id", id));
        listingRepository.incrementViewCount(id);
        return listingMapper.toDto(listing);
    }

    // ── Read (seller) ─────────────────────────────────────────────────────────

    public PagedResponse<ListingDto> getMyListings(String email, int page, int size) {
        User seller = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Listing> result = listingRepository.findBySellerId(seller.getId(), pageable);
        return PagedResponse.of(result.map(listingMapper::toDto));
    }

    // ── Search (Elasticsearch) ────────────────────────────────────────────────

    public PagedResponse<ListingDto> search(String keyword, int page, int size) {
        // Simple fallback: search via JPA LIKE if ES not available
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Listing> result = listingRepository.findByStatus(ListingStatus.ACTIVE, pageable);
        List<ListingDto> filtered = result.getContent().stream()
                .filter(l -> keyword == null
                        || l.getTitle().toLowerCase().contains(keyword.toLowerCase())
                        || (l.getDescription() != null && l.getDescription().toLowerCase().contains(keyword.toLowerCase()))
                        || (l.getTags() != null && l.getTags().toLowerCase().contains(keyword.toLowerCase())))
                .map(listingMapper::toDto)
                .toList();
        return PagedResponse.of(new PageImpl<>(filtered, pageable, filtered.size()));
    }

    // ── Update ────────────────────────────────────────────────────────────────

    @Transactional
    public ListingDto updateListing(Long id, String email,
                                     CreateListingRequest request,
                                     List<MultipartFile> images) {
        Listing listing = getOwnedListing(id, email);

        if (request.getTitle()       != null) listing.setTitle(request.getTitle());
        if (request.getDescription() != null) listing.setDescription(request.getDescription());
        if (request.getCategory()    != null) listing.setCategory(request.getCategory());
        if (request.getCondition()   != null) listing.setCondition(request.getCondition());
        if (request.getPrice()       != null) listing.setPrice(request.getPrice());
        listing.setStockQuantity(request.getStockQuantity());
        if (request.getLocation()    != null) listing.setLocation(request.getLocation());
        if (request.getTags()        != null) listing.setTags(request.getTags());
        listing.setFreeDelivery(request.isFreeDelivery());

        if (images != null && !images.isEmpty()) {
            listing.setImageUrls(saveImages(images));
        }

        listing = listingRepository.save(listing);
        syncToElasticsearch(listing);
        return listingMapper.toDto(listing);
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    @Transactional
    public void deleteListing(Long id, String email) {
        Listing listing = getOwnedListing(id, email);
        listingRepository.delete(listing);
        try { searchRepository.deleteById(String.valueOf(id)); } catch (Exception ignored) {}
        log.info("Listing {} deleted by {}", id, email);
    }

    // ── Status ────────────────────────────────────────────────────────────────

    @Transactional
    public ListingDto updateStatus(Long id, String email, ListingStatus status) {
        Listing listing = getOwnedListing(id, email);
        listing.setStatus(status);
        return listingMapper.toDto(listingRepository.save(listing));
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private Listing getOwnedListing(Long id, String email) {
        Listing listing = listingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Listing", "id", id));
        if (!listing.getSeller().getEmail().equals(email)) {
            throw new UnauthorizedException("You do not own this listing.");
        }
        return listing;
    }

    private List<String> saveImages(List<MultipartFile> files) {
        List<String> urls = new ArrayList<>();
        Path uploadDir = Path.of("uploads/listings");
        try {
            Files.createDirectories(uploadDir);
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;
                String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
                Path dest = uploadDir.resolve(filename);
                Files.copy(file.getInputStream(), dest, StandardCopyOption.REPLACE_EXISTING);
                urls.add("/uploads/listings/" + filename);
            }
        } catch (IOException e) {
            log.error("Failed to save images", e);
        }
        return urls;
    }

    private void syncToElasticsearch(Listing listing) {
        try {
            ListingDocument doc = ListingDocument.builder()
                    .id(String.valueOf(listing.getId()))
                    .title(listing.getTitle())
                    .description(listing.getDescription())
                    .category(listing.getCategory())
                    .condition(listing.getCondition())
                    .price(listing.getPrice())
                    .location(listing.getLocation())
                    .tags(listing.getTags())
                    .status(listing.getStatus().name())
                    .sellerId(listing.getSeller().getId())
                    .sellerName(listing.getSeller().getFullName())
                    .averageRating(listing.getAverageRating())
                    .createdAt(listing.getCreatedAt())
                    .build();
            searchRepository.save(doc);
        } catch (Exception e) {
            log.warn("Elasticsearch sync failed for listing {}: {}", listing.getId(), e.getMessage());
        }
    }

    private Sort parseSort(String sort) {
        if (sort == null) return Sort.by("createdAt").descending();
        String[] parts = sort.split(",");
        String prop = parts[0];
        Sort.Direction dir = (parts.length > 1 && "asc".equalsIgnoreCase(parts[1]))
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, prop);
    }

    // ── Called by other services ──────────────────────────────────────────────

    public Listing getEntityById(Long id) {
        return listingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Listing", "id", id));
    }
}
