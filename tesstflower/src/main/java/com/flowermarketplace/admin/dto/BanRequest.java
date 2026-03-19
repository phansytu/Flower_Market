package com.flowermarketplace.admin.dto;

import com.flowermarketplace.admin.entity.BanRecord.BanType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class BanRequest {

    @NotNull(message = "Ban type is required")
    private BanType banType;            // PERMANENT | TEMPORARY

    @NotBlank(message = "Reason is required")
    private String reason;

    /** Required when banType = TEMPORARY */
    private LocalDateTime expiresAt;
}
