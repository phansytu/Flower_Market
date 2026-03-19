package com.flowermarketplace.payment.service;

import com.flowermarketplace.common.enums.NotificationType;
import com.flowermarketplace.common.enums.OrderStatus;
import com.flowermarketplace.common.enums.PaymentStatus;
import com.flowermarketplace.common.exception.BadRequestException;
import com.flowermarketplace.common.exception.ResourceNotFoundException;
import com.flowermarketplace.common.response.PagedResponse;
import com.flowermarketplace.notification.service.NotificationService;
import com.flowermarketplace.order.entity.Order;
import com.flowermarketplace.order.repository.OrderRepository;
import com.flowermarketplace.payment.dto.PaymentDto;
import com.flowermarketplace.payment.dto.PaymentRequest;
import com.flowermarketplace.payment.dto.RefundRequest;
import com.flowermarketplace.payment.entity.Payment;
import com.flowermarketplace.payment.mapper.PaymentMapper;
import com.flowermarketplace.payment.repository.PaymentRepository;
import com.flowermarketplace.user.entity.User;
import com.flowermarketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final PaymentRepository    paymentRepository;
    private final OrderRepository      orderRepository;
    private final UserRepository       userRepository;
    private final PaymentMapper        paymentMapper;
    private final PaymentGatewayService gateway;
    private final NotificationService  notificationService;

    // ------------------------------------------------------------------ charge

    @Transactional
    public PaymentDto processPayment(Long userId, PaymentRequest request) {

        // Idempotency guard ─ return existing payment if key already used
        if (request.getIdempotencyKey() != null) {
            var existing = paymentRepository.findByIdempotencyKey(request.getIdempotencyKey());
            if (existing.isPresent()) {
                log.info("Idempotent payment request, returning existing payment {}", existing.get().getId());
                return paymentMapper.toDto(existing.get());
            }
        }

        Order order = orderRepository.findById(request.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", request.getOrderId()));

        if (!order.getBuyer().getId().equals(userId)) {
            throw new BadRequestException("Order does not belong to this user.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Order is not in a payable state (current: " + order.getStatus() + ").");
        }
        if (paymentRepository.existsByOrderId(order.getId())) {
            throw new BadRequestException("A payment already exists for order " + order.getOrderNumber());
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));

        String idempotencyKey = request.getIdempotencyKey() != null
                ? request.getIdempotencyKey()
                : UUID.randomUUID().toString();

        // Call gateway
        String transactionId = gateway.charge(request.getPaymentToken(), order.getTotalAmount(), idempotencyKey);
        boolean success = transactionId != null;
        PaymentStatus status = success ? PaymentStatus.COMPLETED : PaymentStatus.FAILED;

        Payment payment = Payment.builder()
                .order(order)
                .user(user)
                .amount(order.getTotalAmount())
                .paymentMethod(request.getPaymentMethod())
                .transactionId(transactionId)
                .idempotencyKey(idempotencyKey)
                .gatewayResponse(success ? "charge_ok" : "charge_failed")
                .status(status)
                .build();

        payment = paymentRepository.save(payment);

        if (success) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);

            notificationService.createNotification(
                    userId, NotificationType.PAYMENT_SUCCESS,
                    "Payment Successful",
                    "Your payment of $" + order.getTotalAmount() + " for order " + order.getOrderNumber() + " was processed.",
                    order.getOrderNumber());

            log.info("Payment {} completed for order {}", payment.getId(), order.getOrderNumber());
        } else {
            notificationService.createNotification(
                    userId, NotificationType.PAYMENT_FAILED,
                    "Payment Failed",
                    "We could not process your payment for order " + order.getOrderNumber() + ". Please try again.",
                    order.getOrderNumber());

            log.warn("Payment FAILED for order {}", order.getOrderNumber());
        }

        return paymentMapper.toDto(payment);
    }

    // ------------------------------------------------------------------ refund

    @Transactional
    public PaymentDto refundPayment(Long paymentId, RefundRequest request, Long requesterId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            throw new BadRequestException("Only completed payments can be refunded.");
        }
        if (payment.getRefundTransactionId() != null) {
            throw new BadRequestException("Payment has already been refunded.");
        }
        if (request.getAmount().compareTo(payment.getAmount()) > 0) {
            throw new BadRequestException("Refund amount cannot exceed the original payment amount.");
        }

        String refundTxn = gateway.refund(payment.getTransactionId(), request.getAmount());
        if (refundTxn == null) {
            throw new BadRequestException("Refund failed at the payment gateway. Please try again.");
        }

        payment.setStatus(PaymentStatus.REFUNDED);
        payment.setRefundTransactionId(refundTxn);
        payment.setRefundAmount(request.getAmount());
        payment.setRefundedAt(LocalDateTime.now());
        payment.setGatewayResponse(payment.getGatewayResponse() + " | refund_ok");

        payment = paymentRepository.save(payment);

        // Move order to REFUNDED status
        Order order = payment.getOrder();
        order.setStatus(OrderStatus.REFUNDED);
        orderRepository.save(order);

        notificationService.createNotification(
                payment.getUser().getId(), NotificationType.PAYMENT_SUCCESS,
                "Refund Processed",
                "A refund of $" + request.getAmount() + " has been issued for order " + order.getOrderNumber() + ".",
                order.getOrderNumber());

        log.info("Refund {} issued for payment {}", refundTxn, paymentId);
        return paymentMapper.toDto(payment);
    }

    // ------------------------------------------------------------------ queries

    public PaymentDto getPaymentById(Long paymentId, Long userId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        if (!payment.getUser().getId().equals(userId)) {
            throw new BadRequestException("Payment does not belong to this user.");
        }
        return paymentMapper.toDto(payment);
    }

    public PaymentDto getPaymentByOrderId(Long orderId, Long userId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment for order", "orderId", orderId));
        if (!payment.getUser().getId().equals(userId)) {
            throw new BadRequestException("Payment does not belong to this user.");
        }
        return paymentMapper.toDto(payment);
    }

    public PagedResponse<PaymentDto> getMyPayments(Long userId, int page, int size) {
        var payments = paymentRepository.findByUserId(
                userId, PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PagedResponse.of(payments.map(paymentMapper::toDto));
    }

    // Admin
    public PagedResponse<PaymentDto> getAllPayments(int page, int size) {
        var payments = paymentRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending()));
        return PagedResponse.of(payments.map(paymentMapper::toDto));
    }

    public PaymentDto getPaymentByIdAdmin(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment", "id", paymentId));
        return paymentMapper.toDto(payment);
    }
}
