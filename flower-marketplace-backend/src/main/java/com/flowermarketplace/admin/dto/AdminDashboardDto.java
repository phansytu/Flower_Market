package com.flowermarketplace.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminDashboardDto {
    private long totalUsers;
    private long totalSellers;
    private long totalBuyers;
    private long totalListings;
    private long activeListings;
    private long totalOrders;
    private long pendingOrders;
    private long totalPayments;
    private BigDecimal totalRevenue;
    private long totalReviews;
}
