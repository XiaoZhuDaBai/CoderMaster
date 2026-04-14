package xiaozhu.judge.pool;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import xiaozhu.judge.config.SandboxPoolConfig;
import xiaozhu.judge.model.LanguageConfigInfo;
import xiaozhu.judge.pool.exception.ContainerPoolException;
import xiaozhu.judge.pool.metrics.ContainerPoolMetrics;
import xiaozhu.judge.pool.model.ContainerInfo;
import xiaozhu.judge.util.DockerClient;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 多语言Docker容器池管理器
 * 统一管理所有语言的容器池
 */
@Slf4j
@Component
public class MultiLanguageDockerSandBoxPool {

    
    private final SandboxPoolConfig properties;
    private final DockerClient dockerClient;
    private final Map<String, LanguageContainerPool> languagePools = new ConcurrentHashMap<>();
    private final Map<String, ContainerPoolMetrics> languageMetrics = new ConcurrentHashMap<>();

    public MultiLanguageDockerSandBoxPool(SandboxPoolConfig properties, DockerClient dockerClient) {
        this.properties = properties;
        this.dockerClient = dockerClient;
    }

    @PostConstruct
    public void initialize() {
        try {
            log.info("开始初始化多语言容器池...");
            
            // 检查配置
            if (properties == null) {
                log.error("SandboxPoolProperties 未注入！");
                throw new RuntimeException("SandboxPoolProperties 未注入");
            }
            
            // 验证Docker连接
            validateDockerConnection();
            
            // 初始化各语言容器池
            initializeLanguagePools();
            
            log.info("多语言容器池初始化完成，支持语言: {}", languagePools.keySet());
            
        } catch (Exception e) {
            log.error("容器池初始化失败: {}", e.getMessage(), e);
            throw new RuntimeException("容器池初始化失败", e);
        }
    }
    
    /**
     * 验证Docker连接
     */
    private void validateDockerConnection() {
        if (dockerClient.ping()) {
            log.info("Docker连接验证成功");
        } else {
            log.error("Docker连接验证失败");
            throw new RuntimeException("无法连接到Docker守护进程");
        }
    }
    
    /**
     * 初始化各语言容器池
     */
    private void initializeLanguagePools() {
        if (properties.getLanguages() == null || properties.getLanguages().isEmpty()) {
            log.warn("未配置任何语言容器池");
            return;
        }
        
        Map<String, String> failedLanguages = new LinkedHashMap<>();

        for (Map.Entry<String, LanguageConfigInfo> entry : properties.getLanguages().entrySet()) {
            String language = entry.getKey();
            LanguageConfigInfo config = entry.getValue();
            
            try {
                // 验证配置
                config.validate();
                
                // 预热镜像
                if (!prewarmImage(config.imageName())) {
                    log.warn("镜像 {} 预热失败，跳过 {} 容器池初始化", config.imageName(), language);
                    failedLanguages.put(language, "镜像预热失败");
                    continue;
                }
                
                // 创建指标收集器
                ContainerPoolMetrics metrics = new ContainerPoolMetrics();
                
                // 创建语言容器池
                LanguageContainerPool pool = new LanguageContainerPool(
                        language, config, dockerClient, properties.getHostCodeBaseDir(), metrics);
                
                // 初始化池
                pool.initialize();
                languagePools.put(language, pool);
                languageMetrics.put(language, metrics);
                
                log.info("{} 容器池初始化完成，配置: {}", language, config);
                
            } catch (Exception e) {
                log.error("初始化 {} 容器池失败: {}", language, e.getMessage(), e);
                failedLanguages.put(language, e.getMessage() != null ? e.getMessage() : "未知错误");
                // 不抛出异常，继续初始化其他语言池
                log.warn("跳过 {} 容器池初始化", language);
            }
        }

        if (languagePools.isEmpty()) {
            throw new IllegalStateException("所有语言容器池初始化失败: " + failedLanguages);
        }

        if (!failedLanguages.isEmpty()) {
            log.warn("部分语言容器池初始化失败: {}", failedLanguages);
        }
    }
    
    /**
     * 预热Docker镜像
     */
    private boolean prewarmImage(String imageName) {
        try {
            log.info("开始预热镜像: {}", imageName);
            if (dockerClient.imageExists(imageName)) {
                log.info("镜像 {} 已存在，跳过拉取", imageName);
                return true;
            }

            dockerClient.pullImage(imageName, message -> log.info("[DOCKER PULL] {}", message));
            log.info("镜像 {} 预热完成", imageName);
            return true;
        } catch (IOException e) {
            log.error("拉取镜像 {} 失败: {}", imageName, e.getMessage());
            return false;
        }
    }

