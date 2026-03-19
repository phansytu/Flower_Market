package com.flowermarketplace.notification.service;

import com.flowermarketplace.common.enums.NotificationType;
import com.flowermarketplace.notification.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationConsumer {

    private final NotificationService notificationService;
    private final EmailService        emailService;

    // ── Order events ─────────────────────────────────────────────────────────

    @RabbitListener(queues = "${rabbitmq.queue.order-status}", ackMode = "AUTO")
    public void handleOrderEvent(Map<String, Object> payload) {
        try {
            Long   buyerId    = Long.valueOf(payload.get("buyerId").toString());
            String orderNum   = (String) payload.get("orderNumber");
            String typeStr    = (String) payload.get("type");
            String buyerEmail = (String) payload.get("buyerEmail");
            String buyerName  = (String) payload.getOrDefault("buyerName", "Customer");
            String total      = payload.getOrDefault("totalAmount", "0.00").toString();
            String tracking   = (String) payload.get("trackingNumber");

            NotificationType type = NotificationType.valueOf(typeStr);
            String title   = buildTitle(type, orderNum);
            String message = buildMessage(type, orderNum);

            notificationService.createNotification(buyerId, type, title, message, orderNum, "ORDER");

            // Trigger emails for key lifecycle events
            switch (type) {
                case ORDER_CONFIRMED -> emailService.sendOrderConfirmation(buyerEmail, buyerName, orderNum, total);
                case ORDER_SHIPPED   -> emailService.sendOrderShipped(buyerEmail, buyerName, orderNum, tracking);
                default              -> { /* no email for other statuses */ }
            }

            log.info("Processed order event {} for order {}", type, orderNum);
        } catch (Exception e) {
            log.error("Failed to process order event: {} | payload={}", e.getMessage(), payload);
        }
    }

    // ── Generic notification events ───────────────────────────────────────────

    @RabbitListener(queues = "${rabbitmq.queue.push-notification}", ackMode = "AUTO")
    public void handlePushEvent(NotificationEvent event) {
        try {
            notificationService.handleEvent(event);
            log.info("Processed push event {} for user {}", event.getType(), event.getUserId());
        } catch (Exception e) {
            log.error("Failed to process push event: {}", e.getMessage());
        }
    }

    // ── Email-only events ────────────────────────────────────────────────────

    @RabbitListener(queues = "${rabbitmq.queue.email}", ackMode = "AUTO")
    public void handleEmailEvent(Map<String, Object> payload) {
        try {
            String to      = (String) payload.get("to");
            String subject = (String) payload.get("subject");
            String body    = (String) payload.get("body");
            boolean html   = Boolean.parseBoolean(payload.getOrDefault("html", "false").toString());

            if (html) {
                emailService.sendHtml(to, subject, body);
            } else {
                emailService.sendPlain(to, subject, body);
            }
        } catch (Exception e) {
            log.error("Failed to process email event: {}", e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String buildTitle(NotificationType type, String orderNum) {
        return switch (type) {
            case ORDER_PLACED    -> "Order Placed: "    + orderNum;
            case ORDER_CONFIRMED -> "Order Confirmed: " + orderNum;
            case ORDER_SHIPPED   -> "Your Order is Shipped!";
            case ORDER_DELIVERED -> "Order Delivered 🎉";
            case ORDER_CANCELLED -> "Order Cancelled: " + orderNum;
            case PAYMENT_SUCCESS -> "Payment Successful";
            case PAYMENT_FAILED  -> "Payment Failed";
            default              -> "Order Update";
        };
    }

    private String buildMessage(NotificationType type, String orderNum) {
        return switch (type) {
            case ORDER_PLACED    -> "Order " + orderNum + " placed successfully. We'll confirm it shortly.";
            case ORDER_CONFIRMED -> "Great news! Order " + orderNum + " has been confirmed.";
            case ORDER_SHIPPED   -> "Order " + orderNum + " is on its way to you!";
            case ORDER_DELIVERED -> "Order " + orderNum + " has been delivered. Enjoy your flowers!";
            case ORDER_CANCELLED -> "Order " + orderNum + " has been cancelled.";
            case PAYMENT_SUCCESS -> "Your payment for order " + orderNum + " was successful.";
            case PAYMENT_FAILED  -> "Payment for order " + orderNum + " failed. Please retry.";
            default              -> "There is an update on order " + orderNum + ".";
        };
    }
}
