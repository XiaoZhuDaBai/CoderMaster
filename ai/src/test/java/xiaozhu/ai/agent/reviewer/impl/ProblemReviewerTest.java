package xiaozhu.ai.agent.reviewer.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xiaozhu.ai.agent.reviewer.ReviewResult;
import xiaozhu.ai.model.ProblemGenerationRequest;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ProblemReviewer 单元测试
 */
class ProblemReviewerTest {

    private ProblemReviewer reviewer;

    @BeforeEach
    void setUp() {
        reviewer = new ProblemReviewer();
    }

    @Test
    void review_WithValidProblem_ShouldPass() {
        ProblemGenerationRequest request = createValidRequest();

        ReviewResult result = reviewer.review(request);

        assertNotNull(result);
        assertTrue(result.isPassed() || result.getScore() >= 70,
                "评审应该通过或得分 >= 70");
        assertEquals("PROBLEM_REVIEW", result.getReviewType());
    }

    @Test
    void review_WithEmptyTitle_ShouldFail() {
        ProblemGenerationRequest request = createValidRequest();
        request.setTitle(null);

        ReviewResult result = reviewer.review(request);

        assertFalse(result.isPassed());
        assertTrue(result.getIssues().stream()
                .anyMatch(issue -> issue.contains("标题")));
    }

    @Test
    void review_WithEmptyDescription_ShouldFail() {
        ProblemGenerationRequest request = createValidRequest();
        request.setDescription(null);

        ReviewResult result = reviewer.review(request);

        assertFalse(result.isPassed());
        assertTrue(result.getIssues().stream()
                .anyMatch(issue -> issue.contains("描述")));
    }

    @Test
    void review_WithExamples_ShouldCount() {
        ProblemGenerationRequest request = createValidRequest();

        ReviewResult result = reviewer.review(request);

        assertEquals(2, result.getDetails().get("example_count"));
    }

    @Test
    void review_WithShortDescription_ShouldWarn() {
        ProblemGenerationRequest request = createValidRequest();
        request.setDescription("短描述");

        ReviewResult result = reviewer.review(request);

        assertTrue(result.getIssues().stream()
                .anyMatch(issue -> issue.contains("过短")),
                "应该报告描述过短");
    }

    @Test
    void review_ShouldCalculateScore() {
        ProblemGenerationRequest request = createValidRequest();

        ReviewResult result = reviewer.review(request);

        assertTrue(result.getScore() >= 0 && result.getScore() <= 100,
                "分数应该在 0-100 之间");
    }

    // 辅助方法

    private ProblemGenerationRequest createValidRequest() {
        ProblemGenerationRequest request = new ProblemGenerationRequest();
        request.setTitle("两数之和");
        request.setDescription("给定一个整数数组 nums 和一个整数目标值 target，请你在该数组中找出和为目标值 target 的那两个整数，并返回它们的数组下标。");
        request.setInputDesc("第一行包含两个整数 n 和 target，分别表示数组长度和目标值。第二行包含 n 个整数，表示数组元素。");
        request.setOutputDesc("输出两个整数，表示两个加数的下标（从 0 开始）。");

        List<ProblemGenerationRequest.Example> examples = new ArrayList<>();
        ProblemGenerationRequest.Example ex1 = new ProblemGenerationRequest.Example();
        ex1.setInput("4 9\n2 7 11 15");
        ex1.setExpectedOutput("0 1");
        examples.add(ex1);

        ProblemGenerationRequest.Example ex2 = new ProblemGenerationRequest.Example();
        ex2.setInput("4 6\n3 2 4 6");
        ex2.setExpectedOutput("1 2");
        examples.add(ex2);

        request.setExamples(examples);
        return request;
    }
}
