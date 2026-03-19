package com.flowermarketplace.admin.mapper;

import com.flowermarketplace.admin.dto.AdminConfigDto;
import com.flowermarketplace.admin.dto.AuditLogDto;
import com.flowermarketplace.admin.dto.BanRecordDto;
import com.flowermarketplace.admin.entity.AdminConfig;
import com.flowermarketplace.admin.entity.AuditLog;
import com.flowermarketplace.admin.entity.BanRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AdminMapper {

    // ── AuditLog ─────────────────────────────────────────────────────────────

    @Mapping(target = "adminId", source = "admin.id")
    @Mapping(target = "adminName", expression = "java(log.getAdmin().getFirstName() + \" \" + log.getAdmin().getLastName())")
    AuditLogDto toAuditLogDto(AuditLog log);

    // ── BanRecord ─────────────────────────────────────────────────────────────

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "userName", expression = "java(ban.getUser().getFirstName() + \" \" + ban.getUser().getLastName())")
    @Mapping(target = "bannedById", source = "bannedBy.id")
    @Mapping(target = "bannedByName", expression = "java(ban.getBannedBy().getFirstName() + \" \" + ban.getBannedBy().getLastName())")
    @Mapping(target = "liftedById", source = "liftedBy.id")
    @Mapping(target = "liftedByName", expression = "java(ban.getLiftedBy() != null ? ban.getLiftedBy().getFirstName() + \" \" + ban.getLiftedBy().getLastName() : null)")
    @Mapping(target = "expired", expression = "java(ban.isExpired())")
    BanRecordDto toBanRecordDto(BanRecord ban);

    // ── AdminConfig ───────────────────────────────────────────────────────────

    @Mapping(target = "updatedById", source = "updatedBy.id")
    @Mapping(target = "updatedByName", expression = "java(cfg.getUpdatedBy() != null ? cfg.getUpdatedBy().getFirstName() + \" \" + cfg.getUpdatedBy().getLastName() : null)")
    @Mapping(target = "configValue", expression = "java(cfg.isSensitive() ? \"***\" : cfg.getConfigValue())")
    AdminConfigDto toConfigDto(AdminConfig cfg);
}
