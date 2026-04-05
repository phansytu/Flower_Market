package com.flowermarketplace.admin.controller;

import com.flowermarketplace.admin.dto.*;
import com.flowermarketplace.admin.service.AdminService;
import com.flowermarketplace.common.enums.ListingStatus;
import com.flowermarketplace.common.enums.OrderStatus;
import com.flowermarketplace.common.enums.PaymentStatus;
import com.flowermarketplace.common.response.ApiResponse;
import com.flowermarketplace.common.response.PagedResponse;
import com.flowermarketplace.listing.dto.ListingDto;
import com.flowermarketplace.order.dto.OrderDto;
import com.flowermarketplace.payment.dto.PaymentDto;
import com.flowermarketplace.review.dto.ReviewDto;
import com.flowermarketplace.user.dto.UserDto;
import com.flowermarketplace.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin panel – dashboard, users, listings, orders, payments, reviews, bans, audit logs and platform config")
public class AdminController {

    private final AdminService adminService;
    private final UserService userService;

    private Long myId(UserDetails ud) {
        return userService.getUserByEmail(ud.getUsername()).getId();
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @GetMapping("/dashboard")
    @Operation(summary = "Platform summary statistics")
    public ResponseEntity<ApiResponse<AdminDashboardDto>> dashboard() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getDashboard()));
    }

    // ── Users ─────────────────────────────────────────────────────────────────

    @GetMapping("/users")
    @Operation(summary = "List all users (paginated)")
    public ResponseEntity<ApiResponse<PagedResponse<UserDto>>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getUsers(page, size)));
    }

    @GetMapping("/users/{id}")
    @Operation(summary = "Get a user by ID")
    public ResponseEntity<ApiResponse<UserDto>> getUser(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getUserById(id)));
    }

    @PatchMapping("/users/{id}/role")
    @Operation(summary = "Change a user's role")
    public ResponseEntity<ApiResponse<UserDto>> changeRole(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Role updated",
                adminService.updateUserRole(id, request, myId(ud))));
    }

    @PatchMapping("/users/{id}/toggle-enabled")
    @Operation(summary = "Enable or disable a user account")
    public ResponseEntity<ApiResponse<UserDto>> toggleUser(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("User account toggled",
                adminService.toggleUserEnabled(id, myId(ud))));
    }

    // ── Ban / Unban ───────────────────────────────────────────────────────────

    @PostMapping("/users/{id}/ban")
    @Operation(summary = "Ban a user (permanent or temporary)")
    public ResponseEntity<ApiResponse<BanRecordDto>> banUser(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @Valid @RequestBody BanRequest request) {
        return ResponseEntity.ok(ApiResponse.success("User banned",
                adminService.banUser(id, request, myId(ud))));
    }

    @PatchMapping("/bans/{banId}/lift")
    @Operation(summary = "Lift an active ban")
    public ResponseEntity<ApiResponse<BanRecordDto>> liftBan(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long banId,
            @RequestParam String liftReason) {
        return ResponseEntity.ok(ApiResponse.success("Ban lifted",
                adminService.liftBan(banId, liftReason, myId(ud))));
    }

    @GetMapping("/users/{id}/bans")
    @Operation(summary = "Get ban history for a user")
    public ResponseEntity<ApiResponse<PagedResponse<BanRecordDto>>> getBanHistory(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getBanHistory(id, page, size)));
    }

    @GetMapping("/bans")
    @Operation(summary = "List all currently active bans")
    public ResponseEntity<ApiResponse<PagedResponse<BanRecordDto>>> getActiveBans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getActiveBans(page, size)));
    }

    // ── Listings ──────────────────────────────────────────────────────────────

    @GetMapping("/listings")
    @Operation(summary = "List all listings, optionally filtered by status")
    public ResponseEntity<ApiResponse<PagedResponse<ListingDto>>> listListings(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) ListingStatus status) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getListings(page, size, status)));
    }

    @PatchMapping("/listings/{id}/status")
    @Operation(summary = "Update a listing's status")
    public ResponseEntity<ApiResponse<ListingDto>> setListingStatus(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id,
            @RequestParam ListingStatus status) {
        return ResponseEntity.ok(ApiResponse.success("Listing status updated",
                adminService.updateListingStatus(id, status, myId(ud))));
    }

    // ── Orders ────────────────────────────────────────────────────────────────

    @GetMapping("/orders")
    @Operation(summary = "List all orders, optionally filtered by status")
    public ResponseEntity<ApiResponse<PagedResponse<OrderDto>>> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) OrderStatus status) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getOrders(page, size, status)));
    }

    // ── Payments ──────────────────────────────────────────────────────────────

    @GetMapping("/payments")
    @Operation(summary = "List all payments, optionally filtered by status")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentDto>>> listPayments(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) PaymentStatus status) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getPayments(page, size, status)));
    }

    // ── Reviews ───────────────────────────────────────────────────────────────

    @GetMapping("/reviews")
    @Operation(summary = "List all reviews")
    public ResponseEntity<ApiResponse<PagedResponse<ReviewDto>>> listReviews(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getReviews(page, size)));
    }

    @PatchMapping("/reviews/{id}/deactivate")
    @Operation(summary = "Hide a review")
    public ResponseEntity<ApiResponse<Void>> deactivateReview(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id) {
        adminService.moderateReview(id, false, myId(ud));
        return ResponseEntity.ok(ApiResponse.success("Review deactivated", null));
    }

    @PatchMapping("/reviews/{id}/reactivate")
    @Operation(summary = "Restore a hidden review")
    public ResponseEntity<ApiResponse<Void>> reactivateReview(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id) {
        adminService.moderateReview(id, true, myId(ud));
        return ResponseEntity.ok(ApiResponse.success("Review reactivated", null));
    }

    // ── Audit Logs ────────────────────────────────────────────────────────────

    @GetMapping("/audit-logs")
    @Operation(summary = "List all audit log entries")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAuditLogs(page, size)));
    }

    @GetMapping("/audit-logs/admin/{adminId}")
    @Operation(summary = "Audit logs by a specific admin")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getByAdmin(
            @PathVariable Long adminId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAuditLogsByAdmin(adminId, page, size)));
    }

    @GetMapping("/audit-logs/entity")
    @Operation(summary = "Audit logs for a specific entity (e.g. USER 42)")
    public ResponseEntity<ApiResponse<PagedResponse<AuditLogDto>>> getByEntity(
            @RequestParam String entityType,
            @RequestParam Long entityId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(ApiResponse.success(
                adminService.getAuditLogsByEntity(entityType, entityId, page, size)));
    }

    // ── Platform Config ───────────────────────────────────────────────────────

    @GetMapping("/config")
    @Operation(summary = "List all platform config settings")
    public ResponseEntity<ApiResponse<List<AdminConfigDto>>> getAllConfig() {
        return ResponseEntity.ok(ApiResponse.success(adminService.getAllConfigs()));
    }

    @GetMapping("/config/category/{category}")
    @Operation(summary = "List config settings by category")
    public ResponseEntity<ApiResponse<List<AdminConfigDto>>> getConfigByCategory(
            @PathVariable String category) {
        return ResponseEntity.ok(ApiResponse.success(adminService.getConfigsByCategory(category)));
    }

    @PutMapping("/config")
    @Operation(summary = "Create or update a config setting")
    public ResponseEntity<ApiResponse<AdminConfigDto>> upsertConfig(
            @AuthenticationPrincipal UserDetails ud,
            @Valid @RequestBody UpsertConfigRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Config saved",
                adminService.upsertConfig(request, myId(ud))));
    }

    @DeleteMapping("/config/{id}")
    @Operation(summary = "Delete a config setting")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(
            @AuthenticationPrincipal UserDetails ud,
            @PathVariable Long id) {
        adminService.deleteConfig(id, myId(ud));
        return ResponseEntity.ok(ApiResponse.success("Config deleted", null));
    }
}
