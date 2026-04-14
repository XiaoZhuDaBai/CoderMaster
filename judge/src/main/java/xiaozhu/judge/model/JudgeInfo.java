package xiaozhu.judge.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 承载判题相关统计信息
 */
@Data
public class JudgeInfo {

    /**
     * 每个测试用例是否通过
     */
    private boolean[] correct = new boolean[0];

    /**
     * 最大耗时（毫秒）
     */
    private long time;

    /**
     * 最大内存（字节）
     */
    private long memory;

    /**
     * 错误信息收集
     */
    private List<String> errorMessages = new ArrayList<>();
}

