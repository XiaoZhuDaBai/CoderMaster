package xiaozhu.judge.pool.exception;

/**
 * 容器池相关异常
 */
public class ContainerPoolException extends RuntimeException {
    
    public ContainerPoolException(String message) {
        super(message);
    }
    
    public ContainerPoolException(String message, Throwable cause) {
        super(message, cause);
    }
    
    /**
     * 容器不可用异常
     */
    public static class ContainerNotAvailableException extends ContainerPoolException {
        public ContainerNotAvailableException(String message) {
            super(message);
        }
    }
    
    /**
     * 容器池已满异常
     */
    public static class PoolFullException extends ContainerPoolException {
        public PoolFullException(String message) {
            super(message);
        }
    }
    
    /**
     * 容器创建失败异常
     */
    public static class ContainerCreationException extends ContainerPoolException {
        public ContainerCreationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
