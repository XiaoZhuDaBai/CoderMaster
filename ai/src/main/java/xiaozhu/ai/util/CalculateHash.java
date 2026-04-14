package xiaozhu.ai.util;

import cn.hutool.crypto.digest.DigestUtil;
import xiaozhu.common.dto.ProblemGenerationResponse;

/**
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/27 22:05
 */
public class CalculateHash {
    /**
     * 计算题目内容哈希（使用 SHA256）
     */
    public static String calculateContentHash(ProblemGenerationResponse response) {
        if (response == null) {
            return null;
        }

        String content = (response.getTitle() != null ? response.getTitle() : "")
                + (response.getDescription() != null ? response.getDescription() : "")
                + (response.getInputDesc() != null ? response.getInputDesc() : "")
                + (response.getOutputDesc() != null ? response.getOutputDesc() : "")
                + (response.getExamples() != null ? response.getExamples() : "");

        // 使用 SHA256 计算哈希值
        return DigestUtil.sha256Hex(content);
    }
}
