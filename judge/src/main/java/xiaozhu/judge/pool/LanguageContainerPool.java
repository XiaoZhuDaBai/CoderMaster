package xiaozhu.judge.pool;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StopWatch;
import xiaozhu.judge.model.LanguageConfigInfo;
import xiaozhu.judge.pool.exception.ContainerPoolException;
import xiaozhu.judge.pool.metrics.ContainerPoolMetrics;
import xiaozhu.judge.pool.model.ContainerInfo;
import xiaozhu.judge.util.DockerClient;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 单语言容器池实现
 * 管理特定语言的容器生命周期
 */
public class LanguageContainerPool {

    private static final Logger log = LoggerFactory.getLogger(LanguageContainerPool.class);

    private final String language;
    private final LanguageConfigInfo config;
    private final DockerClient dockerClient;
    private final String hostCodePath;
    private final ContainerPoolMetrics metrics;

    // 容器池
    private final BlockingQueue<ContainerInfo> availableContainers;
    private final List<ContainerInfo> allContainers;
    private final AtomicInteger currentPoolSize;

    // 线程池
    private final ScheduledExecutorService healthCheckExecutor;
    private final ExecutorService containerCreationExecutor;

    // 状态控制
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicBoolean shutdown = new AtomicBoolean(false);

    public LanguageContainerPool(String language, LanguageConfigInfo config, DockerClient dockerClient,
                                 String hostCodePath, ContainerPoolMetrics metrics) {
        this.language = language;
        this.config = config;
        this.dockerClient = dockerClient;
        this.hostCodePath = hostCodePath;
        this.metrics = metrics;

        this.availableContainers = new LinkedBlockingQueue<>();
        this.allContainers = new CopyOnWriteArrayList<>();
        this.currentPoolSize = new AtomicInteger(0);

        this.healthCheckExecutor = Executors.newSingleThreadScheduledExecutor(
                r -> new Thread(r, "health-check-" + language));
        this.containerCreationExecutor = Executors.newCachedThreadPool(
                r -> new Thread(r, "container-creation-" + language));
    }

    /**
     * 初始化容器池
     */
    public void initialize() {
        if (initialized.compareAndSet(false, true)) {
            try {
                log.info("开始初始化 {} 容器池，目标大小: {}", language, config.poolSize());

                // 清理可能存在的冲突容器
                cleanupConflictingContainers();

                // 预创建容器
                for (int i = 0; i < config.poolSize(); i++) {
                    containerCreationExecutor.submit(this::createContainer);
                }

                // 启动健康检查
                startHealthCheck();

                log.info("{} 容器池初始化完成", language);
            } catch (Exception e) {
                shutdown.set(true);
                shutdownExecutors(true);
                initialized.set(false);
                throw e;
            }
        }
    }

