package xiaozhu.ai.service.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 基于 LangChain4j 的测试用例生成 AI Service 接口（方案A：对数器模式）
 *
 * AI 一次性生成：
 * - solutionCode：Java 正解代码
 * - checkerCode：Java 数据对数器
 * - checkerArgs：对数器参数
 *
 * @author XiaoZhuDaBai
 */
public interface VerifiedTestCaseGenerationAiService {

    /**
     * 生成测试用例（方案A：对数器模式）
     *
     * @param problemDescription 题目描述
     * @param sampleCases        示例用例（JSON格式，参考用）
     * @param generationInstruction 额外指令
     * @return 包含 solutionCode + checkerCode + checkerArgs 的 JSON 字符串
     */
    @SystemMessage(fromResource = "prompt/verified-test-case-generation-prompt-v2.txt")
    String generateTestCases(
            @V("problem_description") String problemDescription,
            @V("sample_cases") String sampleCases,
            @UserMessage String generationInstruction);
}
