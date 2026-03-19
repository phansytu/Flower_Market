package com.flowermarketplace.notification.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@flowermarketplace.com}")
    private String fromAddress;

    // ── Generic send ────────────────────────────────────────────────────────

    @Async
    public void sendHtml(String to, String subject, String htmlBody) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true, "UTF-8");
            helper.setFrom(fromAddress, "Flower Marketplace");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(msg);
            log.info("Email sent → {} | {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
        }
    }

    @Async
    public void sendPlain(String to, String subject, String body) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, false, "UTF-8");
            helper.setFrom(fromAddress, "Flower Marketplace");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            mailSender.send(msg);
            log.info("Plain email sent → {} | {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send plain email to {}: {}", to, e.getMessage());
        }
    }

    // ── Templated helpers ────────────────────────────────────────────────────

    @Async
    public void sendWelcome(String to, String firstName) {
        String html = """
                <h2>Welcome to Flower Marketplace, %s! 🌸</h2>
                <p>We're thrilled to have you on board.</p>
                <p>Browse beautiful flower listings, connect with talented florists,
                and make someone's day special.</p>
                <br><p>Happy shopping!</p>
                <p><strong>The Flower Marketplace Team</strong></p>
                """.formatted(firstName);
        sendHtml(to, "Welcome to Flower Marketplace! 🌸", html);
    }

    @Async
    public void sendOrderConfirmation(String to, String buyerName, String orderNumber, String total) {
        String html = """
                <h2>Order Confirmed 🎉</h2>
                <p>Hi %s,</p>
                <p>Your order <strong>%s</strong> has been confirmed!</p>
                <p>Total charged: <strong>$%s</strong></p>
                <p>We'll notify you as soon as your flowers are on the way.</p>
                <br><p>Thank you for shopping with <strong>Flower Marketplace</strong>.</p>
                """.formatted(buyerName, orderNumber, total);
        sendHtml(to, "Order Confirmed – " + orderNumber, html);
    }

    @Async
    public void sendOrderShipped(String to, String buyerName, String orderNumber, String tracking) {
        String trackingLine = tracking != null
                ? "<p>Tracking number: <strong>" + tracking + "</strong></p>"
                : "";
        String html = """
                <h2>Your Order is on the Way 🚚</h2>
                <p>Hi %s,</p>
                <p>Great news! Order <strong>%s</strong> has been shipped.</p>
                %s
                <br><p><strong>Flower Marketplace Team</strong></p>
                """.formatted(buyerName, orderNumber, trackingLine);
        sendHtml(to, "Order Shipped – " + orderNumber, html);
    }

    @Async
    public void sendPasswordReset(String to, String firstName, String resetLink) {
        String html = """
                <h2>Password Reset Request</h2>
                <p>Hi %s,</p>
                <p>Click the link below to reset your password. This link expires in 1 hour.</p>
                <p><a href="%s">Reset My Password</a></p>
                <p>If you did not request this, please ignore this email.</p>
                """.formatted(firstName, resetLink);
        sendHtml(to, "Reset Your Password – Flower Marketplace", html);
    }

    @Async
    public void sendRefundConfirmation(String to, String buyerName, String orderNumber, String amount) {
        String html = """
                <h2>Refund Processed 💳</h2>
                <p>Hi %s,</p>
                <p>A refund of <strong>$%s</strong> for order <strong>%s</strong> has been issued.</p>
                <p>Funds typically appear within 3–5 business days.</p>
                <br><p><strong>Flower Marketplace Team</strong></p>
                """.formatted(buyerName, amount, orderNumber);
        sendHtml(to, "Refund Confirmation – " + orderNumber, html);
    }
}
