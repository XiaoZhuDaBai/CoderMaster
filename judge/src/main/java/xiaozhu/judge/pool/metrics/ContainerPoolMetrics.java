package xiaozhu.judge.pool.metrics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 容器池指标收集器（简易实现）
 */
public class ContainerPoolMetrics {

    private final AtomicInteger created = new AtomicInteger();
    private final AtomicInteger destroyed = new AtomicInteger();
    private final AtomicInteger faults = new AtomicInteger();
    private final AtomicInteger timeouts = new AtomicInteger();
    private final AtomicInteger returned = new AtomicInteger();
    private final AtomicLong acquireLatencyMs = new AtomicLong();
    private final AtomicInteger acquireCount = new AtomicInteger();

    public void recordCreated() {
        created.incrementAndGet();
    }

    public void recordDestroyed() {
        destroyed.incrementAndGet();
    }

    public void recordFault() {
        faults.incrementAndGet();
    }

    public void recordTimeout() {
        timeouts.incrementAndGet();
    }

    public void recordReturned() {
        returned.incrementAndGet();
    }

    public void recordAcquired(long latencyMs) {
        acquireLatencyMs.addAndGet(latencyMs);
        acquireCount.incrementAndGet();
    }

    public int getCreated() {
        return created.get();
    }

    public int getDestroyed() {
        return destroyed.get();
    }

    public int getFaults() {
        return faults.get();
    }

    public int getTimeouts() {
        return timeouts.get();
    }

    public int getReturned() {
        return returned.get();
    }

    public double getAverageAcquireLatencyMs() {
        int count = acquireCount.get();
        return count == 0 ? 0 : (double) acquireLatencyMs.get() / count;
    }
}


