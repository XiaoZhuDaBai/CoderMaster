package xiaozhu.ai.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;

/**
 * 结构化日志工具类
 */
@Slf4j
public final class StructuredLogger {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private StructuredLogger() {}

    /**
     * 记录操作成功日志
     */
    public static void logSuccess(String operation, String message, Map<String, Object> data) {
        Map<String, Object> logData = buildLogData("SUCCESS", operation, message, data);
        log.info(toJson(logData));
    }

    /**
     * 记录操作失败日志
     */
    public static void logError(String operation, String message, Throwable error, Map<String, Object> data) {
        Map<String, Object> logData = buildLogData("ERROR", operation, message, data);
        if (error != null) {
            logData.put("errorType", error.getClass().getSimpleName());
            logData.put("errorMessage", error.getMessage());
        }
        log.error(toJson(logData));
    }

    /**
     * 记录操作失败日志（无异常）
     */
    public static void logError(String operation, String message, Map<String, Object> data) {
        logError(operation, message, null, data);
    }

    /**
     * 记录性能日志
     */
    public static void logPerformance(String operation, long durationMs, Map<String, Object> data) {
        Map<String, Object> logData = buildLogData("PERFORMANCE", operation, "操作耗时", data);
        logData.put("durationMs", durationMs);

        if (durationMs > 30000) {
            log.warn(toJson(logData));
        } else if (durationMs > 10000) {
            log.warn(toJson(logData));
        } else {
            log.info(toJson(logData));
        }
    }

    /**
     * 记录 AI 调用日志
     */
    public static void logAiCall(String operation, String model, long durationMs, int promptTokens,
                                  int completionTokens, boolean success, String errorMessage) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("level", "INFO");
        logData.put("type", "AI_CALL");
        logData.put("operation", operation);
        logData.put("model", model);
        logData.put("durationMs", durationMs);
        logData.put("promptTokens", promptTokens);
        logData.put("completionTokens", completionTokens);
        logData.put("success", success);

        if (!success && errorMessage != null) {
            logData.put("errorMessage", errorMessage);
            log.error(toJson(logData));
        } else {
            log.info(toJson(logData));
        }
    }

    /**
     * 记录业务事件
     */
    public static void logBusinessEvent(String event, Map<String, Object> data) {
        Map<String, Object> logData = buildLogData("BUSINESS", event, null, data);
        log.info(toJson(logData));
    }

    private static Map<String, Object> buildLogData(String level, String operation,
                                                     String message, Map<String, Object> data) {
        Map<String, Object> logData = new HashMap<>();
        logData.put("level", level);
        logData.put("type", "AI_SERVICE");
        logData.put("operation", operation);
        if (message != null) {
            logData.put("message", message);
        }
        if (data != null && !data.isEmpty()) {
            logData.putAll(data);
        }
        return logData;
    }

    private static String toJson(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (JsonProcessingException e) {
            return data.toString();
        }
    }
}