    /**
     * 清理可能存在的冲突容器
     */
    private void cleanupConflictingContainers() {
        try {
            List<Map<String, Object>> containers = dockerClient.listContainersByName(config.containerNamePrefix());
            for (Map<String, Object> container : containers) {
                String containerId = (String) container.get("Id");
                List<String> names = (List<String>) container.get("Names");
                String containerName = names != null && !names.isEmpty() ? names.get(0) : containerId;
                try {
                    dockerClient.stopContainer(containerId, 5);
                } catch (IOException stopEx) {
                    log.debug("停止容器 {} 失败: {}", containerId, stopEx.getMessage());
                }
                try {
                    dockerClient.removeContainer(containerId, true);
                    log.info("清理冲突容器: {} ({})", containerName, containerId);
                } catch (IOException removeEx) {
                    log.warn("删除容器 {} 失败: {}", containerId, removeEx.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("清理冲突容器时发生异常: {}", e.getMessage());
        }
    }

    /**
     * 获取容器
     */
    public ContainerInfo getContainer(long timeoutMs) throws ContainerPoolException {
        if (shutdown.get()) {
            throw new ContainerPoolException.ContainerNotAvailableException("容器池已关闭");
        }

        StopWatch stopWatch = new StopWatch();
        stopWatch.start();

        final long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);

        try {
            while (true) {
                long remainingNs = deadline - System.nanoTime();
                if (remainingNs <= 0) {
                    stopWatch.stop();
                    metrics.recordTimeout();
                    throw new ContainerPoolException.ContainerNotAvailableException(
                            String.format("获取 %s 容器超时，等待时间: %dms", language, timeoutMs));
                }

                long waitMillis = Math.max(1, TimeUnit.NANOSECONDS.toMillis(remainingNs));
                ContainerInfo container = availableContainers.poll(waitMillis, TimeUnit.MILLISECONDS);
                if (container == null) {
                    stopWatch.stop();
                    metrics.recordTimeout();
                    throw new ContainerPoolException.ContainerNotAvailableException(
                            String.format("获取 %s 容器超时，等待时间: %dms", language, timeoutMs));
                }

                if (!container.acquire()) {
                    continue;
                }

                stopWatch.stop();
                metrics.recordAcquired(stopWatch.getLastTaskTimeMillis());
                log.debug("成功获取 {} 容器: {}", language, container.getContainerId());
                return container;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ContainerPoolException.ContainerNotAvailableException("获取容器被中断");
        }
    }

    /**
     * 归还容器
     */
    public void returnContainer(ContainerInfo container) {
        if (container == null) {
            return;
        }

        container.release();
        metrics.recordReturned();

        if (container.isHealthy() && !shutdown.get()) {
            availableContainers.offer(container);
            log.debug("归还 {} 容器: {}", language, container.getContainerId());
        } else {
            // 容器不健康，需要替换
            replaceContainer(container);
        }
    }

    /**
     * 创建新容器
     */
    private void createContainer() {
        int maxRetries = 3;
        int retryCount = 0;

        while (retryCount < maxRetries) {
            try {
                log.debug("开始创建 {} 容器 (尝试 {}/{})", language, retryCount + 1, maxRetries);

                // 确保镜像存在
                if (!dockerClient.ensureImageExists(config.imageName())) {
                    log.warn("镜像 {} 不存在或拉取失败，跳过容器创建", config.imageName());
                    return;
                }

                String containerName = config.generateContainerName(currentPoolSize.get());

                // 创建容器
                String containerId = dockerClient.createSandboxContainer(
                        config,
                        containerName,
                        hostCodePath,
                        List.of("LANG=C.UTF-8", "LC_ALL=C.UTF-8"));

                // 立即启动容器并保持运行状态（真正的容器复用！）
                dockerClient.startContainer(containerId);
                log.debug("启动容器: {}", containerId);

                ContainerInfo containerInfo = new ContainerInfo(containerId, language, config.imageName());

                allContainers.add(containerInfo);
                availableContainers.offer(containerInfo);
                currentPoolSize.incrementAndGet();
                metrics.recordCreated();

                log.info("成功创建并启动 {} 容器: {} (池大小: {}/{})",
                        language, containerId, currentPoolSize.get(), config.poolSize());

                // 成功创建，跳出重试循环
                break;

            } catch (IOException e) {
                retryCount++;
                log.warn("创建 {} 容器失败 (尝试 {}/{}): {}", language, retryCount, maxRetries, e.getMessage());
                if (retryCount >= maxRetries) {
                    log.error("创建 {} 容器失败，已达到最大重试次数: {}", language, e.getMessage());
                    metrics.recordFault();
                }
                try {
                    Thread.sleep(200);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            } catch (Exception e) {
                log.error("创建 {} 容器失败: {}", language, e.getMessage(), e);
                metrics.recordFault();
                break;
            }
        }
    }

    /**
     * 替换不健康的容器
     */
    private void replaceContainer(ContainerInfo oldContainer) {
        log.info("替换不健康的 {} 容器: {}", language, oldContainer.getContainerId());

        // 销毁旧容器
        destroyContainer(oldContainer);

        // 创建新容器
        if (!shutdown.get()) {
            containerCreationExecutor.submit(this::createContainer);
        }
    }

    /**
     * 销毁容器
     */
    private void destroyContainer(ContainerInfo container) {
        try {
            allContainers.remove(container);
            currentPoolSize.decrementAndGet();
            metrics.recordDestroyed();

            dockerClient.removeContainer(container.getContainerId(), true);

            log.debug("销毁 {} 容器: {}", language, container.getContainerId());
        } catch (Exception e) {
            log.error("销毁 {} 容器失败: {}", language, e.getMessage(), e);
        }
    }

    /**
     * 启动健康检查
     */
    private void startHealthCheck() {
        healthCheckExecutor.scheduleWithFixedDelay(() -> {
            try {
                performHealthCheck();
            } catch (Exception e) {
                log.error("健康检查异常: {}", e.getMessage(), e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    /**
     * 执行健康检查
     */
    private void performHealthCheck() {
        log.debug("开始 {} 容器池健康检查", language);

        // 先遍历现有容器，标记并替换不健康 / 老化的容器
        for (ContainerInfo container : allContainers) {
            try {
                // 检查容器状态
                Map<String, Object> inspect = dockerClient.inspectContainer(container.getContainerId());
                Map<String, Object> state = inspect != null ? (Map<String, Object>) inspect.get("State") : null;
                String status = state != null ? (String) state.get("Status") : null;

                boolean unhealthy = (!"running".equals(status));
                boolean aged = container.getAgeInMinutes() > 60;

                if (unhealthy) {
                    log.warn("发现不健康的 {} 容器: {}", language, container.getContainerId());
                }
                if (aged) {
                    log.info("重置老化的 {} 容器: {}", language, container.getContainerId());
                }

                // 只要有一个条件命中，就视为需要替换
                if (unhealthy || aged) {
                    container.markUnhealthy();
                    availableContainers.remove(container);
                    // 通过统一的替换逻辑销毁并补充新容器
                    replaceContainer(container);
                }

            } catch (Exception e) {
                log.warn("检查 {} 容器状态失败: {}", container.getContainerId(), e.getMessage());
                container.markUnhealthy();
                availableContainers.remove(container);
                // 检查失败同样触发替换，避免死挂的容器占位
                replaceContainer(container);
            }
        }

        // 经过 replaceContainer 之后，currentPoolSize 已按销毁的数量更新
        // 再根据最新的池大小补齐（如果创建过程中有失败，metrics 和 currentPoolSize 也能反映出来）
        int currentSize = currentPoolSize.get();
        int targetSize = config.poolSize();
        if (currentSize < targetSize && !shutdown.get()) {
            int needCreate = targetSize - currentSize;
            for (int i = 0; i < needCreate; i++) {
                containerCreationExecutor.submit(this::createContainer);
            }
        }

        log.debug("{} 容器池健康检查完成，当前大小: {}/{}", language, currentSize, targetSize);
    }

    /**
     * 获取池状态
     */
    public PoolStatus getStatus() {
        return new PoolStatus(
                language,
                currentPoolSize.get(),
                config.poolSize(),
                availableContainers.size(),
                allContainers.stream().mapToInt(ContainerInfo::getUsageCount).sum()
        );
    }

    /**
     * 关闭容器池
     */
    public void shutdown() {
        if (shutdown.compareAndSet(false, true)) {
            log.info("开始关闭 {} 容器池", language);

            shutdownExecutors(false);

            // 销毁所有容器
            allContainers.forEach(this::destroyContainer);
            allContainers.clear();
            availableContainers.clear();
            currentPoolSize.set(0);

            log.info("{} 容器池已关闭", language);
        }
    }

    private void shutdownExecutors(boolean force) {
        if (force) {
            healthCheckExecutor.shutdownNow();
            containerCreationExecutor.shutdownNow();
        } else {
            healthCheckExecutor.shutdown();
            containerCreationExecutor.shutdown();
        }
    }

    /**
     * 池状态信息
     */
    public record PoolStatus(String language, int currentSize, int targetSize, int availableSize, int totalUsageCount) {

        public double getUtilizationRate() {
            return targetSize > 0 ? (double) (targetSize - availableSize) / targetSize : 0.0;
        }
    }
}
