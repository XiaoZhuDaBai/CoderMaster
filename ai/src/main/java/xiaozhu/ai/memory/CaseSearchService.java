package xiaozhu.ai.memory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI 案例检索服务
 *
 * 功能：
 * 1. 存储成功/失败案例
 * 2. 检索相似成功案例（用于生成策略参考）
 * 3. 检索失败案例（用于避免重复失败）
 * 4. 记录检索日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaseSearchService {

    private final SuccessCaseMapper successCaseMapper;
    private final FailureCaseMapper failureCaseMapper;
    private final CaseQueryLogMapper caseQueryLogMapper;

    /**
     * 记录成功案例
     */
    public void recordSuccessCase(SuccessCase successCase) {
        log.info("[CaseSearchService] 记录成功案例，problemHash={}, problemType={}",
                successCase.getProblemHash(), successCase.getProblemType());
        successCaseMapper.insert(successCase);
    }

    /**
     * 记录失败案例
     */
    public void recordFailureCase(FailureCase failureCase) {
        log.info("[CaseSearchService] 记录失败案例，problemHash={}, failureReason={}",
                failureCase.getProblemHash(), failureCase.getFailureReason());
        failureCaseMapper.insert(failureCase);
    }

    /**
     * 检索相似成功案例
     * 
     * 检索策略（按优先级尝试）：
     * 1. 精确匹配 problemType + 关键词 LIKE 匹配
     * 2. 降级：仅关键词 LIKE 匹配（忽略 problemType）
     * 3. 降级：使用通用类型 "SEARCH" 或 "ARRAY" 等模糊匹配
     *
     * @param problemType 题目类型
     * @param algorithmKeyword 算法关键词
     * @param limit 返回数量限制
     * @return 相似成功案例列表
     */
    public List<SuccessCase> findSimilarSuccessCases(String problemType, String algorithmKeyword, int limit) {
        long startTime = System.currentTimeMillis();
        log.info("[CaseSearchService] 检索相似成功案例，problemType={}, keyword={}, limit={}",
                problemType, algorithmKeyword, limit);

        List<SuccessCase> result = new ArrayList<>();

        // 策略1: 精确匹配 problemType + 关键词
        if (problemType != null && !problemType.isBlank()) {
            result = searchByProblemTypeAndKeywords(problemType, algorithmKeyword, limit, true);
            if (!result.isEmpty()) {
                log.info("[CaseSearchService] 精确匹配找到 {} 条结果", result.size());
                recordQueryLog("SUCCESS", null, problemType, algorithmKeyword, result.size());
                return result;
            }
        }

        // 策略2: 仅关键词匹配（降级）
        if (algorithmKeyword != null && !algorithmKeyword.isBlank()) {
            result = searchByKeywordsOnly(algorithmKeyword, limit);
            if (!result.isEmpty()) {
                log.info("[CaseSearchService] 关键词匹配找到 {} 条结果", result.size());
                recordQueryLog("SUCCESS_KEYWORD_ONLY", null, problemType, algorithmKeyword, result.size());
                return result;
            }
        }

        // 策略3: 通用类型模糊匹配（降级）
        result = searchByGeneralTypes(limit);
        log.info("[CaseSearchService] 检索成功案例完成，找到 {} 条结果（降级策略），耗时 {}ms",
                result.size(), System.currentTimeMillis() - startTime);
        recordQueryLog("SUCCESS_GENERAL", null, problemType, algorithmKeyword, result.size());

        return result;
    }

    /**
     * 策略1: 精确匹配 problemType + 关键词
     */
    private List<SuccessCase> searchByProblemTypeAndKeywords(String problemType, String algorithmKeyword, int limit, boolean useAndCondition) {
        QueryWrapper<SuccessCase> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("problem_type", problemType)
                .orderByDesc("success_rate")
                .orderByDesc("created_at")
                .last("LIMIT " + limit);

        if (algorithmKeyword != null && !algorithmKeyword.isBlank()) {
            if (useAndCondition) {
                queryWrapper.and(w -> {
                    for (String keyword : algorithmKeyword.split(",")) {
                        String kw = keyword.trim();
                        if (!kw.isEmpty()) {
                            w.and(inner -> inner.like("algorithm_keyword", kw)
                                    .or().like("problem_title", kw));
                        }
                    }
                });
            }
        }

        return successCaseMapper.selectList(queryWrapper);
    }

    /**
     * 策略2: 仅关键词匹配（忽略 problemType）
     */
    private List<SuccessCase> searchByKeywordsOnly(String algorithmKeyword, int limit) {
        QueryWrapper<SuccessCase> queryWrapper = new QueryWrapper<>();
        
        queryWrapper.and(w -> {
            for (String keyword : algorithmKeyword.split(",")) {
                String kw = keyword.trim();
                if (!kw.isEmpty()) {
                    w.or().like("algorithm_keyword", kw)
                            .or().like("problem_title", kw)
                            .or().like("generation_strategy", kw);
                }
            }
        });
        
        return successCaseMapper.selectList(queryWrapper
                .orderByDesc("success_rate")
                .orderByDesc("created_at")
                .last("LIMIT " + limit));
    }

    /**
     * 策略3: 通用类型模糊匹配
     */
    private List<SuccessCase> searchByGeneralTypes(int limit) {
        // 通用类型列表：搜索、数组、字符串等常见类型
        String[] generalTypes = {"SEARCH", "ARRAY", "STRING", "DP", "GREEDY", "MATH"};
        
        QueryWrapper<SuccessCase> queryWrapper = new QueryWrapper<>();
        queryWrapper.in("problem_type", (Object[]) generalTypes)
                .orderByDesc("success_rate")
                .orderByDesc("created_at")
                .last("LIMIT " + limit);

        return successCaseMapper.selectList(queryWrapper);
    }

    /**
     * 检索相似失败案例
     * 
     * 检索策略（按优先级尝试）：
     * 1. 精确匹配 problemType + 失败原因 LIKE 匹配
     * 2. 降级：仅 problemType 匹配
     * 3. 降级：使用通用类型模糊匹配
     *
     * @param problemType 题目类型
     * @param failureReason 失败原因（可为空，表示查询所有失败案例）
     * @param limit 返回数量限制
     * @return 相似失败案例列表
     */
    public List<FailureCase> findSimilarFailureCases(String problemType, String failureReason, int limit) {
        long startTime = System.currentTimeMillis();
        log.info("[CaseSearchService] 检索相似失败案例，problemType={}, failureReason={}, limit={}",
                problemType, failureReason, limit);

        List<FailureCase> result = new ArrayList<>();

        // 策略1: 精确匹配 problemType + 失败原因
        if (problemType != null && !problemType.isBlank()) {
            result = searchFailureByProblemTypeAndReason(problemType, failureReason, limit);
            if (!result.isEmpty()) {
                log.info("[CaseSearchService] 精确匹配找到 {} 条失败案例", result.size());
                recordQueryLog("FAILURE", null, problemType, failureReason, result.size());
                return result;
            }
        }

        // 策略2: 仅 problemType 匹配（降级）
        if (problemType != null && !problemType.isBlank()) {
            result = searchFailureByProblemTypeOnly(problemType, limit);
            if (!result.isEmpty()) {
                log.info("[CaseSearchService] problemType 匹配找到 {} 条失败案例", result.size());
                recordQueryLog("FAILURE_TYPE_ONLY", null, problemType, failureReason, result.size());
                return result;
            }
        }

        // 策略3: 通用类型模糊匹配（降级）
        result = searchFailureByGeneralTypes(limit);
        log.info("[CaseSearchService] 检索失败案例完成，找到 {} 条结果（降级策略），耗时 {}ms",
                result.size(), System.currentTimeMillis() - startTime);
        recordQueryLog("FAILURE_GENERAL", null, problemType, failureReason, result.size());

        return result;
    }

    /**
     * 策略1: 精确匹配 problemType + 失败原因
     */
    private List<FailureCase> searchFailureByProblemTypeAndReason(String problemType, String failureReason, int limit) {
        LambdaQueryWrapper<FailureCase> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FailureCase::getProblemType, problemType)
                .orderByDesc(FailureCase::getRetryCount)
                .orderByDesc(FailureCase::getCreatedAt)
                .last("LIMIT " + limit);

        if (failureReason != null && !failureReason.isBlank()) {
            queryWrapper.and(w -> {
                w.like(FailureCase::getFailureReason, failureReason)
                        .or().like(FailureCase::getFailureDetail, failureReason)
                        .or().like(FailureCase::getLessonsLearned, failureReason);
            });
        }

        return failureCaseMapper.selectList(queryWrapper);
    }

    /**
     * 策略2: 仅 problemType 匹配
     */
    private List<FailureCase> searchFailureByProblemTypeOnly(String problemType, int limit) {
        LambdaQueryWrapper<FailureCase> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(FailureCase::getProblemType, problemType)
                .orderByDesc(FailureCase::getRetryCount)
                .orderByDesc(FailureCase::getCreatedAt)
                .last("LIMIT " + limit);

        return failureCaseMapper.selectList(queryWrapper);
    }

    /**
     * 策略3: 通用类型模糊匹配
     */
    private List<FailureCase> searchFailureByGeneralTypes(int limit) {
        String[] generalTypes = {"SEARCH", "ARRAY", "STRING", "DP", "GREEDY", "MATH"};
        
        LambdaQueryWrapper<FailureCase> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(FailureCase::getProblemType, (Object[]) generalTypes)
                .orderByDesc(FailureCase::getRetryCount)
                .orderByDesc(FailureCase::getCreatedAt)
                .last("LIMIT " + limit);

        return failureCaseMapper.selectList(queryWrapper);
    }

    /**
     * 检查是否存在重复的 solutionCode（通过哈希去重）
     * @deprecated 此方法未被使用，如需启用请确保业务逻辑正确
     */
    @Deprecated
    public boolean hasSimilarSuccessCase(String solutionCodeHash) {
        if (solutionCodeHash == null || solutionCodeHash.isBlank()) {
            return false;
        }
        LambdaQueryWrapper<SuccessCase> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SuccessCase::getSolutionCodeHash, solutionCodeHash);
        return successCaseMapper.selectCount(queryWrapper) > 0;
    }

    /**
     * 获取最近的成功案例（用于统计和调试）
     * @deprecated 此方法未被使用，如需启用请确保业务逻辑正确
     */
    @Deprecated
    public List<SuccessCase> findRecentSuccessCases(int limit) {
        LambdaQueryWrapper<SuccessCase> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(SuccessCase::getCreatedAt)
                .last("LIMIT " + limit);
        return successCaseMapper.selectList(queryWrapper);
    }

    /**
     * 获取最近的失败案例（用于统计和调试）
     * @deprecated 此方法未被使用，如需启用请确保业务逻辑正确
     */
    @Deprecated
    public List<FailureCase> findRecentFailureCases(int limit) {
        LambdaQueryWrapper<FailureCase> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.orderByDesc(FailureCase::getCreatedAt)
                .last("LIMIT " + limit);
        return failureCaseMapper.selectList(queryWrapper);
    }

    /**
     * 获取某类型的失败率
     * @deprecated 此方法未被使用，如需启用请确保业务逻辑正确
     */
    @Deprecated
    public BigDecimal getFailureRateByProblemType(String problemType) {
        Long totalCount = successCaseMapper.selectCount(
                new LambdaQueryWrapper<SuccessCase>().eq(SuccessCase::getProblemType, problemType)
        );
        Long failureCount = failureCaseMapper.selectCount(
                new LambdaQueryWrapper<FailureCase>().eq(FailureCase::getProblemType, problemType)
        );

        if (totalCount + failureCount == 0) {
            return BigDecimal.ZERO;
        }

        return BigDecimal.valueOf(failureCount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalCount + failureCount), 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * 记录检索日志
     */
    private void recordQueryLog(String queryType, String problemHash, String problemType,
                                 String keywords, int resultCount) {
        try {
            CaseQueryLog queryLog = new CaseQueryLog();
            queryLog.setQueryType(queryType);
            queryLog.setProblemHash(problemHash);
            queryLog.setProblemType(problemType);
            queryLog.setKeywords(keywords);
            queryLog.setResultCount(resultCount);
            queryLog.setHitRate(BigDecimal.valueOf(resultCount > 0 ? 100 : 0));
            queryLog.setQueryDurationMs(0);
            queryLog.setUsedInGeneration(false);
            caseQueryLogMapper.insert(queryLog);
        } catch (Exception e) {
            log.warn("[CaseSearchService] 记录检索日志失败: {}", e.getMessage());
        }
    }

    /**
     * 构建成功案例摘要（用于传递给 Agent 的上下文）
     */
    public String buildSuccessCaseSummary(List<SuccessCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return "无相似成功案例";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("【相似成功案例】共 ").append(cases.size()).append(" 条：\n\n");

        for (int i = 0; i < Math.min(cases.size(), 3); i++) {
            SuccessCase c = cases.get(i);
            summary.append("案例 ").append(i + 1).append("：\n");
            summary.append("- 题目类型：").append(c.getProblemType()).append("\n");
            summary.append("- 算法关键词：").append(c.getAlgorithmKeyword()).append("\n");
            summary.append("- 成功率：").append(c.getSuccessRate()).append("%\n");
            summary.append("- 策略摘要：").append(c.getGenerationStrategy()).append("\n");
            summary.append("\n");
        }

        return summary.toString();
    }

    /**
     * 构建失败案例摘要（用于传递给 Agent 的上下文）
     */
    public String buildFailureCaseSummary(List<FailureCase> cases) {
        if (cases == null || cases.isEmpty()) {
            return "无相似失败案例";
        }

        StringBuilder summary = new StringBuilder();
        summary.append("【相似失败案例】共 ").append(cases.size()).append(" 条：\n\n");

        for (int i = 0; i < Math.min(cases.size(), 3); i++) {
            FailureCase c = cases.get(i);
            summary.append("案例 ").append(i + 1).append("：\n");
            summary.append("- 失败原因：").append(c.getFailureReason()).append("\n");
            summary.append("- 教训：").append(c.getLessonsLearned()).append("\n");
            summary.append("\n");
        }

        return summary.toString();
    }
}
