package xiaozhu.ai.service.problem;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import xiaozhu.ai.metrics.AiMetricsService;
import xiaozhu.ai.model.ProblemGenerationRequest;
import xiaozhu.ai.service.llm.QuestionGenerationAiService;
import xiaozhu.ai.util.CalculateHash;
import xiaozhu.ai.util.ExtractJsonFromUtil;
import xiaozhu.common.constant.RabbitMQConstants;
import xiaozhu.common.constant.RedisKeyConstant;
import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.common.message.ProblemGeneratedMessage;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import static xiaozhu.ai.util.CalculateHash.calculateContentHash;

/**
 * 题目生成服务实现类
 */
@Slf4j
@Service
public class ProblemServiceImpl implements ProblemService {

    private static final String QUESTION_GENERATION_COMMAND = "请严格按照系统指令输出题目JSON，勿添加额外说明。";
    private static final long CACHE_TTL_DAYS = 7L;

    private final RedisTemplate<String, Object> redisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final QuestionGenerationAiService questionGenerationAiService;
    private final AiMetricsService aiMetricsService;

    public ProblemServiceImpl(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("chatModelPrototype") ChatModel chatModel,
            RabbitTemplate rabbitTemplate,
            AiMetricsService aiMetricsService) {
        this.redisTemplate = redisTemplate;
        this.rabbitTemplate = rabbitTemplate;
        this.aiMetricsService = aiMetricsService;
        this.questionGenerationAiService = AiServices.builder(QuestionGenerationAiService.class)
                .chatModel(chatModel)
                .build();
    }

    @Override
    public List<ProblemGenerationResponse> generateBatchProblems(ProblemGenerationRequest request) {
        if (request == null) {
            return Collections.emptyList();
        }

        int targetCount = (request.getNumber() != null && request.getNumber() > 0) 
                ? request.getNumber() 
                : 1;

        String userKey = resolveUserKey(request);
        return generateUniqueProblems(request, targetCount, userKey);
    }

    @Override
    @Async("problemGenerationExecutor")
    public void generateBatchProblemsAsync(ProblemGenerationRequest request) {
        String userKey = null;
        try {
            userKey = resolveUserKey(request);
            log.info("开始生成题目，userKey={}, number={}", userKey, request.getNumber());
            
            List<ProblemGenerationResponse> results = generateBatchProblems(request);
            
            log.info("题目生成完成，userKey={}, 成功生成 {} 个题目", userKey, results.size());
        } catch (IllegalArgumentException e) {
            log.error("生成题目失败：{}", e.getMessage());
        } catch (Exception e) {
            log.error("生成题目失败，userKey={}, error={}",
                    userKey != null ? userKey : "unknown",
                    e.getMessage(), e);
        }
    }

    /**
     * 生成指定数量的唯一题目
     */
    private List<ProblemGenerationResponse> generateUniqueProblems(ProblemGenerationRequest request, int targetCount, String userKey) {
        List<ProblemGenerationResponse> results = Collections.synchronizedList(new ArrayList<>());
        Set<String> contentHashes = ConcurrentHashMap.newKeySet();
        int maxRetries = targetCount * 3;
        int maxConcurrency = Math.min(3, targetCount);

        log.info("开始批量生成题目，目标数量：{}，最大并发数：{}", targetCount, maxConcurrency);

        // 第一阶段：并行生成（控制并发数）
        IntStream.range(0, maxConcurrency)
                .parallel()
                .forEach(i -> {
                    int attempts = 0;
                    int maxAttemptsPerTask = (maxRetries / maxConcurrency) + 1;
                    
                    while (attempts < maxAttemptsPerTask) {
                        synchronized (results) {
                            if (results.size() >= targetCount) {
                                break;
                            }
                        }

                        ProblemGenerationResponse response = generateSingleProblem(request);
                        if (response != null) {
                            String contentHash = calculateContentHash(response);
                            if (StrUtil.isBlank(contentHash)) {
                                log.warn("题目内容哈希计算失败，title={}", response.getTitle());
                                attempts++;
                                continue;
                            }

                            if (contentHashes.add(contentHash)) {
                                synchronized (results) {
                                    if (results.size() < targetCount) {
                                        String redisKey = buildProblemRedisKey(userKey, contentHash);
                                        response.setContentHash(contentHash);
                                        long generatedAt = Instant.now().toEpochMilli();
                                        redisTemplate.opsForValue().set(
                                                redisKey,
                                                response,
                                                CACHE_TTL_DAYS,
                                                TimeUnit.DAYS
                                        );
                                        recordUserProblemIndex(userKey, contentHash);
                                        results.add(response);
                                        log.info("成功生成第 {}/{} 个唯一题目：title={}", 
                                                results.size(), targetCount, response.getTitle());
                                        
                                        publishProblemGeneratedEvent(userKey, contentHash, redisKey, request, generatedAt);
                                    }
                                }
                            } else {
                                log.debug("检测到重复题目，已跳过：contentHash={}", contentHash);
                            }
                        }
                        attempts++;
                    }
                });

        // 第二阶段：如果还没达到目标数量，继续串行生成
        int attempts = 0;
        while (results.size() < targetCount && attempts < maxRetries) {
            ProblemGenerationResponse response = generateSingleProblem(request);
            if (response != null) {
                String contentHash = CalculateHash.calculateContentHash(response);
                if (StrUtil.isBlank(contentHash)) {
                    log.warn("题目内容哈希计算失败，title={}", response.getTitle());
                    attempts++;
                    continue;
                }

                if (contentHashes.add(contentHash)) {
                    String redisKey = buildProblemRedisKey(userKey, contentHash);
                    response.setContentHash(contentHash);
                    long generatedAt = Instant.now().toEpochMilli();
                    redisTemplate.opsForValue().set(
                            redisKey,
                            response,
                            CACHE_TTL_DAYS,
                            TimeUnit.DAYS
                    );

                    recordUserProblemIndex(userKey, contentHash);
                    results.add(response);
                    log.info("成功生成第 {}/{} 个唯一题目：title={}", 
                            results.size(), targetCount, response.getTitle());
                    
                    publishProblemGeneratedEvent(userKey, contentHash, redisKey, request, generatedAt);
                } else {
                    log.debug("检测到重复题目，已跳过：contentHash={}", contentHash);
                }
            }
            attempts++;
        }

        if (results.size() < targetCount) {
            log.warn("未能生成足够数量的唯一题目，目标：{}，实际：{}", targetCount, results.size());
        } else {
            log.info("批量生成完成，成功生成 {} 个唯一题目", results.size());
        }

        return results;
    }

