package xiaozhu.judge.pool.model;

import lombok.Getter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 容器信息封装
 * 包含容器的基本信息和状态
 */
@Getter
public class ContainerInfo {
    
    private final String containerId;
    private final String language;
    private final String imageName;
    private final LocalDateTime createdAt;
    private final AtomicBoolean inUse;
    private final AtomicBoolean healthy;
    private LocalDateTime lastUsedAt;
    private int usageCount;
    
    public ContainerInfo(String containerId, String language, String imageName) {
        this.containerId = containerId;
        this.language = language;
        this.imageName = imageName;
        this.createdAt = LocalDateTime.now();
        this.inUse = new AtomicBoolean(false);
        this.healthy = new AtomicBoolean(true);
        this.lastUsedAt = LocalDateTime.now();
        this.usageCount = 0;
    }
    
    /**
     * 获取容器
     */
    public boolean acquire() {
        if (inUse.compareAndSet(false, true)) {
            lastUsedAt = LocalDateTime.now();
            usageCount++;
            return true;
        }
        return false;
    }
    
    /**
     * 归还容器
     */
    public void release() {
        inUse.set(false);
    }
    
    /**
     * 标记为不健康
     */
    public void markUnhealthy() {
        healthy.set(false);
    }
    
    /**
     * 检查是否健康
     */
    public boolean isHealthy() {
        return healthy.get() && !inUse.get();
    }
    
    /**
     * 检查是否在使用中
     */
    public boolean isInUse() {
        return inUse.get();
    }
    
    /**
     * 获取容器年龄（分钟）
     */
    public long getAgeInMinutes() {
        return Duration.between(createdAt, LocalDateTime.now()).toMinutes();
    }
    
    /**
     * 获取空闲时间（分钟）
     */
    public long getIdleTimeInMinutes() {
        return Duration.between(lastUsedAt, LocalDateTime.now()).toMinutes();
    }

    
    @Override
    public String toString() {
        return String.format("ContainerInfo{id='%s', language='%s', inUse=%s, healthy=%s, usageCount=%d}", 
                containerId, language, inUse.get(), healthy.get(), usageCount);
    }
}
