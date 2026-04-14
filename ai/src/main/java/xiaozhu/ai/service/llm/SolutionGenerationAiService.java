package xiaozhu.ai.service.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import xiaozhu.ai.model.SolutionGenerationContext;

/**
 * 基于 LangChain4j 的题解生成 AI Service 接口
 *
 * 使用 {@link SystemMessage} 绑定提示词模板文件，
 * 再结合 {@link TokenStream} 实现流式输出，便于将模型的思考过程也实时推送给前端。
 *
 * @author XiaoZhu
 */
public interface SolutionGenerationAiService {

    /**
     * 基于题目生成参数与完整题面，流式生成题解内容
     *
     * @param context 题解生成上下文，包含所有必要的变量
     * @param instruction 额外的用户指令，用于强调输出格式或风格
     * @return 流式题解 TokenStream
     */
    @SystemMessage(fromResource = "prompt/solution-generation-prompt.txt")
    TokenStream generateSolution(SolutionGenerationContext context, @UserMessage String instruction);
}

