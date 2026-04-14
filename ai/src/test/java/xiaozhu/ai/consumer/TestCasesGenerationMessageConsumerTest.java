package xiaozhu.ai.consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.core.task.TaskExecutor;
import dev.langchain4j.model.chat.ChatLanguageModel;
import xiaozhu.ai.metrics.AiMetricsService;
import xiaozhu.common.dto.ProblemGenerationResponse;
import xiaozhu.common.dto.TestCaseGenerationResponse;
import org.springframework.data.redis.core.RedisTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestCasesGenerationMessageConsumer 单元测试
 * 重点测试 .repeat 替换和验证集成
 */
class TestCasesGenerationMessageConsumerTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private TaskExecutor testCaseGenerationExecutor;

    @Mock
    private ChatLanguageModel testCaseChatModel;

    @Mock
    private AiMetricsService aiMetricsService;


    private TestCasesGenerationMessageConsumer consumer;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        consumer = new TestCasesGenerationMessageConsumer(
            redisTemplate, testCaseGenerationExecutor, testCaseChatModel,
            aiMetricsService
        );
    }

    @Test
    void testReplaceRepeatExpressions_NormalCase() {
        // 正常情况
        String input = "{\"input\": \"abc\".repeat(3), \"expected\": \"xyz\"}";
        String result = consumer.getClass().getDeclaredMethods()[0].getName().contains("replaceRepeat")
            ? invokeReplaceRepeatExpressions(consumer, input)
            : input;

        // 使用反射调用私有方法进行测试
        try {
            java.lang.reflect.Method method = TestCasesGenerationMessageConsumer.class
                .getDeclaredMethod("replaceRepeatExpressions", String.class);
            method.setAccessible(true);
            result = (String) method.invoke(consumer, input);

            assertEquals("{\"input\": \"abcabcabc\", \"expected\": \"xyz\"}", result);
        } catch (Exception e) {
            fail("反射调用失败: " + e.getMessage());
        }
    }

    @Test
    void testReplaceRepeatExpressions_LargeRepeat() {
        // 大量重复的情况
        String input = "{\"input\": \"a\".repeat(3000)}";

        try {
            java.lang.reflect.Method method = TestCasesGenerationMessageConsumer.class
                .getDeclaredMethod("replaceRepeatExpressions", String.class);
            method.setAccessible(true);
            String result = (String) method.invoke(consumer, input);

            // 应该被截断或跳过替换
            assertTrue(result.contains(".repeat(3000)") || result.length() < input.length() * 2);
        } catch (Exception e) {
            fail("反射调用失败: " + e.getMessage());
        }
    }

    @Test
    void testValidateAndFilterTestCases() {
        // 准备测试数据
        ProblemGenerationResponse problem = new ProblemGenerationResponse();
        problem.setTitle("数组排序");
        problem.setDescription("输入一个数组，对其进行升序排序");

        TestCaseGenerationResponse response = new TestCaseGenerationResponse();
        TestCaseGenerationResponse.TestCaseDetail testCase = new TestCaseGenerationResponse.TestCaseDetail();
        testCase.setCaseIndex(1);
        testCase.setInput("2\n3 1 4 2"); // 第一行是数组长度，第二行是数组元素
        testCase.setExpectedOutput("1 2 2 4"); // 排序后的结果

        response.setTestCases(java.util.Arrays.asList(testCase));

        // 调用验证方法
        try {
            java.lang.reflect.Method method = TestCasesGenerationMessageConsumer.class
                .getDeclaredMethod("validateAndFilterTestCases",
                    TestCaseGenerationResponse.class, ProblemGenerationResponse.class);
            method.setAccessible(true);
            TestCaseGenerationResponse result = (TestCaseGenerationResponse) method.invoke(consumer, response, problem);

            // 验证结果
            assertNotNull(result);
            assertNotNull(result.getTestCases());
            assertEquals(1, result.getTestCases().size());

            TestCaseGenerationResponse.TestCaseDetail validatedCase = result.getTestCases().get(0);
            assertNotNull(validatedCase.getInput());
            assertNotNull(validatedCase.getExpectedOutput());

        } catch (Exception e) {
            fail("反射调用失败: " + e.getMessage());
        }
    }


    // 辅助方法：通过反射调用私有方法
    private String invokeReplaceRepeatExpressions(TestCasesGenerationMessageConsumer consumer, String input) {
        try {
            java.lang.reflect.Method method = consumer.getClass()
                .getDeclaredMethod("replaceRepeatExpressions", String.class);
            method.setAccessible(true);
            return (String) method.invoke(consumer, input);
        } catch (Exception e) {
            throw new RuntimeException("反射调用失败", e);
        }
    }
}
