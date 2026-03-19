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
public class AdminConfigDto {
    private Long          id;
    private String        configKey;
    private String        configValue;   // masked as "***" when sensitive
    private String        description;
    private String        category;
    private String        valueType;
    private boolean       sensitive;
    private Long          updatedById;
    private String        updatedByName;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    
}
