package com.flowermarketplace.notification.controller;

import com.flowermarketplace.common.response.ApiResponse;
import com.flowermarketplace.common.response.PagedResponse;
import com.flowermarketplace.notification.dto.NotificationDto;
import com.flowermarketplace.notification.service.NotificationService;
import com.flowermarketplace.user.dto.UserDto;
import com.flowermarketplace.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "In-app and email notifications")
public class NotificationController {

    private final NotificationService notifService;
    private final UserService         userService;

    @GetMapping
    @Operation(summary = "Get all notifications (paginated)")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationDto>>> getAll(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(notifService.getNotifications(me.getId(), page, size)));
    }

    @GetMapping("/unread")
    @Operation(summary = "Get unread notifications")
    public ResponseEntity<ApiResponse<PagedResponse<NotificationDto>>> getUnread(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(notifService.getUnread(me.getId(), page, size)));
    }

    @GetMapping("/unread/count")
    @Operation(summary = "Get count of unread notifications")
    public ResponseEntity<ApiResponse<Long>> unreadCount(
            @AuthenticationPrincipal UserDetails userDetails) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(notifService.getUnreadCount(me.getId())));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<ApiResponse<Integer>> markAllRead(
            @AuthenticationPrincipal UserDetails userDetails) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        int updated = notifService.markAllAsRead(me.getId());
        return ResponseEntity.ok(ApiResponse.success(updated + " notifications marked as read", updated));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark a single notification as read")
    public ResponseEntity<ApiResponse<NotificationDto>> markOneRead(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        NotificationDto dto = notifService.markOneAsRead(id, me.getId());
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", dto));
    }
}
