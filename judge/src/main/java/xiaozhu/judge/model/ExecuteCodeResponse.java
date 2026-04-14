package xiaozhu.judge.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 判题执行结果
 */
@Data
public class ExecuteCodeResponse {

    /**
     * 所有测试用例的标准输出
     */
    private List<String> outputList = new ArrayList<>();

    /**
     * 执行统计信息
     */
    private JudgeInfo judgeInfo;

    /**
     * 总体退出码（0 表示成功）
     */
    private Long exitCode = 0L;
}
