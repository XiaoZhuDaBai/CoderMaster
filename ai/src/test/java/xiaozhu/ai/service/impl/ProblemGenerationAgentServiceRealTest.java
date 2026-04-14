//package xiaozhu.ai.service.impl;
//
//import com.alibaba.fastjson2.JSON;
//import com.alibaba.fastjson2.JSONWriter;
//import jakarta.annotation.Resource;
//import lombok.extern.slf4j.Slf4j;
//import org.junit.jupiter.api.Test;
//import org.springframework.boot.test.context.SpringBootTest;
//import xiaozhu.ai.model.ProblemGenerationRequest;
//import xiaozhu.common.dto.ProblemGenerationResponse;
//import xiaozhu.ai.service.ProblemGenerationAgentService;
//import xiaozhu.ai.service.SolutionGenerationAgentService;
//
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
///**
// * 通过真实 LangChain4j 配置生成题目，便于人工查看。
// * 运行方式：mvn -pl ai-service -Dtest=ProblemGenerationAgentServiceRealTest test
// */
//@Slf4j
//@SpringBootTest
//class ProblemGenerationAgentServiceRealTest {
//
//    @Resource
//    private ProblemGenerationAgentService problemGenerationAgentService;
//    @Resource
//    private SolutionGenerationAgentService solutionGenerationAgentService;
//
//    @Test
//    void generateRealProblem() {
//        ArrayList<ProblemGenerationRequest> requests = new ArrayList<>();
//
//
//        ProblemGenerationRequest request1 = ProblemGenerationRequest.builder()
//                .tagIds(Arrays.asList("动态规划"))
//                .difficulty("困难")
//                .source("竞赛")
//                .build();
////        ProblemGenerationRequest request2 = ProblemGenerationRequest.builder()
////                .tagIds(Arrays.asList("动态规划"))
////                .difficulty("困难")
////                .source("竞赛")
////                .build();
////        ProblemGenerationRequest request3 = ProblemGenerationRequest.builder()
////                .tagIds(Arrays.asList("动态规划"))
////                .difficulty("困难")
////                .source("竞赛")
////                .build();
//
//        requests.add(request1);
////        requests.add(request2);
////        requests.add(request3);
//
//
//        List<ProblemGenerationResponse> responses = problemGenerationAgentService.generateBatchProblems(requests);
//
//        // 实时输出每个token
//        String s = solutionGenerationAgentService.generateSolutionStreaming(request1, responses.getFirst(), System.out::print);
////        log.info("solution: {}", s);
//
//        System.out.println(JSON.toJSONString(responses, JSONWriter.Feature.PrettyFormat));
//    }
//}
//
