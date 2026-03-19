package com.flowermarketplace.admin.dto;

import com.flowermarketplace.admin.entity.BanRecord.BanType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BanRecordDto {
    private Long          id;
    private Long          userId;
    private String        userName;
    private Long          bannedById;
    private String        bannedByName;
    private String        reason;
    private BanType       banType;
    private LocalDateTime expiresAt;
    private boolean       active;
    private boolean       expired;
    private Long          liftedById;
    private String        liftedByName;
    private LocalDateTime liftedAt;
    private String        liftReason;
    private LocalDateTime createdAt;
}
