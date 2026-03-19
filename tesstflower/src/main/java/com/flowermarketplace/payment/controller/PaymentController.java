package com.flowermarketplace.payment.controller;

import com.flowermarketplace.common.response.ApiResponse;
import com.flowermarketplace.common.response.PagedResponse;
import com.flowermarketplace.payment.dto.PaymentDto;
import com.flowermarketplace.payment.dto.PaymentRequest;
import com.flowermarketplace.payment.dto.RefundRequest;
import com.flowermarketplace.payment.service.PaymentService;
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

@RestController
@RequestMapping("/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment processing and refunds")
public class PaymentController {

    private final PaymentService paymentService;
    private final UserService    userService;

    // ── Process payment ──────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Process payment for an order")
    public ResponseEntity<ApiResponse<PaymentDto>> pay(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody PaymentRequest request) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        PaymentDto payment = paymentService.processPayment(me.getId(), request);
        return ResponseEntity.ok(ApiResponse.success("Payment processed", payment));
    }

    // ── Query ────────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID")
    public ResponseEntity<ApiResponse<PaymentDto>> getById(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentById(id, me.getId())));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get payment by order ID")
    public ResponseEntity<ApiResponse<PaymentDto>> getByOrder(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long orderId) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(paymentService.getPaymentByOrderId(orderId, me.getId())));
    }

    @GetMapping("/my")
    @Operation(summary = "Get my payment history")
    public ResponseEntity<ApiResponse<PagedResponse<PaymentDto>>> myPayments(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success(paymentService.getMyPayments(me.getId(), page, size)));
    }

    // ── Refund ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Admin: issue a full or partial refund")
    public ResponseEntity<ApiResponse<PaymentDto>> refund(
            @AuthenticationPrincipal UserDetails userDetails,
            @PathVariable Long id,
            @Valid @RequestBody RefundRequest request) {

        UserDto me = userService.getUserByEmail(userDetails.getUsername());
        PaymentDto payment = paymentService.refundPayment(id, request, me.getId());
        return ResponseEntity.ok(ApiResponse.success("Refund issued successfully", payment));
    }
}
