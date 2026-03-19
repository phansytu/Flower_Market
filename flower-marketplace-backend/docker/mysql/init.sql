-- ─────────────────────────────────────────────────────────────────
-- Flower Marketplace – MySQL initialisation
-- ─────────────────────────────────────────────────────────────────

CREATE DATABASE IF NOT EXISTS flower_marketplace
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON flower_marketplace.* TO 'root'@'%';
FLUSH PRIVILEGES;

USE flower_marketplace;
flower_marketplace
