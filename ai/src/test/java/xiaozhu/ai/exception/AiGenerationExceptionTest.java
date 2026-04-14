package xiaozhu.ai.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * AiGenerationException 单元测试
 */
class AiGenerationExceptionTest {

    @Test
    void testExceptionCreationWithMessage() {
        AiGenerationException exception = new AiGenerationException(
                AiErrorType.AI_RESPONSE_EMPTY, "AI 返回空响应");

        assertEquals(AiErrorType.AI_RESPONSE_EMPTY, exception.getErrorType());
        assertEquals("AI 返回空响应", exception.getMessage());
        assertNull(exception.getDetail());
        assertTrue(exception.isRetryable());
    }

    @Test
    void testExceptionCreationWithDetail() {
        AiGenerationException exception = new AiGenerationException(
                AiErrorType.SANDBOX_VERIFY_FAILED,
                "样例验证未通过",
                "题目=两数之和");

        assertEquals(AiErrorType.SANDBOX_VERIFY_FAILED, exception.getErrorType());
        assertEquals("样例验证未通过", exception.getMessage());
        assertEquals("题目=两数之和", exception.getDetail());
        assertFalse(exception.isRetryable());
    }

    @Test
    void testRetryableErrors() {
        assertTrue(AiErrorType.AI_RESPONSE_EMPTY.isRetryable());
        assertTrue(AiErrorType.AI_JSON_EXTRACT_FAILED.isRetryable());
        assertTrue(AiErrorType.AI_JSON_PARSE_FAILED.isRetryable());
        assertTrue(AiErrorType.UNKNOWN_ERROR.isRetryable());
    }

    @Test
    void testNonRetryableErrors() {
        assertFalse(AiErrorType.SANDBOX_COMPILE_ERROR.isRetryable());
        assertFalse(AiErrorType.SANDBOX_RUNTIME_ERROR.isRetryable());
        assertFalse(AiErrorType.SANDBOX_VERIFY_FAILED.isRetryable());
        assertFalse(AiErrorType.SANDBOX_TIMEOUT.isRetryable());
        assertFalse(AiErrorType.SOLUTION_CODE_VALIDATION_FAILED.isRetryable());
        assertFalse(AiErrorType.GENERATOR_CODE_VALIDATION_FAILED.isRetryable());
    }

    @Test
    void testToString() {
        AiGenerationException exception = new AiGenerationException(
                AiErrorType.AI_JSON_PARSE_FAILED, "JSON 解析失败", "位置=第5行");

        String str = exception.toString();
        assertTrue(str.contains("AiGenerationException"));
        assertTrue(str.contains("AI_JSON_PARSE_FAILED"));
        assertTrue(str.contains("JSON 解析失败"));
        assertTrue(str.contains("位置=第5行"));
    }
}
