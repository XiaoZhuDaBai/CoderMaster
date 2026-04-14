package xiaozhu.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 生成参考解答代码的响应数据（仅包含 solutionCode）
 *
 * AI 只生成 solutionCode，必须经过样例验证后才能输出。
 * 通过沙箱执行 sample_cases 验证正确性，验证通过后再用于后续流程。
 *
 * @author XiaoZhuDaBai
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SolutionCodeGenerationResponse {

    /**
     * Java 正解代码
     * 经过样例验证，确保算法逻辑正确
     */
    private String solutionCode;
}
