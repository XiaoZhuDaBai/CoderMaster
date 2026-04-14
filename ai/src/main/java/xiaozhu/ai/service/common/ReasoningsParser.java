package xiaozhu.ai.service.common;

import lombok.extern.slf4j.Slf4j;

/**
 * 思维链解析器
 * 抽取 ChatService、SolutionService 中重复的思维链解析逻辑
 */
@Slf4j
public class ReasoningsParser {

    /**
     * 解析推理模型的响应，分离思维链和最终答案
     * 基于 DeepSeek 推理模型的输出格式
     *
     * @param fullContent 完整内容
     * @param response    响应对象
     */
    public static void parse(String fullContent, StreamingHandler.StreamingResponse response) {
        if (fullContent == null || fullContent.isEmpty()) {
            response.setReasoningContent("");
            response.setContent("");
            return;
        }

        // 查找思维链结束的标记
        int reasoningEndIndex = findReasoningEndIndex(fullContent);

        if (reasoningEndIndex > 0) {
            // 找到了思维链结束标记
            response.setReasoningContent(fullContent.substring(0, reasoningEndIndex));
            response.setContent(fullContent.substring(reasoningEndIndex));
        } else {
            // 没有找到明确的标记，根据内容长度估算
            if (fullContent.length() < 200) {
                // 内容较短，假设都是思维链
                response.setReasoningContent(fullContent);
                response.setContent("");
            } else {
                // 内容较长，假设前40%是思维链，后60%是答案
                int splitPoint = (int) (fullContent.length() * 0.4);
                response.setReasoningContent(fullContent.substring(0, splitPoint));
                response.setContent(fullContent.substring(splitPoint));
            }
        }
    }

    /**
     * 查找思维链结束的位置
     */
    private static int findReasoningEndIndex(String content) {
        // DeepSeek 推理模型可能使用的结束标记
        String[] markers = {
            "\n\n```",  // 可能的思维链结束标签
            "\n## ",     // markdown 标题
            "\n### ",    // markdown 子标题
            "\n1. ",     // 编号列表
            "\n概述",    // 常见的答案开始标记
            "\n思路",    // 常见的答案开始标记
            "\n正确性证明",
            "\n复杂度分析",
            "\n代码",
            "\n测试"
        };

        for (String marker : markers) {
            int index = content.indexOf(marker);
            if (index > 0) {
                return index;
            }
        }

        // 如果没找到标记，检查是否包含明显的答案开始模式
        if (content.contains("概述：") || content.contains("思路：")) {
            int overviewIndex = content.indexOf("概述：");
            if (overviewIndex > 0) return overviewIndex;

            int approachIndex = content.indexOf("思路：");
            if (approachIndex > 0) return approachIndex;
        }

        return -1;
    }
}
