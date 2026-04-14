package xiaozhu.ai.service.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface TestCaseGenerationAiService {

    @SystemMessage(fromResource = "prompt/test-case-generation-prompt.txt")
    String generateTestCases(
            @V("problem_description") String problemDescription,
            @V("sample_cases") String sampleCases,
            @UserMessage String generationInstruction);
}

