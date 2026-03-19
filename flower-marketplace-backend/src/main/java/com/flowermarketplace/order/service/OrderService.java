package com.flowermarketplace.order.service;

import com.flowermarketplace.common.enums.ListingStatus;
import com.flowermarketplace.common.enums.OrderStatus;
import com.flowermarketplace.common.exception.BadRequestException;
import com.flowermarketplace.common.exception.ResourceNotFoundException;
import com.flowermarketplace.common.exception.UnauthorizedException;
import com.flowermarketplace.common.response.PagedResponse;
import com.flowermarketplace.listing.entity.Listing;
import com.flowermarketplace.listing.repository.ListingRepository;
import com.flowermarketplace.order.dto.CreateOrderRequest;
import com.flowermarketplace.order.dto.OrderDto;
import com.flowermarketplace.order.entity.Order;
import com.flowermarketplace.order.entity.OrderItem;
import com.flowermarketplace.order.mapper.OrderMapper;
import com.flowermarketplace.order.repository.OrderRepository;
import com.flowermarketplace.user.entity.Address;
import com.flowermarketplace.user.entity.User;
import com.flowermarketplace.user.repository.AddressRepository;
import com.flowermarketplace.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final ListingRepository listingRepository;
    private final UserRepository userRepository;
    private final AddressRepository addressRepository;
    private final OrderMapper orderMapper;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public OrderDto createOrder(String buyerEmail, CreateOrderRequest request) {
        User buyer = userRepository.findByEmail(buyerEmail)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        Listing listing = listingRepository.findById(request.getListingId())
                .orElseThrow(() -> new ResourceNotFoundException("Listing", "id", request.getListingId()));

        if (listing.getStatus() != ListingStatus.ACTIVE) {
            throw new BadRequestException("Listing is not available.");
        }
        if (listing.getStockQuantity() < request.getQuantity()) {
            throw new BadRequestException("Insufficient stock.");
        }

        Address address = null;
        if (request.getShippingAddressId() != null) {
            address = addressRepository.findById(request.getShippingAddressId()).orElse(null);
        }

        BigDecimal unitPrice = listing.getPrice();
        BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(request.getQuantity()));

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .buyer(buyer)
                .status(OrderStatus.PENDING)
                .totalAmount(subtotal)
                .shippingAddress(address)
                .deliveryType(request.getDeliveryType())
                .note(request.getNote())
                .build();

        OrderItem item = OrderItem.builder()
                .order(order)
                .listing(listing)
                .quantity(request.getQuantity())
                .unitPrice(unitPrice)
                .subtotal(subtotal)
                .build();

        order.getItems().add(item);

        // Decrement stock
        listing.setStockQuantity(listing.getStockQuantity() - request.getQuantity());
        if (listing.getStockQuantity() == 0)
            listing.setStatus(ListingStatus.SOLD_OUT);
        listingRepository.save(listing);

        order = orderRepository.save(order);
        log.info("Order {} created by {}", order.getOrderNumber(), buyerEmail);
        return orderMapper.toDto(order);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public OrderDto getOrderById(Long id, String email) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        if (!order.getBuyer().getEmail().equals(email)) {
            throw new UnauthorizedException("Access denied.");
        }
        return orderMapper.toDto(order);
    }

    public PagedResponse<OrderDto> getMyOrders(String email, int page, int size) {
        User buyer = userRepository.findByEmail(email)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        Page<Order> result = orderRepository.findByBuyerIdOrderByCreatedAtDesc(
                buyer.getId(), PageRequest.of(page, size));
        return PagedResponse.of(result.map(orderMapper::toDto));
    }

    // ── Cancel ────────────────────────────────────────────────────────────────

    @Transactional
    public OrderDto cancelOrder(Long id, String email) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));

        if (!order.getBuyer().getEmail().equals(email)) {
            throw new UnauthorizedException("Access denied.");
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new BadRequestException("Only PENDING orders can be cancelled.");
        }

        order.setStatus(OrderStatus.CANCELLED);

        // Restore stock
        order.getItems().forEach(item -> {
            Listing l = item.getListing();
            l.setStockQuantity(l.getStockQuantity() + item.getQuantity());
            if (l.getStatus() == ListingStatus.SOLD_OUT)
                l.setStatus(ListingStatus.ACTIVE);
            listingRepository.save(l);
        });

        return orderMapper.toDto(orderRepository.save(order));
    }

    // ── Update status (seller/admin) ──────────────────────────────────────────

    @Transactional
    public OrderDto updateStatus(Long id, OrderStatus status) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", "id", id));
        order.setStatus(status);
        return orderMapper.toDto(orderRepository.save(order));
    }

    // ── Admin: all orders ─────────────────────────────────────────────────────

    public PagedResponse<OrderDto> getAllOrders(int page, int size, OrderStatus status) {
        Page<Order> result = (status != null)
                ? orderRepository.findByStatus(status, PageRequest.of(page, size))
                : orderRepository.findAll(PageRequest.of(page, size));
        return PagedResponse.of(result.map(orderMapper::toDto));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private String generateOrderNumber() {
        String date = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String random = String.format("%06d", ThreadLocalRandom.current().nextInt(1, 999999));
        return "ORD-" + date + "-" + random;
    }
}
