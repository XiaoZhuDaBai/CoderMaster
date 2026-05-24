-- ============================================
-- idolnoOJ 提交服务数据库 DDL
-- 版本：3.0
-- 说明：提交服务独立数据库，移除触发器，支持微服务架构
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `oj_submission`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `oj_submission`;

-- ============================================
-- 表1：提交记录表 (submission)
-- ============================================
CREATE TABLE `submission` (
                              `submission_id`    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '提交ID',
                              `user_id`          BIGINT UNSIGNED NOT NULL COMMENT '用户ID（关联user-service的user_id，无外键约束）',
                              `question_id`      BIGINT UNSIGNED NOT NULL COMMENT '题目ID（关联problem-service的question_id，无外键约束）',
                              `code`             TEXT            NOT NULL COMMENT '提交代码',
                              `language`         VARCHAR(20)     NOT NULL COMMENT '编程语言',
                              `judge_status`     ENUM('PENDING', 'JUDGING', 'AC', 'WA', 'TLE', 'MLE', 'RE', 'CE') DEFAULT 'PENDING' COMMENT '判题状态',
                              `judge_result`     JSON            DEFAULT NULL COMMENT '判题结果详情（JSON）',
                              `total_cases`      INT UNSIGNED    DEFAULT 0 COMMENT '总测试用例数',
                              `passed_cases`     INT UNSIGNED    DEFAULT 0 COMMENT '通过用例数',
                              `time_cost`        INT UNSIGNED    DEFAULT 0 COMMENT '耗时（ms）',
                              `memory_cost`      INT UNSIGNED    DEFAULT 0 COMMENT '内存（KB）',
                              `error_message`    TEXT            DEFAULT NULL COMMENT '错误信息',
                              `create_time`      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
                              `judge_time`       DATETIME        DEFAULT NULL COMMENT '判题完成时间',
                              PRIMARY KEY (`submission_id`),
                              KEY `idx_user_id` (`user_id`),
                              KEY `idx_question_id` (`question_id`),
                              KEY `idx_judge_status` (`judge_status`),
                              KEY `idx_create_time` (`create_time`),
                              KEY `idx_user_question_time` (`user_id`, `question_id`, `create_time`),
                              KEY `idx_cover_list` (`user_id`, `create_time`, `judge_status`, `question_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提交记录表';

-- ============================================
-- 表2：测试用例详情表 (submission_case)
-- ============================================
CREATE TABLE `submission_case` (
                                   `case_id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用例ID',
                                   `submission_id`    BIGINT UNSIGNED NOT NULL COMMENT '提交ID',
                                   `case_index`       INT UNSIGNED    NOT NULL COMMENT '用例序号（从1开始）',
                                   `actual_output`    TEXT            COMMENT '实际输出',
                                   `status`           ENUM('AC', 'WA', 'TLE', 'MLE', 'RE', 'CE') DEFAULT NULL COMMENT '状态',
                                   `case_version`     INT UNSIGNED    DEFAULT NULL COMMENT '用例版本号',
                                   `case_hash`        CHAR(64)        DEFAULT NULL COMMENT '用例内容哈希',
                                   `time_cost`        INT UNSIGNED    DEFAULT 0 COMMENT '耗时（ms）',
                                   `memory_cost`      INT UNSIGNED    DEFAULT 0 COMMENT '内存（KB）',
                                   `error_message`    TEXT            DEFAULT NULL COMMENT '错误信息',
                                   PRIMARY KEY (`case_id`),
                                   KEY `idx_submission_id` (`submission_id`),
                                   KEY `idx_submission_index` (`submission_id`, `case_index`),
                                   KEY `idx_case_hash` (`case_hash`),
                                   CONSTRAINT `fk_case_submission` FOREIGN KEY (`submission_id`) REFERENCES `submission` (`submission_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提交测试用例详情表';

-- ============================================
-- 重要说明
-- ============================================
-- 1. 已移除所有触发器，统计更新改为异步处理
-- 2. 建议使用 RabbitMQ 消息队列异步更新统计：
--    - submission 插入后发送消息到队列
--    - 消费者异步更新相关统计
-- 3. 定时任务批量修复统计数据，保证一致性
-- 4. 跨服务数据一致性通过最终一致性保证，而非强一致性

-- ============================================
-- 完成
-- ============================================
SELECT 'Submission database schema created successfully! Total: 2 tables' AS message;
