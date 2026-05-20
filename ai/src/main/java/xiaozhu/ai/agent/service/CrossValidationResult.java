package xiaozhu.ai.agent.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

/**
 * 交叉验证结果封装
 *
 * <p>由 {@link CrossValidationService} 生成，用于描述独立 AI 模型对 solutionCode 的语义验证结果。</p>
 *
 * <p>双 AI 交叉验证结果判断（5种情况）：
 * <ul>
 *   <li>情况①: sandboxA == sandboxB == expectedOutput → 三方一致，expectedOutput 可信</li>
 *   <li>情况②: sandboxA == sandboxB != expectedOutput → 两个 AI 一致但与题目不符，expectedOutput 错误，需要修正</li>
 *   <li>情况③: sandboxA == expectedOutput != sandboxB → AI#1 和题目一致，以 AI#1 为准</li>
 *   <li>情况④: sandboxA != expectedOutput && sandboxA == sandboxB → 同情况②，两个 AI 一致但与题目不符</li>
 *   <li>情况⑤: sandboxA != expectedOutput && sandboxB != expectedOutput && sandboxA != sandboxB → 严重分歧，用 AI#1</li>
 * </ul>
 *
 * @param validated                              是否完成了验证（false 表示跳过或异常）
 * @param allPassed                              是否全部通过（三方一致）
 * @param passCount                              通过数量
 * @param totalCount                             总数量
 * @param summary                                摘要信息
 * @param details                                详细结果列表
 * @param elapsedMs                              执行耗时（毫秒）
 * @param solutionCodeFromCrossValidator         交叉验证 AI 生成的独立 solutionCode（用于 Agent 参考）
 * @param hasOriginalExpectedOutputError         是否有题目自带的 expectedOutput 被修正（情况②或④）
 * @param discrepancies                          具体分歧描述列表
 */
@Getter
@AllArgsConstructor
public class CrossValidationResult {

    private final boolean validated;
    private final boolean allPassed;
    private final int passCount;
    private final int totalCount;
    private final String summary;
    private final List<String> details;
    private final long elapsedMs;
    /** 交叉验证 AI 生成的独立 solutionCode */
    private final String solutionCodeFromCrossValidator;
    /** 是否有题目自带的 expectedOutput 被修正（情况②或④） */
    private final boolean hasOriginalExpectedOutputError;
    /** 具体分歧描述列表（每项对应一个 testCase 的验证结果） */
    private final List<String> discrepancies;

    /**
     * @deprecated 使用含新字段的构造器替代
     */
    @Deprecated
    public CrossValidationResult(boolean validated, boolean allPassed, int passCount,
                                  int totalCount, String summary, List<String> details, long elapsedMs) {
        this(validated, allPassed, passCount, totalCount, summary, details, elapsedMs,
                null, false, List.of());
    }

    public static CrossValidationResult skipped(String reason) {
        return new CrossValidationResult(
                false, false, 0, 0, "跳过: " + reason, List.of(), 0, null, false, List.of());
    }

    public static CrossValidationResult error(String reason) {
        return new CrossValidationResult(
                false, false, 0, 0, "错误: " + reason, List.of(), 0, null, false, List.of());
    }

    /** solutionCode 语义正确，可信度高（全部通过） */
    public boolean isReliable() {
        return validated && allPassed;
    }

    /** 主模型与交叉验证模型存在严重分歧（0% 通过率） */
    public boolean hasSignificantDisagreement() {
        return validated && !allPassed && passCount == 0;
    }

    /** 是否完成了双 AI 对比验证（有独立 solutionCode 产出） */
    public boolean hasIndependentSolutionCode() {
        return solutionCodeFromCrossValidator != null && !solutionCodeFromCrossValidator.isBlank();
    }
}
