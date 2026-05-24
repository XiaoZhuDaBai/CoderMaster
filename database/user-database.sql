-- ============================================
-- idolnoOJ 用户服务数据库 DDL
-- 版本：3.0
-- 说明：用户服务独立数据库，移除触发器，支持微服务架构
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `oj_user`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `oj_user`;

-- ============================================
-- 表1：用户表 (user)
-- ============================================
CREATE TABLE `user` (
                        `user_id`        BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用户ID（自增主键）',
                        `uuid`           VARCHAR(64)      NOT NULL COMMENT '用户UUID（业务ID）',
                        `openid`         VARCHAR(64)      NOT NULL COMMENT '第三方OpenID',
                        `unionid`        VARCHAR(64)      DEFAULT NULL COMMENT '第三方UnionID（跨应用唯一）',
                        `login_provider` VARCHAR(20)      NOT NULL DEFAULT 'WECHAT' COMMENT '登录提供方：WECHAT/QQ',
                        `email`          VARCHAR(255)     DEFAULT NULL COMMENT '邮箱（可选）',
                        `phone`          VARCHAR(32)      DEFAULT NULL COMMENT '手机号（可选）',
                        `nickname`       VARCHAR(100)     DEFAULT NULL COMMENT '昵称',
                        `avatar`         VARCHAR(500)     DEFAULT NULL COMMENT '头像URL',
                        `gender`         TINYINT          DEFAULT 0 COMMENT '性别：0-未知，1-男，2-女',
                        `is_admin`       TINYINT          DEFAULT 0 COMMENT '是否管理员：0-否，1-是',
                        `user_type`      ENUM('NORMAL','INTERNAL','ROBOT','BANNED') DEFAULT 'NORMAL' COMMENT '用户类型',
                        `ban_reason`     VARCHAR(255)     DEFAULT NULL COMMENT '禁用原因/备注',
                        `deactivated_at` DATETIME         DEFAULT NULL COMMENT '注销/禁用时间',
                        `last_login_time` DATETIME        DEFAULT NULL COMMENT '最近登录时间',
                        `last_login_ip`  VARCHAR(45)      DEFAULT NULL COMMENT '最近登录 IP',
                        `create_time`    DATETIME         DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                        `update_time`    DATETIME         DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                        PRIMARY KEY (`user_id`),
                        UNIQUE KEY `uk_uuid` (`uuid`),
                        UNIQUE KEY `uk_unionid` (`unionid`),
                        UNIQUE KEY `uk_openid_provider` (`openid`, `login_provider`),
                        KEY `idx_user_type` (`user_type`),
                        KEY `idx_is_admin` (`is_admin`),
                        KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ============================================
-- 完成
-- ============================================
SELECT 'User database schema created successfully! Total: 1 table' AS message;
