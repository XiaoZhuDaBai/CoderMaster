package xiaozhu.common.eenum;

/**
 * 判题状态枚举，对应 submission.judge_status
 */
public enum JudgeStatus {
    PENDING,
    JUDGING,
    AC,
    WA,
    TLE,
    MLE,
    RE,
    CE,
    SYSTEM_ERROR
}

