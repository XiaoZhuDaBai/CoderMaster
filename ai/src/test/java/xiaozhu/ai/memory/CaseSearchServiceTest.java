package xiaozhu.ai.memory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * CaseSearchService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class CaseSearchServiceTest {

    @Mock
    private SuccessCaseMapper successCaseMapper;

    @Mock
    private FailureCaseMapper failureCaseMapper;

    @Mock
    private CaseQueryLogMapper caseQueryLogMapper;

    @InjectMocks
    private CaseSearchService caseSearchService;

    private SuccessCase sampleSuccessCase;
    private FailureCase sampleFailureCase;

    @BeforeEach
    void setUp() {
        sampleSuccessCase = new SuccessCase();
        sampleSuccessCase.setId(1L);
        sampleSuccessCase.setProblemType("DP");
        sampleSuccessCase.setAlgorithmKeyword("动态规划");
        sampleSuccessCase.setSuccessRate(BigDecimal.valueOf(100));
        sampleSuccessCase.setTestcaseCount(10);
        sampleSuccessCase.setProblemTitle("两数之和");

        sampleFailureCase = new FailureCase();
        sampleFailureCase.setId(1L);
        sampleFailureCase.setProblemType("DP");
        sampleFailureCase.setFailureReason("AI_JSON_EXTRACT_FAILED");
        sampleFailureCase.setProblemTitle("两数之和");
    }

    @Test
    void recordSuccessCase_ShouldInsert() {
        // given
        doNothing().when(successCaseMapper).insert(any(SuccessCase.class));

        // when
        caseSearchService.recordSuccessCase(sampleSuccessCase);

        // then
        verify(successCaseMapper, times(1)).insert(sampleSuccessCase);
    }

    @Test
    void recordFailureCase_ShouldInsert() {
        // given
        doNothing().when(failureCaseMapper).insert(any(FailureCase.class));

        // when
        caseSearchService.recordFailureCase(sampleFailureCase);

        // then
        verify(failureCaseMapper, times(1)).insert(sampleFailureCase);
    }

    @Test
    void findSimilarSuccessCases_ShouldReturnCases() {
        // given
        when(successCaseMapper.selectList(any())).thenReturn(List.of(sampleSuccessCase));

        // when
        List<SuccessCase> result = caseSearchService.findSimilarSuccessCases("DP", "动态规划", 5);

        // then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("DP", result.get(0).getProblemType());
    }

    @Test
    void findSimilarFailureCases_ShouldReturnCases() {
        // given
        when(failureCaseMapper.selectList(any())).thenReturn(List.of(sampleFailureCase));

        // when
        List<FailureCase> result = caseSearchService.findSimilarFailureCases("DP", "AI_JSON_EXTRACT_FAILED", 5);

        // then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("DP", result.get(0).getProblemType());
    }

    @Test
    void hasSimilarSuccessCase_WithNullHash_ShouldReturnFalse() {
        // when
        boolean result = caseSearchService.hasSimilarSuccessCase(null);

        // then
        assertFalse(result);
    }

    @Test
    void hasSimilarSuccessCase_WithBlankHash_ShouldReturnFalse() {
        // when
        boolean result = caseSearchService.hasSimilarSuccessCase("  ");

        // then
        assertFalse(result);
    }

    @Test
    void buildSuccessCaseSummary_WithEmptyList_ShouldReturnMessage() {
        // when
        String summary = caseSearchService.buildSuccessCaseSummary(List.of());

        // then
        assertEquals("无相似成功案例", summary);
    }

    @Test
    void buildSuccessCaseSummary_WithNullList_ShouldReturnMessage() {
        // when
        String summary = caseSearchService.buildSuccessCaseSummary(null);

        // then
        assertEquals("无相似成功案例", summary);
    }

    @Test
    void buildFailureCaseSummary_WithEmptyList_ShouldReturnMessage() {
        // when
        String summary = caseSearchService.buildFailureCaseSummary(List.of());

        // then
        assertEquals("无相似失败案例", summary);
    }

    @Test
    void getFailureRateByProblemType_WithNoData_ShouldReturnZero() {
        // given
        when(successCaseMapper.selectCount(any())).thenReturn(0L);
        when(failureCaseMapper.selectCount(any())).thenReturn(0L);

        // when
        BigDecimal rate = caseSearchService.getFailureRateByProblemType("DP");

        // then
        assertEquals(BigDecimal.ZERO, rate);
    }
}
