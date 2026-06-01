package xiaozhu.judge.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import xiaozhu.judge.pool.MultiLanguageDockerSandBoxPool;
import xiaozhu.judge.pool.LanguageContainerPool;
import xiaozhu.judge.pool.metrics.ContainerPoolMetrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 容器池指标收集器
 * 用于收集判题系统容器池的使用情况
 */
@Slf4j
@Service
public class ContainerMetricsService {

    private final MultiLanguageDockerSandBoxPool containerPool;
    private final MeterRegistry meterRegistry;
    
    // 标记是否已经初始化完成
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    public ContainerMetricsService(MultiLanguageDockerSandBoxPool containerPool, MeterRegistry meterRegistry) {
        this.containerPool = containerPool;
        this.meterRegistry = meterRegistry;
    }

    // 使用 Map 缓存已注册的 Gauge，避免重复注册
    private final Map<String, Gauge> poolSizeGauges = new ConcurrentHashMap<>();
    private final Map<String, Gauge> poolAvailableGauges = new ConcurrentHashMap<>();
    private final Map<String, Gauge> poolInUseGauges = new ConcurrentHashMap<>();
    private final Map<String, Gauge> metricsCreatedGauges = new ConcurrentHashMap<>();
    private final Map<String, Gauge> metricsDestroyedGauges = new ConcurrentHashMap<>();
    private final Map<String, Gauge> metricsFaultsGauges = new ConcurrentHashMap<>();
    private final Map<String, Gauge> metricsTimeoutsGauges = new ConcurrentHashMap<>();
    private final Map<String, Gauge> metricsReturnedGauges = new ConcurrentHashMap<>();
    private final Map<String, Gauge> metricsLatencyGauges = new ConcurrentHashMap<>();
    
    // 使用 AtomicReference 来持有可变的状态值
    private final Map<String, AtomicReference<LanguageContainerPool.PoolStatus>> poolStatusRefs = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<ContainerPoolMetrics>> metricsRefs = new ConcurrentHashMap<>();

