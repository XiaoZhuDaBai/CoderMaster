package xiaozhu.judge.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;
import xiaozhu.judge.model.LanguageConfigInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/17 15:59
 */
@Component
@ConfigurationProperties(prefix = "sandbox")
@Validated
@Data
public class SandboxPoolConfig {
    @NotBlank(message = "宿主机代码根目录不能为空")
    private String hostCodeBaseDir = System.getProperty("user.dir");

    @Valid
    private DockerConfig docker = new DockerConfig();

    @Valid
    private MonitoringConfig monitoring = new MonitoringConfig();

    @Valid
    private SecurityConfig security = new SecurityConfig();

    @Valid
    @NotEmpty(message = "语言沙箱配置不能为空")
    private Map<String, LanguageConfigInfo> languages = new HashMap<>();

    /**
     * Docker 配置
     */
    @Data
    public static class DockerConfig {
        @NotBlank(message = "Docker主机地址不能为空")
        private String host = "tcp://localhost:2375";

        @Positive(message = "连接超时时间必须大于0")
        private int connectTimeout = 30000;

        @Positive(message = "读取超时时间必须大于0")
        private int readTimeout = 60000;

    }

    /**
     * 监控配置
     */
    @Data
    public static class MonitoringConfig {
        @Positive(message = "健康检查间隔必须大于0")
        private long healthCheckInterval = 30000;

        private boolean metricsEnabled = true;

    }

    /**
     * 安全配置
     */
    @Data
    public static class SecurityConfig {
        private boolean enableNetworkIsolation = true;

        @Positive(message = "最大文件大小必须大于0")
        private long maxFileSize = 10485760; // 10MB

        private String[] allowedSystemCalls = {"read", "write", "exit"};
    }
}
