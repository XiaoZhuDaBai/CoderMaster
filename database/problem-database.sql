-- ============================================
-- idolnoOJ 题目服务数据库 DDL
-- ============================================

-- 创建数据库
CREATE DATABASE IF NOT EXISTS `oj_problem`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `oj_problem`;

-- ============================================
-- 表1：题目表 (question)
-- ============================================
CREATE TABLE `question` (
                            `question_id`      BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '题目ID（自增主键）',
                            `question_code`    VARCHAR(64)     NOT NULL COMMENT '题目编号（业务ID，如L0001）',
                            `title`            VARCHAR(255)    NOT NULL COMMENT '题目标题',
                            `difficulty`       VARCHAR(16)         DEFAULT 1 COMMENT '难度：简单，中等，困难',
                            `question_type`    TINYINT         DEFAULT 0 COMMENT '类型：0-ACM，1-OI',
                            `source`           VARCHAR(100)    DEFAULT NULL COMMENT '来源（LeetCode/NowCoder等）',
                            `author_id`        BIGINT UNSIGNED DEFAULT NULL COMMENT '作者ID（关联user-service的user_id，无外键约束）',
                            `description`      TEXT            COMMENT '题目描述',
                            `input_desc`       TEXT            COMMENT '输入描述',
                            `output_desc`      TEXT            COMMENT '输出描述',
                            `examples`         JSON            COMMENT '样例（JSON格式）',
                            `time_limit`       INT UNSIGNED    DEFAULT 1000 COMMENT '时间限制（ms）',
                            `memory_limit`     INT UNSIGNED    DEFAULT 256 COMMENT '内存限制（MB）',
                            `stack_limit`      INT UNSIGNED    DEFAULT 128 COMMENT '栈限制（MB）',
                            `status`           TINYINT         DEFAULT 1 COMMENT '状态：0-下架，1-上架',
                            `version`          INT UNSIGNED    DEFAULT 1 COMMENT '题面版本号，发布后递增',
                            `published_time`   DATETIME        DEFAULT NULL COMMENT '最近一次发布上线时间',
                            `content_hash`     CHAR(64)        DEFAULT NULL COMMENT '题目内容哈希，用于关联测试用例',
                            `create_time`      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                            `update_time`      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                            PRIMARY KEY (`question_id`),
                            UNIQUE KEY `uk_question_code` (`question_code`),
                            KEY `idx_difficulty` (`difficulty`),
                            KEY `idx_source` (`source`),
                            KEY `idx_status` (`status`),
                            KEY `idx_version` (`version`),
                            KEY `idx_create_time` (`create_time`),
                            KEY `idx_author_id` (`author_id`),
                            KEY `idx_content_hash` (`content_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目表';

-- ============================================
-- 表2：标签表 (tag)
-- ============================================
CREATE TABLE `tag` (
                       `tag_id`           INT UNSIGNED    NOT NULL AUTO_INCREMENT COMMENT '标签ID',
                       `tag_name`         VARCHAR(50)     NOT NULL COMMENT '标签名称',
                       `tag_category`     VARCHAR(50)     DEFAULT NULL COMMENT '标签分类（算法/数据结构等）',
                       `usage_count`      INT UNSIGNED    DEFAULT 0 COMMENT '使用次数',
                       `create_time`      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                       PRIMARY KEY (`tag_id`),
                       UNIQUE KEY `uk_tag_name` (`tag_name`),
                       KEY `idx_category` (`tag_category`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

-- ============================================
-- 表3：题目标签关联表 (question_tag)
-- ============================================
CREATE TABLE `question_tag` (
                                `id`               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
                                `question_id`      BIGINT UNSIGNED NOT NULL COMMENT '题目ID',
                                `tag_id`           INT UNSIGNED    NOT NULL COMMENT '标签ID',
                                `create_time`      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                PRIMARY KEY (`id`),
                                UNIQUE KEY `uk_question_tag` (`question_id`, `tag_id`),
                                KEY `idx_tag_id` (`tag_id`),
                                KEY `idx_question_id` (`question_id`),
                                CONSTRAINT `fk_qt_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`question_id`) ON DELETE CASCADE,
                                CONSTRAINT `fk_qt_tag` FOREIGN KEY (`tag_id`) REFERENCES `tag` (`tag_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目标签关联表';

-- ============================================
-- 表4：题目测试用例表 (question_test_case)
-- ============================================
CREATE TABLE `question_test_case` (
                                      `case_id`          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '用例ID',
                                      `question_id`      BIGINT UNSIGNED NOT NULL COMMENT '题目ID',
                                      `case_index`       INT UNSIGNED    NOT NULL COMMENT '用例序号（从1开始）',
                                      `input`            LONGTEXT        NOT NULL COMMENT '标准输入',
                                      `expected_output`  LONGTEXT        NOT NULL COMMENT '标准输出',
                                      `is_public`        TINYINT         DEFAULT 0 COMMENT '是否公开：0-隐藏，1-公开',
                                      `case_type`        ENUM('SAMPLE','HIDDEN','EXTREME') DEFAULT 'HIDDEN' COMMENT '用例类型',
                                      `weight`           DECIMAL(5,2)    DEFAULT 1.00 COMMENT '该用例得分权重',
                                      `generation_source` VARCHAR(32)      DEFAULT 'AI' COMMENT '用例来源（AI/MANUAL/IMPORT等）',
                                      `version`          INT UNSIGNED    DEFAULT 1 COMMENT '用例版本号',
                                      `review_status`    TINYINT         DEFAULT 0 COMMENT '用例状态：0-草稿，1-审核中，2-已发布',
                                      `content_hash`     CHAR(64)        DEFAULT NULL COMMENT '测试用例内容哈希，用于关联',
                                      `time_limit`       INT UNSIGNED    DEFAULT NULL COMMENT '该用例时间限制（ms，NULL使用题目默认值）',
                                      `memory_limit`     INT UNSIGNED    DEFAULT NULL COMMENT '该用例内存限制（MB，NULL使用题目默认值）',
                                      `create_time`      DATETIME        DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time`      DATETIME        DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                      PRIMARY KEY (`case_id`),
                                      KEY `idx_question_id` (`question_id`),
                                      KEY `idx_question_index` (`question_id`, `case_index`),
                                      KEY `idx_is_public` (`is_public`),
                                      KEY `idx_case_type` (`case_type`),
                                      KEY `idx_version` (`version`),
                                      KEY `idx_content_hash` (`content_hash`),
                                      CONSTRAINT `fk_test_case_question` FOREIGN KEY (`question_id`) REFERENCES `question` (`question_id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='题目测试用例表（存储题目的标准测试用例）';

-- ============================================
-- 初始化标签数据
-- ============================================
-- 数据结构类标签
INSERT INTO `tag` (`tag_name`, `tag_category`) VALUES
                                                   ('数组', '数据结构'),
                                                   ('字符串', '数据结构'),
                                                   ('链表', '数据结构'),
                                                   ('栈', '数据结构'),
                                                   ('队列', '数据结构'),
                                                   ('树', '数据结构'),
                                                   ('图', '数据结构'),
                                                   ('哈希表', '数据结构'),
                                                   ('堆', '数据结构'),
                                                   ('并查集', '数据结构'),
                                                   ('字典树', '数据结构'),
                                                   ('线段树', '数据结构'),
                                                   ('树状数组', '数据结构'),
                                                   ('前缀和', '数据结构'),
                                                   ('差分数组', '数据结构'),
                                                   ('平衡二叉树', '数据结构');

-- 算法类标签
INSERT INTO `tag` (`tag_name`, `tag_category`) VALUES
                                                   ('动态规划', '算法'),
                                                   ('贪心', '算法'),
                                                   ('回溯', '算法'),
                                                   ('分治', '算法'),
                                                   ('排序', '算法'),
                                                   ('搜索', '算法'),
                                                   ('深度优先搜索', '算法'),
                                                   ('广度优先搜索', '算法'),
                                                   ('双指针', '算法'),
                                                   ('滑动窗口', '算法'),
                                                   ('位运算', '算法'),
                                                   ('数学', '算法'),
                                                   ('模拟', '算法'),
                                                   ('二分查找', '算法'),
                                                   ('拓扑排序', '算法'),
                                                   ('最短路径', '算法'),
                                                   ('最小生成树', '算法'),
                                                   ('字符串匹配', '算法'),
                                                   ('数论', '算法'),
                                                   ('组合数学', '算法'),
                                                   ('几何', '算法'),
                                                   ('博弈论', '算法'),
                                                   ('构造', '算法'),
                                                   ('交互', '算法');

-- ============================================
-- 视图：题目详情（包含标签）
-- ============================================
CREATE OR REPLACE VIEW `v_question_detail` AS
SELECT
    q.question_id,
    q.question_code,
    q.title,
    q.difficulty,
    q.version,
    q.source,
    q.status,
    GROUP_CONCAT(t.tag_name ORDER BY t.tag_name SEPARATOR ',') AS tags,
    q.create_time
FROM question q
         LEFT JOIN question_tag qt ON q.question_id = qt.question_id
         LEFT JOIN tag t ON qt.tag_id = t.tag_id
WHERE q.status = 1
GROUP BY q.question_id;

-- ============================================
-- 完成
-- ============================================
SELECT 'Problem database schema created successfully! Total: 3 tables' AS message;
