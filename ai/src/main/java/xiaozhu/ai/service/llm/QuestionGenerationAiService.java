package xiaozhu.ai.service.llm;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface QuestionGenerationAiService {

    @SystemMessage(fromResource = "prompt/question-generation-prompt.txt")
    String generateQuestion(
            @V("random_seed") String randomSeed,
            @V("tag_ids") String tagIds,
            @V("difficulty") String difficulty,
            @V("source") String source,
            @V("question_type") String questionType,
            @V("time_limit") String timeLimit,
            @V("memory_limit") String memoryLimit,
            @V("additional_requirements") String additionalRequirements,
            @UserMessage String instruction);
}

