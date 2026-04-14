package xiaozhu.judge.model;


import com.alibaba.cloud.commons.lang.StringUtils;
import jakarta.validation.constraints.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.UUID.randomUUID;

/**
 * 语言沙箱配置的不可变载体
 * 使用 record 确保配置的不可变性和线程安全
 */
public record LanguageConfigInfo(
    @NotBlank(message = "镜像名称不能为空")
    String imageName,
    
    @NotBlank(message = "容器名称前缀不能为空")
    String containerNamePrefix,
    
    @NotBlank(message = "挂载目录不能为空")
    String volumeDir,
    
    @Min(value = 64 * 1024 * 1024, message = "内存限制不能小于64MB")
    long memoryLimit,
    
    @Min(value = 64 * 1024 * 1024, message = "内存交换限制不能小于64MB")
    long memorySwapLimit,
    
    @DecimalMin(value = "0.1", message = "CPU限制不能小于0.1")
    @DecimalMax(value = "8.0", message = "CPU限制不能大于8.0")
    double cpuLimit,
    
    @Min(value = 1, message = "池大小不能小于1")
    @Max(value = 20, message = "池大小不能大于20")
    int poolSize,
    
    @Min(value = 1000, message = "编译超时时间不能小于1秒")
    long compileTimeout,
    
    @Min(value = 1000, message = "执行超时时间不能小于1秒")
    long executeTimeout,
    
    // 新增命令配置
    String compileCmd,
    String runCmd,
    boolean needCompile,
    boolean useContainerPool
) {
    /**
     * 构建 HostConfig 映射，供 REST API 调用
     */
    public Map<String, Object> buildHostConfig(String hostCodePath) {
        if (StringUtils.isBlank(hostCodePath)) {
            throw new IllegalArgumentException("宿主机代码路径不能为空");
        }

        Map<String, Object> hostConfig = new HashMap<>();
        hostConfig.put("Memory", memoryLimit);
        hostConfig.put("MemorySwap", memorySwapLimit);
        hostConfig.put("CpuQuota", (long) (cpuLimit * 100000));
        hostConfig.put("CpuPeriod", 100000L);
        hostConfig.put("NetworkMode", "none");
        hostConfig.put("ReadonlyRootfs", false);
        hostConfig.put("Binds", List.of(hostCodePath + ":" + volumeDir));
        hostConfig.put("CapDrop", List.of("ALL"));
        hostConfig.put("CapAdd", List.of("CHOWN", "DAC_OVERRIDE"));
        hostConfig.put("SecurityOpt", List.of("no-new-privileges:true"));
        return hostConfig;
    }

    /**
     * 生成容器名称
     */
    public String generateContainerName(int index) {
        return String.format("%s-%d-%s", containerNamePrefix, index, randomUUID().toString().substring(0, 8));
    }

    /**
     * 验证配置的合理性
     */
    public void validate() {
        if (memorySwapLimit < memoryLimit) {
            throw new IllegalArgumentException("内存交换限制不能小于内存限制");
        }
    }
}
