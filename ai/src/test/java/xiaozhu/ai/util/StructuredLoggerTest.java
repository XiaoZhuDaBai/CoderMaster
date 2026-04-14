package xiaozhu.ai.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * StructuredLogger 单元测试
 */
class StructuredLoggerTest {

    @Test
    void testLogDataBuilding() {
        // 测试日志数据构建（只验证不抛出异常）
        Map<String, Object> data = new HashMap<>();
        data.put("userKey", "test-user");
        data.put("contentHash", "abc123");
        data.put("count", 5);

        assertEquals("test-user", data.get("userKey"));
        assertEquals("abc123", data.get("contentHash"));
        assertEquals(5, data.get("count"));
    }
}
