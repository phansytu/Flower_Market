package com.flowermarketplace.admin.service;

import com.flowermarketplace.admin.dto.*;
import com.flowermarketplace.admin.entity.AdminConfig;
import com.flowermarketplace.admin.entity.AuditLog;
import com.flowermarketplace.admin.entity.BanRecord;
import com.flowermarketplace.admin.mapper.AdminMapper;
import com.flowermarketplace.admin.repository.AdminConfigRepository;
import com.flowermarketplace.admin.repository.AuditLogRepository;
import com.flowermarketplace.admin.repository.BanRecordRepository;
import com.flowermarketplace.common.enums.ListingStatus;
import com.flowermarketplace.common.enums.OrderStatus;
import com.flowermarketplace.common.enums.PaymentStatus;
import com.flowermarketplace.common.exception.BadRequestException;
import com.flowermarketplace.common.exception.ResourceNotFoundException;
import com.flowermarketplace.common.response.PagedResponse;
import com.flowermarketplace.listing.dto.ListingDto;
import com.flowermarketplace.listing.entity.Listing;
import com.flowermarketplace.listing.mapper.ListingMapper;
import com.flowermarketplace.listing.repository.ListingRepository;
import com.flowermarketplace.order.dto.OrderDto;
import com.flowermarketplace.order.entity.Order;
import com.flowermarketplace.order.mapper.OrderMapper;
import com.flowermarketplace.order.repository.OrderRepository;
import com.flowermarketplace.payment.dto.PaymentDto;
import com.flowermarketplace.payment.mapper.PaymentMapper;
import com.flowermarketplace.payment.repository.PaymentRepository;
import com.flowermarketplace.review.dto.ReviewDto;
import com.flowermarketplace.review.mapper.ReviewMapper;
import com.flowermarketplace.review.repository.ReviewRepository;
import com.flowermarketplace.review.service.ReviewService;
import com.flowermarketplace.user.dto.UserDto;
import com.flowermarketplace.user.entity.User;
import com.flowermarketplace.user.mapper.UserMapper;
import com.flowermarketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository        userRepo;
    private final ListingRepository     listingRepo;
    private final OrderRepository       orderRepo;
    private final PaymentRepository     paymentRepo;
    private final ReviewRepository      reviewRepo;
    private final AuditLogRepository    auditLogRepo;
    private final BanRecordRepository   banRepo;
    private final AdminConfigRepository configRepo;
    private final ReviewService         reviewService;

    private final UserMapper    userMapper;
    private final ListingMapper listingMapper;
    private final OrderMapper   orderMapper;
    private final PaymentMapper paymentMapper;
    private final ReviewMapper  reviewMapper;
    private final AdminMapper   adminMapper;

    // ── Dashboard ─────────────────────────────────────────────────────────────

    public AdminDashboardDto getDashboard() {
        return AdminDashboardDto.builder()
                .totalUsers(userRepo.count())
                .totalSellers(userRepo.countByRole(com.flowermarketplace.common.enums.Role.ROLE_SELLER))
                .totalBuyers(userRepo.countByRole(com.flowermarketplace.common.enums.Role.ROLE_BUYER))
                .totalListings(listingRepo.count())
                .activeListings(listingRepo.countByStatus(ListingStatus.ACTIVE))
                .totalOrders(orderRepo.count())
                .pendingOrders(orderRepo.countByStatus(OrderStatus.PENDING))
                .totalPayments(paymentRepo.count())
                .totalRevenue(paymentRepo.sumCompletedRevenue().orElse(BigDecimal.ZERO))
                .totalReviews(reviewRepo.count())
                .build();
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    public PagedResponse<UserDto> getUsers(int page, int size) {
        Page<User> users = userRepo.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PagedResponse.of(users.map(userMapper::toDto));
    }

    public UserDto getUserById(Long id) {
        return userMapper.toDto(userRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id)));
    }

    @Transactional
    public UserDto updateUserRole(Long userId, UpdateUserRoleRequest request, Long adminId) {
        User user = getUser(userId);
        String before = user.getRole().name();
        user.setRole(request.getRole());
        userRepo.save(user);
        writeAudit(adminId, "USER_ROLE_CHANGED", "USER", userId,
                "Changed role from " + before + " to " + request.getRole().name(), before, request.getRole().name());
        return userMapper.toDto(user);
    }

    @Transactional
    public UserDto toggleUserEnabled(Long userId, Long adminId) {
        User user = getUser(userId);
        user.setEnabled(!user.isEnabled());
        userRepo.save(user);
        String action = user.isEnabled() ? "USER_ENABLED" : "USER_DISABLED";
        writeAudit(adminId, action, "USER", userId, action + " for user " + userId, null, null);
        return userMapper.toDto(user);
    }

    // ── Ban / Unban ───────────────────────────────────────────────────────────

    @Transactional
    public BanRecordDto banUser(Long userId, BanRequest request, Long adminId) {
        if (banRepo.existsByUserIdAndActiveTrue(userId)) {
            throw new BadRequestException("User already has an active ban.");
        }
        if (request.getBanType() == BanRecord.BanType.TEMPORARY && request.getExpiresAt() == null) {
            throw new BadRequestException("expiresAt is required for TEMPORARY bans.");
        }
        User user  = getUser(userId);
        User admin = getUser(adminId);

        // Disable account immediately
        user.setEnabled(false);
        userRepo.save(user);

        BanRecord ban = BanRecord.builder()
                .user(user).bannedBy(admin)
                .reason(request.getReason())
                .banType(request.getBanType())
                .expiresAt(request.getExpiresAt())
                .active(true)
                .build();
        ban = banRepo.save(ban);

        writeAudit(adminId, "USER_BANNED", "USER", userId,
                request.getBanType() + " ban: " + request.getReason(), null, null);
        return adminMapper.toBanRecordDto(ban);
    }

    @Transactional
    public BanRecordDto liftBan(Long banId, String liftReason, Long adminId) {
        BanRecord ban = banRepo.findById(banId)
                .orElseThrow(() -> new ResourceNotFoundException("BanRecord", "id", banId));
        if (!ban.isActive()) throw new BadRequestException("This ban is not active.");

        User admin = getUser(adminId);
        ban.setActive(false);
        ban.setLiftedBy(admin);
        ban.setLiftedAt(LocalDateTime.now());
        ban.setLiftReason(liftReason);
        banRepo.save(ban);

        // Re-enable user account
        ban.getUser().setEnabled(true);
        userRepo.save(ban.getUser());

        writeAudit(adminId, "USER_UNBANNED", "USER", ban.getUser().getId(),
                "Ban lifted. Reason: " + liftReason, null, null);
        return adminMapper.toBanRecordDto(ban);
    }

    public PagedResponse<BanRecordDto> getBanHistory(Long userId, int page, int size) {
        return PagedResponse.of(
                banRepo.findByUserIdOrderByCreatedAtDesc(userId, PageRequest.of(page, size))
                       .map(adminMapper::toBanRecordDto));
    }

    public PagedResponse<BanRecordDto> getActiveBans(int page, int size) {
        return PagedResponse.of(
                banRepo.findByActiveTrueOrderByCreatedAtDesc(PageRequest.of(page, size))
                       .map(adminMapper::toBanRecordDto));
    }

    /** Scheduled: auto-lift expired temporary bans every hour. */
    @Scheduled(fixedRate = 3_600_000)
    @Transactional
    public void expireTemporaryBans() {
        List<BanRecord> expired = banRepo.findExpiredActiveBans();
        for (BanRecord ban : expired) {
            ban.setActive(false);
            ban.setLiftedAt(LocalDateTime.now());
            ban.setLiftReason("Automatically lifted — ban period expired.");
            ban.getUser().setEnabled(true);
            userRepo.save(ban.getUser());
            banRepo.save(ban);
            log.info("Auto-lifted expired ban {} for user {}", ban.getId(), ban.getUser().getId());
        }
    }

    // ── Listings ──────────────────────────────────────────────────────────────

    public PagedResponse<ListingDto> getListings(int page, int size, ListingStatus status) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Listing> listings = (status != null)
                ? listingRepo.findByStatus(status, pageable)
                : listingRepo.findAll(pageable);
        return PagedResponse.of(listings.map(listingMapper::toDto));
    }

    @Transactional
    public ListingDto updateListingStatus(Long listingId, ListingStatus status, Long adminId) {
        Listing listing = listingRepo.findById(listingId)
                .orElseThrow(() -> new ResourceNotFoundException("Listing", "id", listingId));
        String before = listing.getStatus().name();
        listing.setStatus(status);
        listingRepo.save(listing);
        writeAudit(adminId, "LISTING_STATUS_CHANGED", "LISTING", listingId,
                "Status: " + before + " → " + status.name(), before, status.name());
        return listingMapper.toDto(listing);
    }

    // ── Orders ────────────────────────────────────────────────────────────────

    public PagedResponse<OrderDto> getOrders(int page, int size, OrderStatus status) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Order> orders = (status != null)
                ? orderRepo.findByStatus(status, pageable)
                : orderRepo.findAll(pageable);
        return PagedResponse.of(orders.map(orderMapper::toDto));
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    public PagedResponse<PaymentDto> getPayments(int page, int size, PaymentStatus status) {
        var pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        var payments = (status != null)
                ? paymentRepo.findByStatus(status, pageable)
                : paymentRepo.findAll(pageable);
        return PagedResponse.of(payments.map(paymentMapper::toDto));
    }

    // ── Reviews ───────────────────────────────────────────────────────────────

    public PagedResponse<ReviewDto> getReviews(int page, int size) {
        return PagedResponse.of(
                reviewRepo.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))
                          .map(reviewMapper::toDto));
    }

    @Transactional
    public void moderateReview(Long reviewId, boolean activate, Long adminId) {
        if (activate) reviewService.reactivateReview(reviewId);
        else          reviewService.deactivateReview(reviewId);
        writeAudit(adminId, activate ? "REVIEW_REACTIVATED" : "REVIEW_DEACTIVATED",
                "REVIEW", reviewId, null, null, null);
    }

    // ── Audit Logs ────────────────────────────────────────────────────────────

    public PagedResponse<AuditLogDto> getAuditLogs(int page, int size) {
        return PagedResponse.of(
                auditLogRepo.findAll(PageRequest.of(page, size, Sort.by("createdAt").descending()))
                            .map(adminMapper::toAuditLogDto));
    }

    public PagedResponse<AuditLogDto> getAuditLogsByAdmin(Long adminId, int page, int size) {
        return PagedResponse.of(
                auditLogRepo.findByAdminIdOrderByCreatedAtDesc(adminId, PageRequest.of(page, size))
                            .map(adminMapper::toAuditLogDto));
    }

    public PagedResponse<AuditLogDto> getAuditLogsByEntity(String entityType, Long entityId, int page, int size) {
        return PagedResponse.of(
                auditLogRepo.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId, PageRequest.of(page, size))
                            .map(adminMapper::toAuditLogDto));
    }

    // ── Platform Config ───────────────────────────────────────────────────────

    public List<AdminConfigDto> getAllConfigs() {
        return configRepo.findAll(Sort.by("category", "configKey"))
                         .stream().map(adminMapper::toConfigDto).toList();
    }

    public List<AdminConfigDto> getConfigsByCategory(String category) {
        return configRepo.findByCategoryOrderByConfigKey(category)
                         .stream().map(adminMapper::toConfigDto).toList();
    }

    @Transactional
    public AdminConfigDto upsertConfig(UpsertConfigRequest request, Long adminId) {
        User admin = getUser(adminId);
        AdminConfig config = configRepo.findByConfigKey(request.getConfigKey())
                .orElse(AdminConfig.builder().configKey(request.getConfigKey()).build());

        String before = config.getConfigValue();
        config.setConfigValue(request.getConfigValue());
        if (request.getDescription() != null) config.setDescription(request.getDescription());
        if (request.getCategory()    != null) config.setCategory(request.getCategory());
        if (request.getValueType()   != null) config.setValueType(request.getValueType());
        config.setSensitive(request.isSensitive());
        config.setUpdatedBy(admin);

        config = configRepo.save(config);
        writeAudit(adminId, "CONFIG_UPDATED", "CONFIG", config.getId(),
                "Key: " + config.getConfigKey(), before, config.getConfigValue());
        return adminMapper.toConfigDto(config);
    }

    @Transactional
    public void deleteConfig(Long configId, Long adminId) {
        AdminConfig config = configRepo.findById(configId)
                .orElseThrow(() -> new ResourceNotFoundException("AdminConfig", "id", configId));
        configRepo.delete(config);
        writeAudit(adminId, "CONFIG_DELETED", "CONFIG", configId,
                "Deleted config key: " + config.getConfigKey(), null, null);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private User getUser(Long id) {
        return userRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", id));
    }

    private void writeAudit(Long adminId, String action, String entityType, Long entityId,
                             String description, String before, String after) {
        try {
            User admin = getUser(adminId);
            AuditLog log = AuditLog.builder()
                    .admin(admin).action(action)
                    .entityType(entityType).entityId(entityId)
                    .description(description)
                    .beforeValue(before).afterValue(after)
                    .build();
            auditLogRepo.save(log);
        } catch (Exception e) {
            log.warn("Failed to write audit log for action {}: {}", action, e.getMessage());
        }
    }
}
