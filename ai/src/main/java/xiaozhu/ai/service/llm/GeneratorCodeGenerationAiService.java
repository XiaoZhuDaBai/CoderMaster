package xiaozhu.ai.service.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * 基于 LangChain4j 的 generatorCode 生成 AI Service 接口
 *
 * 在 solutionCode 验证通过后调用，生成测试数据生成器代码。
 */
public interface GeneratorCodeGenerationAiService {

    /**
     * 生成测试数据生成器代码（只生成 generatorCode）
     *
     * @param problemDescription 题目描述
     * @param sampleCases        示例用例（JSON格式，参考用）
     * @param solutionCode       已验证的参考解答代码
     * @param generationInstruction 额外指令
     * @return 包含 generatorCode + generatorArgs 的 JSON 字符串
     */
    @SystemMessage(fromResource = "prompt/generator-code-generation-prompt.txt")
    String generateGeneratorCode(
            @V("problem_description") String problemDescription,
            @V("sample_cases") String sampleCases,
            @V("solutionCode") String solutionCode,
            @UserMessage String generationInstruction);
}
