package xiaozhu.ai.util;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/27 21:59
 */
@Slf4j
public class ExtractJsonFromUtil {
    /**
     * 从 AI 响应中提取纯 JSON 内容
     * 处理 markdown 代码块标记（```json ... ```）和其他可能的包装文本
     *
     * @param responseText AI 原始响应文本
     * @return 提取的纯 JSON 字符串，如果提取失败则返回 null
     */
    public static String extractJsonFromResponse(String responseText) {
        if (StrUtil.isBlank(responseText)) {
            return null;
        }

        String text = responseText.trim();

        // 移除 markdown 代码块标记：```json 或 ```
        // 匹配开头的 ```json 或 ```（可能带换行和空格）
        text = text.replaceAll("^```(?:json)?\\s*\\n?", "");
        // 匹配结尾的 ```（可能带换行和空格）
        text = text.replaceAll("\\n?\\s*```$", "");

        // 移除可能的前导/后导反引号（单独的反引号）
        text = text.replaceAll("^`+", "").replaceAll("`+$", "");

        text = text.trim();

        // 查找第一个 { 和最后一个 }，提取 JSON 对象
        int firstBrace = text.indexOf('{');
        int lastBrace = text.lastIndexOf('}');

        if (firstBrace == -1 || lastBrace == -1 || firstBrace >= lastBrace) {
            log.warn("无法在响应中找到有效的 JSON 对象边界，响应前100字符：{}",
                    text.length() > 100 ? text.substring(0, 100) : text);
            return null;
        }

        // 提取 JSON 部分
        String json = text.substring(firstBrace, lastBrace + 1).trim();

        // 验证提取的 JSON 是否以 { 开头，} 结尾
        if (!json.startsWith("{") || !json.endsWith("}")) {
            log.warn("提取的 JSON 格式不正确，提取内容前50字符：{}",
                    json.length() > 50 ? json.substring(0, 50) : json);
            return null;
        }

        return json;
    }
}
