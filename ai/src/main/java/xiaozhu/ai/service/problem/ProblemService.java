package xiaozhu.ai.service.problem;

import xiaozhu.ai.model.ProblemGenerationRequest;
import xiaozhu.common.dto.ProblemGenerationResponse;

import java.util.List;

/**
 * 题目生成服务接口
 */
public interface ProblemService {

    /**
     * 同步生成题目
     *
     * @param request 生成请求
     * @return 生成的题目列表
     */
    List<ProblemGenerationResponse> generateBatchProblems(ProblemGenerationRequest request);

    /**
     * 异步生成题目
     * 题目生成在后台执行，结果会存储到 Redis，并通过消息队列通知
     *
     * @param request 生成请求
     */
    void generateBatchProblemsAsync(ProblemGenerationRequest request);
}
