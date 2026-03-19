package com.flowermarketplace.admin.repository;

import com.flowermarketplace.admin.entity.AdminConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminConfigRepository extends JpaRepository<AdminConfig, Long> {

    Optional<AdminConfig> findByConfigKey(String configKey);

    List<AdminConfig> findByCategoryOrderByConfigKey(String category);

    boolean existsByConfigKey(String configKey);
}
