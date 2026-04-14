package xiaozhu.ai.model;

import lombok.Data;
import xiaozhu.ai.model.ProblemGenerationRequest;
import xiaozhu.common.dto.ProblemGenerationResponse;

/**
 * 题解生成请求包装类
 */
@Data
public class SolutionGenerationRequest {
    private ProblemGenerationRequest request;
    private ProblemGenerationResponse problem;
}

