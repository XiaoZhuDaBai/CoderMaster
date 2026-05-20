package xiaozhu.ai.agent.reviewer.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xiaozhu.ai.agent.reviewer.ReviewResult;
import xiaozhu.ai.model.TestCaseGenerationResponse;
import xiaozhu.ai.model.TestCaseGenerationResponse.TestCaseDetail;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TestCaseReviewer 单元测试
 */
class TestCaseReviewerTest {

    private TestCaseReviewer reviewer;

    @BeforeEach
    void setUp() {
        reviewer = new TestCaseReviewer();
    }

    @Test
    void review_WithValidTestCases_ShouldPass() {
        // 准备测试数据：6 个用例，包含边界条件
        TestCaseGenerationResponse response = createValidResponse();

        // 执行评审
        ReviewResult result = reviewer.review(response);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.isPassed() || result.getScore() >= 60,
                "评审应该通过或得分 >= 60");
        assertEquals("TEST_CASE_REVIEW", result.getReviewType());
        assertTrue(result.getDurationMs() >= 0);
    }

    @Test
    void review_WithEmptyTestCases_ShouldFail() {
        TestCaseGenerationResponse response = new TestCaseGenerationResponse();
        response.setTestCases(new ArrayList<>());

        ReviewResult result = reviewer.review(response);

        assertFalse(result.isPassed());
        assertTrue(result.getIssues().size() > 0);
    }

    @Test
    void review_WithInsufficientCount_ShouldWarn() {
        // 只有 2 个用例（少于最低要求 5 个）
        TestCaseGenerationResponse response = createResponseWithCount(2);

        ReviewResult result = reviewer.review(response);

        assertTrue(result.getIssues().stream()
                .anyMatch(issue -> issue.contains("数量不足")),
                "应该报告用例数量不足");
    }

    @Test
    void review_WithZeroValue_ShouldDetectBoundary() {
        TestCaseGenerationResponse response = createResponseWithBoundary("0");

        ReviewResult result = reviewer.review(response);

        assertTrue((Boolean) result.getDetails().get("has_zero_case"),
                "应该检测到零值用例");
    }

    @Test
    void review_WithNegativeValue_ShouldDetectBoundary() {
        TestCaseGenerationResponse response = createResponseWithBoundary("-5");

        ReviewResult result = reviewer.review(response);

        assertTrue((Boolean) result.getDetails().get("has_negative_case"),
                "应该检测到负数用例");
    }

    @Test
    void review_ShouldCalculateScore() {
        TestCaseGenerationResponse response = createValidResponse();

        ReviewResult result = reviewer.review(response);

        assertTrue(result.getScore() >= 0 && result.getScore() <= 100,
                "分数应该在 0-100 之间");
    }

    // 辅助方法

    private TestCaseGenerationResponse createValidResponse() {
        TestCaseGenerationResponse response = new TestCaseGenerationResponse();
        List<TestCaseDetail> testCases = new ArrayList<>();

        // 添加 6 个用例，包含边界条件
        testCases.add(createTestCase(1, "0", "0", 1, "SAMPLE"));
        testCases.add(createTestCase(2, "-5", "-5", 1, "SAMPLE"));
        testCases.add(createTestCase(3, "1 2 3", "6", 0, "HIDDEN"));
        testCases.add(createTestCase(4, "100 200", "300", 0, "HIDDEN"));
        testCases.add(createTestCase(5, "12345", "12345", 0, "EXTREME"));
        testCases.add(createTestCase(6, "10 20 30", "60", 0, "HIDDEN"));

        response.setTestCases(testCases);
        return response;
    }

    private TestCaseGenerationResponse createResponseWithCount(int count) {
        TestCaseGenerationResponse response = new TestCaseGenerationResponse();
        List<TestCaseDetail> testCases = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            testCases.add(createTestCase(i + 1, String.valueOf(i), String.valueOf(i), 1, "SAMPLE"));
        }

        response.setTestCases(testCases);
        return response;
    }

    private TestCaseGenerationResponse createResponseWithBoundary(String input) {
        TestCaseGenerationResponse response = new TestCaseGenerationResponse();
        List<TestCaseDetail> testCases = new ArrayList<>();
        testCases.add(createTestCase(1, input, input, 1, "SAMPLE"));
        response.setTestCases(testCases);
        return response;
    }

    private TestCaseDetail createTestCase(int index, String input, String output, int isPublic, String caseType) {
        TestCaseDetail detail = new TestCaseDetail();
        detail.setCaseIndex(index);
        detail.setInput(input);
        detail.setExpectedOutput(output);
        detail.setIsPublic(isPublic);
        detail.setCaseType(caseType);
        detail.setWeight(1.0);
        return detail;
    }
}
