-- ══════════════════════════════════════════════════════════════════════════════
-- Flower Marketplace – Database Schema
-- MySQL 8.0+  |  UTF-8 MB4  |  InnoDB
-- ══════════════════════════════════════════════════════════════════════════════

CREATE DATABASE IF NOT EXISTS flower_marketplace
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

CREATE USER IF NOT EXISTS 'flower_user'@'%' IDENTIFIED BY 'flower_pass';
GRANT ALL PRIVILEGES ON flower_marketplace.* TO 'flower_user'@'%';
FLUSH PRIVILEGES;

USE flower_marketplace;

SET FOREIGN_KEY_CHECKS = 0;
SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. USERS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS users (
    id                       BIGINT          NOT NULL AUTO_INCREMENT,
    first_name               VARCHAR(100)    NOT NULL,
    last_name                VARCHAR(100)    NOT NULL,
    email                    VARCHAR(150)    NOT NULL,
    phone_number             VARCHAR(20)     NULL,
    password                 VARCHAR(255)    NOT NULL COMMENT 'BCrypt hash',
    role                     ENUM('ROLE_BUYER','ROLE_SELLER','ROLE_ADMIN')
                                             NOT NULL DEFAULT 'ROLE_BUYER',
    profile_image_url        VARCHAR(512)    NULL,
    city                     VARCHAR(100)    NULL,
    bio                      TEXT            NULL,
    enabled                  TINYINT(1)      NOT NULL DEFAULT 1,
    account_non_expired      TINYINT(1)      NOT NULL DEFAULT 1,
    account_non_locked       TINYINT(1)      NOT NULL DEFAULT 1,
    credentials_non_expired  TINYINT(1)      NOT NULL DEFAULT 1,
    created_at               DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at               DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                             ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE INDEX idx_user_email   (email),
    UNIQUE INDEX idx_user_phone   (phone_number),
    INDEX        idx_user_role    (role),
    INDEX        idx_user_enabled (enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Platform user accounts (buyers, sellers, admins)';


-- ─────────────────────────────────────────────────────────────────────────────
-- 2. ADDRESSES
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS addresses (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    user_id       BIGINT       NOT NULL,
    full_name     VARCHAR(100) NOT NULL,
    phone_number  VARCHAR(20)  NOT NULL,
    address_line  VARCHAR(255) NOT NULL,
    ward          VARCHAR(100) NULL,
    district      VARCHAR(100) NULL,
    city          VARCHAR(100) NOT NULL,
    country       VARCHAR(10)  NOT NULL DEFAULT 'VN',
    is_default    TINYINT(1)   NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    INDEX idx_addr_user    (user_id),
    INDEX idx_addr_default (user_id, is_default),

    CONSTRAINT fk_addr_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='User shipping addresses';


-- ─────────────────────────────────────────────────────────────────────────────
-- 3. REFRESH_TOKENS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS refresh_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    token       VARCHAR(512) NOT NULL,
    user_id     BIGINT       NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    revoked     TINYINT(1)   NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE INDEX idx_rt_token   (token),
    INDEX        idx_rt_user    (user_id),
    INDEX        idx_rt_expires (expires_at),
    INDEX        idx_rt_valid   (user_id, revoked, expires_at),

    CONSTRAINT fk_rt_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='JWT refresh tokens (stateful revocation)';


-- ─────────────────────────────────────────────────────────────────────────────
-- 4. LISTINGS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS listings (
    id              BIGINT          NOT NULL AUTO_INCREMENT,
    seller_id       BIGINT          NOT NULL,
    title           VARCHAR(200)    NOT NULL,
    description     TEXT            NULL,
    category        VARCHAR(50)     NOT NULL COMMENT 'ROSE,TULIP,SUNFLOWER,ORCHID,BOUQUET,DAISY,...',
    `condition`     VARCHAR(30)     NOT NULL COMMENT 'FRESH, DRIED, ARTIFICIAL',
    price           DECIMAL(15,2)   NOT NULL,
    stock_quantity  INT             NOT NULL DEFAULT 1,
    location        VARCHAR(200)    NULL,
    tags            VARCHAR(500)    NULL     COMMENT 'Comma-separated search tags',
    status          ENUM('ACTIVE','SOLD','ARCHIVED','PENDING_REVIEW')
                                    NOT NULL DEFAULT 'ACTIVE',
    free_delivery   TINYINT(1)      NOT NULL DEFAULT 0,
    average_rating  DOUBLE          NOT NULL DEFAULT 0.0,
    review_count    INT             NOT NULL DEFAULT 0,
    view_count      INT             NOT NULL DEFAULT 0,
    created_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)     NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    INDEX idx_listing_seller   (seller_id),
    INDEX idx_listing_status   (status),
    INDEX idx_listing_category (category),
    INDEX idx_listing_price    (price),
    INDEX idx_listing_location (location),
    INDEX idx_listing_created  (created_at),
    FULLTEXT INDEX ft_listing_search (title, description, tags),

    CONSTRAINT fk_listing_seller
        FOREIGN KEY (seller_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Flower product listings';


-- ─────────────────────────────────────────────────────────────────────────────
-- 5. LISTING_IMAGES  (JPA @ElementCollection)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS listing_images (
    listing_id  BIGINT       NOT NULL,
    image_url   VARCHAR(512) NOT NULL,
    sort_order  SMALLINT     NOT NULL DEFAULT 0 COMMENT '0 = primary image',

    INDEX idx_li_listing (listing_id),

    CONSTRAINT fk_li_listing
        FOREIGN KEY (listing_id) REFERENCES listings(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Listing product images (one row per URL)';


-- ─────────────────────────────────────────────────────────────────────────────
-- 6. ORDERS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS orders (
    id                   BIGINT         NOT NULL AUTO_INCREMENT,
    order_number         VARCHAR(30)    NOT NULL COMMENT 'e.g. ORD-20240315-0001',
    buyer_id             BIGINT         NOT NULL,
    status               ENUM('PENDING','CONFIRMED','PROCESSING','SHIPPED',
                              'DELIVERED','CANCELLED','REFUNDED')
                                        NOT NULL DEFAULT 'PENDING',
    total_amount         DECIMAL(15,2)  NOT NULL,
    shipping_address_id  BIGINT         NULL,
    delivery_type        VARCHAR(20)    NULL COMMENT 'TODAY, TOMORROW, SCHEDULE',
    note                 VARCHAR(500)   NULL,
    created_at           DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at           DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                        ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE INDEX idx_order_number  (order_number),
    INDEX        idx_order_buyer   (buyer_id),
    INDEX        idx_order_status  (status),
    INDEX        idx_order_created (created_at),

    CONSTRAINT fk_order_buyer
        FOREIGN KEY (buyer_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT fk_order_address
        FOREIGN KEY (shipping_address_id) REFERENCES addresses(id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Customer orders';


-- ─────────────────────────────────────────────────────────────────────────────
-- 7. ORDER_ITEMS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS order_items (
    id          BIGINT         NOT NULL AUTO_INCREMENT,
    order_id    BIGINT         NOT NULL,
    listing_id  BIGINT         NOT NULL,
    quantity    INT            NOT NULL DEFAULT 1,
    unit_price  DECIMAL(15,2)  NOT NULL COMMENT 'Price snapshot at order time',
    subtotal    DECIMAL(15,2)  NOT NULL COMMENT 'quantity x unit_price',

    PRIMARY KEY (id),
    INDEX idx_oi_order   (order_id),
    INDEX idx_oi_listing (listing_id),

    CONSTRAINT fk_oi_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_oi_listing
        FOREIGN KEY (listing_id) REFERENCES listings(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Individual line-items within an order';


-- ─────────────────────────────────────────────────────────────────────────────
-- 8. PAYMENTS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS payments (
    id                     BIGINT         NOT NULL AUTO_INCREMENT,
    order_id               BIGINT         NOT NULL,
    user_id                BIGINT         NOT NULL,
    amount                 DECIMAL(12,2)  NOT NULL,
    status                 ENUM('PENDING','COMPLETED','FAILED',
                                'REFUNDED','PARTIALLY_REFUNDED')
                                          NOT NULL DEFAULT 'PENDING',
    payment_method         VARCHAR(50)    NOT NULL COMMENT 'CARD,COD,BANK_TRANSFER,MOMO,ZALOPAY',
    transaction_id         VARCHAR(255)   NULL COMMENT 'External gateway charge ID',
    gateway_response       TEXT           NULL COMMENT 'Raw JSON from payment gateway',
    idempotency_key        VARCHAR(255)   NULL COMMENT 'Prevent double-charge on retry',
    refund_transaction_id  VARCHAR(255)   NULL,
    refund_amount          DECIMAL(12,2)  NULL,
    refunded_at            DATETIME(6)    NULL,
    created_at             DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at             DATETIME(6)    NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                          ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE INDEX idx_payment_order       (order_id),
    UNIQUE INDEX idx_payment_txn         (transaction_id),
    UNIQUE INDEX idx_payment_idempotency (idempotency_key),
    INDEX        idx_payment_user        (user_id),
    INDEX        idx_payment_status      (status),

    CONSTRAINT fk_payment_order
        FOREIGN KEY (order_id) REFERENCES orders(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT fk_payment_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Payment records (1-to-1 with orders)';


-- ─────────────────────────────────────────────────────────────────────────────
-- 9. REVIEWS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS reviews (
    id                 BIGINT      NOT NULL AUTO_INCREMENT,
    reviewer_id        BIGINT      NOT NULL,
    listing_id         BIGINT      NOT NULL,
    seller_id          BIGINT      NOT NULL COMMENT 'Denormalized for efficient seller stats',
    rating             TINYINT     NOT NULL COMMENT '1-5 stars',
    comment            TEXT        NULL,
    seller_reply       TEXT        NULL,
    seller_replied_at  DATETIME(6) NULL,
    active             TINYINT(1)  NOT NULL DEFAULT 1 COMMENT 'Soft-delete flag',
    created_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at         DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                   ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE INDEX uk_reviewer_listing (reviewer_id, listing_id),
    INDEX        idx_review_listing  (listing_id),
    INDEX        idx_review_seller   (seller_id),
    INDEX        idx_review_reviewer (reviewer_id),
    INDEX        idx_review_rating   (rating),
    INDEX        idx_review_active   (active),

    CONSTRAINT fk_review_reviewer
        FOREIGN KEY (reviewer_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_review_listing
        FOREIGN KEY (listing_id) REFERENCES listings(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_review_seller
        FOREIGN KEY (seller_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT chk_review_rating CHECK (rating BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Product reviews and seller replies';


-- ─────────────────────────────────────────────────────────────────────────────
-- 10. CONVERSATIONS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS conversations (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    user1_id            BIGINT      NOT NULL,
    user2_id            BIGINT      NOT NULL,
    last_message        TEXT        NULL,
    last_message_at     DATETIME(6) NULL,
    unread_count_user1  INT         NOT NULL DEFAULT 0,
    unread_count_user2  INT         NOT NULL DEFAULT 0,
    created_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at          DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                                    ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE INDEX uk_conv_participants (user1_id, user2_id),
    INDEX        idx_conv_user1       (user1_id),
    INDEX        idx_conv_user2       (user2_id),
    INDEX        idx_conv_last_msg    (last_message_at),

    CONSTRAINT fk_conv_user1
        FOREIGN KEY (user1_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_conv_user2
        FOREIGN KEY (user2_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT chk_conv_users CHECK (user1_id <> user2_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='1-to-1 chat conversations between users';


-- ─────────────────────────────────────────────────────────────────────────────
-- 11. CHAT_MESSAGES
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS chat_messages (
    id               BIGINT      NOT NULL AUTO_INCREMENT,
    conversation_id  BIGINT      NOT NULL,
    sender_id        BIGINT      NOT NULL,
    content          TEXT        NOT NULL,
    message_type     VARCHAR(20) NOT NULL DEFAULT 'TEXT' COMMENT 'TEXT, IMAGE, FILE',
    `read`           TINYINT(1)  NOT NULL DEFAULT 0,
    created_at       DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    INDEX idx_msg_conv    (conversation_id),
    INDEX idx_msg_sender  (sender_id),
    INDEX idx_msg_created (created_at),
    INDEX idx_msg_unread  (conversation_id, `read`),

    CONSTRAINT fk_msg_conversation
        FOREIGN KEY (conversation_id) REFERENCES conversations(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_msg_sender
        FOREIGN KEY (sender_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Chat messages within a conversation';


-- ─────────────────────────────────────────────────────────────────────────────
-- 12. NOTIFICATIONS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notifications (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    user_id         BIGINT       NOT NULL,
    type            ENUM(
                        'ORDER_PLACED','ORDER_CONFIRMED','ORDER_SHIPPED',
                        'ORDER_DELIVERED','ORDER_CANCELLED',
                        'PAYMENT_SUCCESS','PAYMENT_FAILED',
                        'REVIEW_RECEIVED','REVIEW_REPLIED',
                        'NEW_MESSAGE','LISTING_SOLD',
                        'LISTING_APPROVED','LISTING_REJECTED',
                        'ACCOUNT_BANNED','ACCOUNT_UNBANNED','SYSTEM'
                    ) NOT NULL,
    title           VARCHAR(255) NOT NULL,
    message         TEXT         NOT NULL,
    reference_id    VARCHAR(100) NULL COMMENT 'FK value of related entity',
    reference_type  VARCHAR(50)  NULL COMMENT 'ORDER, LISTING, REVIEW, PAYMENT',
    is_read         TINYINT(1)   NOT NULL DEFAULT 0,
    read_at         DATETIME(6)  NULL,
    created_at      DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    INDEX idx_notif_user    (user_id),
    INDEX idx_notif_read    (user_id, is_read),
    INDEX idx_notif_type    (type),
    INDEX idx_notif_created (created_at),

    CONSTRAINT fk_notif_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='In-app notifications for users';


-- ─────────────────────────────────────────────────────────────────────────────
-- 13. AUDIT_LOGS  (immutable – never updated)
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS audit_logs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    admin_id      BIGINT       NOT NULL,
    action        VARCHAR(100) NOT NULL COMMENT 'USER_ROLE_CHANGED, LISTING_ARCHIVED, ...',
    entity_type   VARCHAR(50)  NULL COMMENT 'USER, LISTING, ORDER, PAYMENT, REVIEW',
    entity_id     BIGINT       NULL,
    description   TEXT         NULL,
    before_value  TEXT         NULL COMMENT 'JSON snapshot before change',
    after_value   TEXT         NULL COMMENT 'JSON snapshot after change',
    ip_address    VARCHAR(45)  NULL COMMENT 'IPv4 or IPv6',
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    INDEX idx_audit_admin      (admin_id),
    INDEX idx_audit_action     (action),
    INDEX idx_audit_entity     (entity_type, entity_id),
    INDEX idx_audit_created_at (created_at),

    CONSTRAINT fk_audit_admin
        FOREIGN KEY (admin_id) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Immutable admin action audit trail';


-- ─────────────────────────────────────────────────────────────────────────────
-- 14. BAN_RECORDS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ban_records (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    user_id     BIGINT      NOT NULL,
    banned_by   BIGINT      NOT NULL COMMENT 'Admin who issued the ban',
    reason      TEXT        NOT NULL,
    ban_type    ENUM('PERMANENT','TEMPORARY') NOT NULL DEFAULT 'PERMANENT',
    expires_at  DATETIME(6) NULL COMMENT 'Non-null only for TEMPORARY bans',
    active      TINYINT(1)  NOT NULL DEFAULT 1,
    lifted_by   BIGINT      NULL COMMENT 'Admin who lifted the ban',
    lifted_at   DATETIME(6) NULL,
    lift_reason TEXT        NULL,
    created_at  DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    INDEX idx_ban_user    (user_id),
    INDEX idx_ban_admin   (banned_by),
    INDEX idx_ban_active  (user_id, active),
    INDEX idx_ban_expires (expires_at),

    CONSTRAINT fk_ban_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE ON UPDATE CASCADE,

    CONSTRAINT fk_ban_by
        FOREIGN KEY (banned_by) REFERENCES users(id)
        ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT fk_ban_lifted_by
        FOREIGN KEY (lifted_by) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='User ban and suspension records';


-- ─────────────────────────────────────────────────────────────────────────────
-- 15. ADMIN_CONFIGS
-- ─────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS admin_configs (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    config_key    VARCHAR(100) NOT NULL,
    config_value  TEXT         NOT NULL,
    description   TEXT         NULL,
    category      VARCHAR(50)  NOT NULL DEFAULT 'SYSTEM'
                               COMMENT 'PAYMENTS,LISTINGS,ORDERS,SYSTEM,NOTIFICATIONS',
    value_type    VARCHAR(20)  NOT NULL DEFAULT 'STRING'
                               COMMENT 'STRING,INTEGER,DECIMAL,BOOLEAN,JSON',
    sensitive     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT 'Mask in API responses if 1',
    updated_by    BIGINT       NULL,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
                               ON UPDATE CURRENT_TIMESTAMP(6),

    PRIMARY KEY (id),
    UNIQUE INDEX idx_config_key      (config_key),
    INDEX        idx_config_category (category),

    CONSTRAINT fk_config_updated_by
        FOREIGN KEY (updated_by) REFERENCES users(id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Runtime platform configuration (key-value store)';


-- ══════════════════════════════════════════════════════════════════════════════
-- SEED DATA – Default admin configs
-- ══════════════════════════════════════════════════════════════════════════════
INSERT INTO admin_configs (config_key, config_value, description, category, value_type) VALUES
    ('platform_fee_percent',       '5.0',   'Platform commission % deducted from each sale',       'PAYMENTS',       'DECIMAL'),
    ('max_listing_images',         '10',    'Maximum number of images per listing',                 'LISTINGS',       'INTEGER'),
    ('featured_listing_limit',     '20',    'Number of listings shown in featured section',         'LISTINGS',       'INTEGER'),
    ('min_review_order_required',  'true',  'Require completed order before allowing review',       'LISTINGS',       'BOOLEAN'),
    ('maintenance_mode',           'false', 'Put platform in read-only maintenance mode',           'SYSTEM',         'BOOLEAN'),
    ('max_addresses_per_user',     '5',     'Maximum saved addresses per user account',             'SYSTEM',         'INTEGER'),
    ('order_auto_complete_days',   '7',     'Auto-mark SHIPPED orders as DELIVERED after N days',  'ORDERS',         'INTEGER'),
    ('notification_retention_days','90',    'Days to retain read notifications before purging',     'NOTIFICATIONS',  'INTEGER'),
    ('refresh_token_expiry_days',  '7',     'Refresh token validity in days',                       'SYSTEM',         'INTEGER'),
    ('max_active_listings_seller', '50',    'Max simultaneous ACTIVE listings per seller',          'LISTINGS',       'INTEGER')
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value);


SET FOREIGN_KEY_CHECKS = 1;