    /**
     * 定期更新容器池指标
     * 每30秒更新一次
     */
    @Scheduled(fixedRate = 30000)
    public void updateContainerMetrics() {
        try {
            Map<String, LanguageContainerPool.PoolStatus> poolStatusMap = containerPool.getAllPoolStatus();
            Map<String, ContainerPoolMetrics> metricsMap = containerPool.getAllMetrics();

            if (poolStatusMap.isEmpty()) {
                log.warn("容器池状态为空，无法更新指标");
                return;
            }

            log.debug("开始更新容器池指标，语言数量: {}", poolStatusMap.size());

            // 更新每个语言池的指标
            for (Map.Entry<String, LanguageContainerPool.PoolStatus> entry : poolStatusMap.entrySet()) {
                String language = entry.getKey();
                LanguageContainerPool.PoolStatus status = entry.getValue();
                ContainerPoolMetrics metrics = metricsMap.get(language);

                if (status != null && metrics != null) {
                    // 更新状态引用
                    AtomicReference<LanguageContainerPool.PoolStatus> statusRef = 
                            poolStatusRefs.computeIfAbsent(language, k -> new AtomicReference<>());
                    statusRef.set(status);
                    
                    AtomicReference<ContainerPoolMetrics> metricsRef = 
                            metricsRefs.computeIfAbsent(language, k -> new AtomicReference<>());
                    metricsRef.set(metrics);

                    // 注册或更新 Gauge 指标（绑定到 AtomicReference，每次读取时获取最新值）
                    registerOrUpdateGauge("judge_container_pool_size", language, statusRef, 
                            ref -> (double) ref.get().currentSize(), poolSizeGauges, "判题容器池当前大小");
                    registerOrUpdateGauge("judge_container_pool_available", language, statusRef,
                            ref -> (double) ref.get().availableSize(), poolAvailableGauges, "判题容器池可用容器数");
                    registerOrUpdateGauge("judge_container_pool_in_use", language, statusRef,
                            ref -> (double) (ref.get().currentSize() - ref.get().availableSize()), poolInUseGauges, "判题容器池使用中容器数");

                    // 更新累计指标
                    registerOrUpdateGauge("judge_container_metrics_created", language, metricsRef,
                            ref -> (double) ref.get().getCreated(), metricsCreatedGauges, "判题容器创建总数");
                    registerOrUpdateGauge("judge_container_metrics_destroyed", language, metricsRef,
                            ref -> (double) ref.get().getDestroyed(), metricsDestroyedGauges, "判题容器销毁总数");
                    registerOrUpdateGauge("judge_container_metrics_faults", language, metricsRef,
                            ref -> (double) ref.get().getFaults(), metricsFaultsGauges, "判题容器故障总数");
                    registerOrUpdateGauge("judge_container_metrics_timeouts", language, metricsRef,
                            ref -> (double) ref.get().getTimeouts(), metricsTimeoutsGauges, "判题容器获取超时总数");
                    registerOrUpdateGauge("judge_container_metrics_returned", language, metricsRef,
                            ref -> (double) ref.get().getReturned(), metricsReturnedGauges, "判题容器归还总数");
                    registerOrUpdateGauge("judge_container_metrics_acquire_latency_avg", language, metricsRef,
                            ref -> ref.get().getAverageAcquireLatencyMs(), metricsLatencyGauges, "判题容器获取平均耗时（毫秒）");

                    log.debug("已注册指标: {} - poolSize={}, available={}, created={}", 
                            language, status.currentSize(), status.availableSize(), metrics.getCreated());
                } else {
                    log.warn("语言 {} 的指标为空: status={}, metrics={}", language, status, metrics);
                }
            }
            
            // 首次成功更新后记录日志
            if (!initialized.get() && !poolStatusMap.isEmpty()) {
                log.info("容器池指标初始化成功，支持的语言: {}", poolStatusMap.keySet());
                log.info("已注册的 Gauge 数量: poolSize={}, available={}, inUse={}", 
                        poolSizeGauges.size(), poolAvailableGauges.size(), poolInUseGauges.size());
                initialized.set(true);
            }
        } catch (Exception e) {
            log.error("更新容器指标失败", e);
        }
    }

    private <T> void registerOrUpdateGauge(String name, String language, T state, 
                                           java.util.function.ToDoubleFunction<T> valueFunction,
                                           Map<String, Gauge> gaugeCache, String description) {
        gaugeCache.computeIfAbsent(language, key -> 
            Gauge.builder(name, state, valueFunction)
                    .description(description)
                    .tag("language", language)
                    .register(meterRegistry)
        );
    }

    /**
     * 记录容器创建
     */
    public void recordContainerCreated(String language) {
        Counter.builder("judge.container.created")
                .description("判题容器创建总数")
                .tag("language", language)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录容器销毁
     */
    public void recordContainerDestroyed(String language) {
        Counter.builder("judge.container.destroyed")
                .description("判题容器销毁总数")
                .tag("language", language)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录容器故障
     */
    public void recordContainerFault(String language) {
        Counter.builder("judge.container.faults")
                .description("判题容器故障总数")
                .tag("language", language)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录容器获取超时
     */
    public void recordContainerTimeout(String language) {
        Counter.builder("judge.container.timeouts")
                .description("判题容器获取超时总数")
                .tag("language", language)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录容器归还
     */
    public void recordContainerReturned(String language) {
        Counter.builder("judge.container.returned")
                .description("判题容器归还总数")
                .tag("language", language)
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录容器获取耗时
     */
    public void recordContainerAcquireTime(String language, long durationMs) {
        Timer.builder("judge.container.acquire.duration")
                .description("判题容器获取耗时")
                .tag("language", language)
                .register(meterRegistry)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }
}

