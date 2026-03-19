package com.flowermarketplace.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogDto {
    private Long          id;
    private Long          adminId;
    private String        adminName;
    private String        action;
    private String        entityType;
    private Long          entityId;
    private String        description;
    private String        beforeValue;
    private String        afterValue;
    private String        ipAddress;
    private LocalDateTime createdAt;
}
