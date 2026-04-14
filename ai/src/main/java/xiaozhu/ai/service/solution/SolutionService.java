package xiaozhu.ai.service.solution;

import xiaozhu.ai.model.ProblemGenerationRequest;
import xiaozhu.common.dto.ProblemGenerationResponse;

import java.util.function.Consumer;

/**
 * 题解生成服务接口
 */
public interface SolutionService {

    /**
     * 流式生成题解
     *
     * @param request 题目生成请求参数
     * @param problem AI生成的题目内容
     * @param onToken 每个token的回调函数
     */
    void generateSolutionStreaming(ProblemGenerationRequest request, 
                                  ProblemGenerationResponse problem,
                                  Consumer<String> onToken);
}
