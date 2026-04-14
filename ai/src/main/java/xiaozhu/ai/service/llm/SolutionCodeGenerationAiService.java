package xiaozhu.ai.service.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 基于 LangChain4j 的 solutionCode 生成 AI Service 接口
 *
 * AI 只生成 solutionCode，必须经过样例验证后才能输出。
 */
public interface SolutionCodeGenerationAiService {

    /**
     * 生成参考解答代码（只生成 solutionCode）
     *
     * @param problemDescription 题目描述
     * @param sampleCases        示例用例（JSON格式，参考用）
     * @param generationInstruction 额外指令
     * @return 包含 solutionCode 的 JSON 字符串
     */
    @SystemMessage(fromResource = "prompt/solution-code-generation-prompt.txt")
    String generateSolutionCode(
            @V("problem_description") String problemDescription,
            @V("sample_cases") String sampleCases,
            @UserMessage String generationInstruction);
}
