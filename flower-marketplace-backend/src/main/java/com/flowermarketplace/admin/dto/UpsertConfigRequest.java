package com.flowermarketplace.admin.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data

public class UpsertConfigRequest {

    @NotBlank(message = "Config key is required")
    private String configKey;

    @NotBlank(message = "Config value is required")
    private String configValue;

    private String description;
    private String category; // defaults to SYSTEM
    private String valueType; // STRING | INTEGER | DECIMAL | BOOLEAN | JSON
    private boolean sensitive;

}