    /**
     * 获取容器
     */
    public ContainerInfo getContainer(String language) throws ContainerPoolException {
        return getContainer(language, 5000); // 默认5秒超时
    }
    
    /**
     * 获取容器（带超时）
     */
    public ContainerInfo getContainer(String language, long timeoutMs) throws ContainerPoolException {
        LanguageContainerPool pool = languagePools.get(language);
        if (pool == null) {
            throw new ContainerPoolException.ContainerNotAvailableException(
                    "不支持的语言: " + language);
        }
        
        return pool.getContainer(timeoutMs);
    }
    
    /**
     * 归还容器
     */
    public void returnContainer(ContainerInfo container) {
        if (container == null) {
            return;
        }
        
        LanguageContainerPool pool = languagePools.get(container.getLanguage());
        if (pool != null) {
            pool.returnContainer(container);
        } else {
            log.warn("找不到语言 {} 的容器池", container.getLanguage());
        }
    }

    
    @PreDestroy
    public void shutdown() {
        log.info("开始关闭多语言容器池...");
        
        // 关闭各语言容器池
        for (LanguageContainerPool pool : languagePools.values()) {
            try {
                pool.shutdown();
            } catch (Exception e) {
                log.error("关闭容器池时发生异常: {}", e.getMessage(), e);
            }
        }
        
        languagePools.clear();
        languageMetrics.clear();
        
        // 关闭Docker客户端
        if (dockerClient != null) {
            try {
                dockerClient.close();
            } catch (Exception e) {
                log.error("关闭Docker客户端时发生异常: {}", e.getMessage(), e);
            }
        }
        
        log.info("多语言容器池已关闭");
    }

    /**
     * 获取池状态
     */
    public Map<String, LanguageContainerPool.PoolStatus> getAllPoolStatus() {
        Map<String, LanguageContainerPool.PoolStatus> statusMap = new ConcurrentHashMap<>();
        languagePools.forEach((language, pool) -> {
            statusMap.put(language, pool.getStatus());
        });
        return statusMap;
    }

    /**
     * 获取指定语言的池状态
     */
    public LanguageContainerPool.PoolStatus getPoolStatus(String language) {
        LanguageContainerPool pool = languagePools.get(language);
        return pool != null ? pool.getStatus() : null;
    }

    /**
     * 获取指标
     */
    public Map<String, ContainerPoolMetrics> getAllMetrics() {
        return new ConcurrentHashMap<>(languageMetrics);
    }

    /**
     * 获取指定语言的指标
     */
    public ContainerPoolMetrics getMetrics(String language) {
        return languageMetrics.get(language);
    }

    /**
     * 健康检查
     */
    public boolean isHealthy() {
        try {
            // 检查Docker连接
            if (!dockerClient.ping()) {
                log.warn("Docker ping 失败");
                return false;
            }

            // 如果没有任何容器池，认为不健康
            if (languagePools.isEmpty()) {
                log.warn("没有可用的容器池");
                return false;
            }

            // 检查各语言池状态，至少有一个池有容器就认为健康
            int healthyPoolCount = 0;
            for (LanguageContainerPool pool : languagePools.values()) {
                LanguageContainerPool.PoolStatus status = pool.getStatus();
                if (status.currentSize() > 0) {
                    healthyPoolCount++;
                } else {
                    log.warn("语言 {} 的容器池为空", status.language());
                }
            }

            boolean isHealthy = healthyPoolCount > 0;
            if (isHealthy) {
                log.debug("健康检查通过，可用容器池数量: {}/{}", healthyPoolCount, languagePools.size());
            } else {
                log.warn("健康检查失败，没有可用的容器池");
            }

            return isHealthy;
        } catch (Exception e) {
            log.error("健康检查失败: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取支持的编程语言
     */
    public String[] getSupportedLanguages() {
        return languagePools.keySet().toArray(new String[0]);
    }

    /**
     * 获取初始化状态信息
     */
    public Map<String, Object> getInitializationStatus() {
        Map<String, Object> status = new ConcurrentHashMap<>();
        status.put("totalConfiguredLanguages", properties.getLanguages() != null ? properties.getLanguages().size() : 0);
        status.put("successfullyInitializedLanguages", languagePools.size());
        status.put("availableLanguages", languagePools.keySet());

        if (properties.getLanguages() != null) {
            Map<String, String> failedLanguages = new ConcurrentHashMap<>();
            for (String language : properties.getLanguages().keySet()) {
                if (!languagePools.containsKey(language)) {
                    failedLanguages.put(language, "初始化失败或镜像不可用");
                }
            }
            if (!failedLanguages.isEmpty()) {
                status.put("failedLanguages", failedLanguages);
            }
        }

        return status;
    }
}