    public ProblemGenerationResponse generateSingleProblem(ProblemGenerationRequest request) {
        String responseText = null;
        long startTime = System.currentTimeMillis();
        try {
            log.info("开始调用 AI 模型生成题目，参数：tagIds={}, difficulty={}", 
                    request.getTagIds(), request.getDifficulty());
            responseText = questionGenerationAiService.generateQuestion(
                    UUID.randomUUID().toString(),
                    request.getTagIds().toString(),
                    StrUtil.isBlank(request.getDifficulty()) ? "0" : request.getDifficulty(),
                    StrUtil.isBlank(request.getSource()) ? "null" : request.getSource(),
                    String.valueOf(request.getQuestionType() != null ? request.getQuestionType() : 0),
                    request.getTimeLimit() != null ? String.valueOf(request.getTimeLimit()) : "null",
                    request.getMemoryLimit() != null ? String.valueOf(request.getMemoryLimit()) : "null",
                    StrUtil.isBlank(request.getAdditionalRequirements()) ? "null" : request.getAdditionalRequirements(),
                    QUESTION_GENERATION_COMMAND
            );
            
            if (StrUtil.isBlank(responseText)) {
                log.error("AI 模型返回空响应");
                aiMetricsService.recordAiCallError();
                return null;
            }

            log.debug("AI 模型响应：{}", responseText);

            String cleanJson = ExtractJsonFromUtil.extractJsonFromResponse(responseText);
            if (StrUtil.isBlank(cleanJson)) {
                log.error("无法从 AI 响应中提取 JSON 内容");
                aiMetricsService.recordAiCallError();
                return null;
            }

            ProblemGenerationResponse response = JSON.parseObject(cleanJson, ProblemGenerationResponse.class);

            // 设置默认值
            if (response.getStackLimit() == null) {
                response.setStackLimit(128);
            }
            if (response.getQuestionType() == null) {
                response.setQuestionType(0);
            }

            // 记录成功指标
            long duration = System.currentTimeMillis() - startTime;
            aiMetricsService.recordQuestionGeneration(duration);
            // Token 使用量由 TokenUsageListener 通过 ChatModelListener 自动记录，无需手动处理

            log.info("题目生成成功：title={}", response.getTitle());
            return response;

        } catch (JSONException e) {
            log.error("解析 AI 响应 JSON 失败", e);
            if (responseText != null) {
                log.error("原始响应：{}", responseText);
            }
            aiMetricsService.recordAiCallError();
            return null;
        } catch (Exception e) {
            log.error("生成题目失败", e);
            aiMetricsService.recordAiCallError();
            return null;
        }
    }

    private String resolveUserKey(ProblemGenerationRequest request) {
        if (request == null || StrUtil.isBlank(request.getUserUuid())) {
            throw new IllegalArgumentException("用户标识不能为空，无法生成题目");
        }
        return request.getUserUuid().trim();
    }

    private String buildProblemRedisKey(String userKey, String contentHash) {
        return RedisKeyConstant.QUESTION_PREFIX + userKey + ":" + contentHash;
    }

    private void recordUserProblemIndex(String userKey, String contentHash) {
        String indexKey = RedisKeyConstant.QUESTION_USER_INDEX_PREFIX + userKey;
        redisTemplate.opsForSet().add(indexKey, contentHash);
        redisTemplate.expire(indexKey, CACHE_TTL_DAYS, TimeUnit.DAYS);
    }

    private void publishProblemGeneratedEvent(String userKey,
                                              String contentHash,
                                              String redisKey,
                                              ProblemGenerationRequest request,
                                              long generatedAt) {
        try {
            String requestId = request != null ? request.getUserUuid() : null;
            if (StrUtil.isBlank(requestId)) {
                requestId = UUID.randomUUID().toString();
            }
            ProblemGeneratedMessage message = ProblemGeneratedMessage.builder()
                    .userKey(userKey)
                    .contentHash(contentHash)
                    .redisKey(redisKey)
                    .requestId(requestId)
                    .occurredAt(generatedAt)
                    .build();
            rabbitTemplate.convertAndSend(
                    RabbitMQConstants.PROBLEM_GENERATED_EXCHANGE,
                    RabbitMQConstants.PROBLEM_GENERATED_ROUTING_KEY,
                    message
            );
            log.info("已发布题目生成事件，userKey={}, contentHash={}, generatedAt={}", userKey, contentHash, generatedAt);
        } catch (Exception e) {
            log.error("发布题目生成事件失败，userKey={}, contentHash={}", userKey, contentHash, e);
        }
    }
}
