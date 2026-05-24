-- ============================================
-- AI Memory System 数据库表
-- ============================================

USE `oj_problem`;

-- ============================================
-- 表1：AI 成功案例表 (ai_success_case)
-- ============================================
CREATE TABLE `ai_success_case` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `problem_type`     VARCHAR(50)     NOT NULL COMMENT '题目类型：DP/贪心/图论/字符串/搜索/数学等',
    `algorithm_keyword` VARCHAR(200)   DEFAULT NULL COMMENT '算法关键词（逗号分隔）',
    `generation_strategy` TEXT         DEFAULT NULL COMMENT '使用的生成策略摘要',
    `testcase_count`   INT UNSIGNED    DEFAULT 0 COMMENT '生成的测试用例数量',
    `success_rate`     DECIMAL(5,2)    DEFAULT 100.00 COMMENT '验证通过率（0-100）',
    `problem_hash`      CHAR(64)        DEFAULT NULL COMMENT '题目内容哈希',
    `problem_title`     VARCHAR(255)    DEFAULT NULL COMMENT '题目标题',
    `context_summary`   TEXT           DEFAULT NULL COMMENT '生成时的上下文摘要',
    `solution_code_hash` CHAR(64)      DEFAULT NULL COMMENT 'solutionCode 哈希（用于去重相似案例）',
    `generation_duration_ms` INT UNSIGNED DEFAULT 0 COMMENT '生成耗时（毫秒）',
    `token_used`        INT UNSIGNED    DEFAULT 0 COMMENT '使用的 Token 数量',
    `model_name`        VARCHAR(100)    DEFAULT NULL COMMENT '使用的模型名称',
    `created_at`        DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_problem_type` (`problem_type`),
    KEY `idx_problem_hash` (`problem_hash`),
    KEY `idx_algorithm_keyword` (`algorithm_keyword`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 生成成功案例记录表';

-- ============================================
-- 表2：AI 失败案例表 (ai_failure_case)
-- ============================================
CREATE TABLE `ai_failure_case` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `problem_type`     VARCHAR(50)     NOT NULL COMMENT '题目类型',
    `failure_reason`   VARCHAR(200)   NOT NULL COMMENT '失败原因枚举',
    `failure_detail`   TEXT           DEFAULT NULL COMMENT '详细错误信息',
    `attempt_strategy` TEXT           DEFAULT NULL COMMENT '失败的策略',
    `lessons_learned`  TEXT           DEFAULT NULL COMMENT '从失败中总结的教训',
    `problem_hash`     CHAR(64)       DEFAULT NULL COMMENT '题目内容哈希',
    `problem_title`    VARCHAR(255)   DEFAULT NULL COMMENT '题目标题',
    `retry_count`      INT UNSIGNED    DEFAULT 1 COMMENT '重试次数',
    `final_error_type` VARCHAR(50)    DEFAULT NULL COMMENT '最终错误类型',
    `token_used`       INT UNSIGNED    DEFAULT 0 COMMENT '使用的 Token 数量',
    `model_name`       VARCHAR(100)    DEFAULT NULL COMMENT '使用的模型名称',
    `created_at`       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_problem_type` (`problem_type`),
    KEY `idx_failure_reason` (`failure_reason`),
    KEY `idx_problem_hash` (`problem_hash`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 生成失败案例记录表';

-- ============================================
-- 表3：AI 案例检索记录表 (ai_case_query_log)
-- ============================================
CREATE TABLE `ai_case_query_log` (
    `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    `query_type`       VARCHAR(50)    NOT NULL COMMENT '查询类型：SUCCESS/FAILURE/SIMILAR',
    `problem_hash`     CHAR(64)       DEFAULT NULL COMMENT '查询时的题目哈希',
    `problem_type`     VARCHAR(50)   DEFAULT NULL COMMENT '查询时的题目类型',
    `keywords`         VARCHAR(500)   DEFAULT NULL COMMENT '查询关键词',
    `result_count`     INT UNSIGNED   DEFAULT 0 COMMENT '返回结果数量',
    `hit_rate`         DECIMAL(5,2)   DEFAULT 0.00 COMMENT '命中相似案例的比率',
    `query_duration_ms` INT UNSIGNED DEFAULT 0 COMMENT '查询耗时（毫秒）',
    `used_in_generation` TINYINT     DEFAULT 0 COMMENT '是否用于实际生成',
    `created_at`       DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_query_type` (`query_type`),
    KEY `idx_problem_hash` (`problem_hash`),
    KEY `idx_problem_type` (`problem_type`),
    KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 案例检索日志表';

-- ============================================
-- 完成
-- ============================================
SELECT 'AI Memory System database schema created successfully! Total: 3 tables' AS message;
