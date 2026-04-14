package xiaozhu.ai.exception;

/**
 * AI 生成相关业务异常
 */
public class AiGenerationException extends RuntimeException {

    private final AiErrorType errorType;
    private final String detail;

    public AiGenerationException(AiErrorType errorType, String message) {
        super(message);
        this.errorType = errorType;
        this.detail = null;
    }

    public AiGenerationException(AiErrorType errorType, String message, String detail) {
        super(message);
        this.errorType = errorType;
        this.detail = detail;
    }

    public AiErrorType getErrorType() {
        return errorType;
    }

    public String getDetail() {
        return detail;
    }

    public boolean isRetryable() {
        return errorType.isRetryable();
    }

    @Override
    public String toString() {
        return String.format("AiGenerationException{type=%s, message='%s', detail='%s'}",
                errorType, getMessage(), detail);
    }
}
