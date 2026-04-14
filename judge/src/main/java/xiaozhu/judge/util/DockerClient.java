package xiaozhu.judge.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import xiaozhu.judge.config.SandboxPoolConfig;
import xiaozhu.judge.model.LanguageConfigInfo;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Consumer;

/**
 * 轻量级 Docker Engine REST 客户端
 * 基于 OkHttp 调用 Docker Engine API，避免额外的本地依赖
 */
@Component
public class DockerClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DockerClient.class);

    private static final String DOCKER_API_VERSION = "v1.43";
    private static final MediaType JSON = MediaType.get("application/json");

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String dockerBaseUrl;

    public DockerClient(SandboxPoolConfig config) {
        this.objectMapper = new ObjectMapper();
        this.dockerBaseUrl = normalizeHost(resolveHost(config));
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(config.getDocker().getConnectTimeout()))
                .readTimeout(Duration.ofMillis(config.getDocker().getReadTimeout()))
                .writeTimeout(Duration.ofMillis(config.getDocker().getReadTimeout()))
                .build();
    }

    private static String resolveHost(SandboxPoolConfig config) {
        String host = System.getenv("DOCKER_HOST");
        if (host == null || host.isEmpty()) {
            host = config.getDocker().getHost();
        }
        return host;
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isEmpty()) {
            return "http://localhost:2375";
        }
        if (host.startsWith("tcp://")) {
            return host.replace("tcp://", "http://");
        }
        if (host.startsWith("unix://") || host.startsWith("npipe://")) {
            return "http://localhost:2375";
        }
        if (!host.startsWith("http://") && !host.startsWith("https://")) {
            return "http://" + host;
        }
        return host;
    }

    private String buildUrl(String path) {
        if (path.startsWith("/")) {
            return dockerBaseUrl + path;
        }
        return dockerBaseUrl + "/" + path;
    }

    private String apiPath(String relative) {
        if (relative.startsWith("/")) {
            return DOCKER_API_VERSION + relative;
        }
        return DOCKER_API_VERSION + "/" + relative;
    }

    public boolean ping() {
        Request request = new Request.Builder()
                .url(buildUrl("_ping"))
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            log.error("Docker ping 失败: {}", e.getMessage());
            return false;
        }
    }

    public boolean imageExists(String imageName) throws IOException {
        String encoded = URLEncoder.encode(imageName, StandardCharsets.UTF_8);
        Request request = new Request.Builder()
                .url(buildUrl(apiPath("images/" + encoded + "/json")))
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.code() == 404) {
                return false;
            }
            if (!response.isSuccessful()) {
                throw new IOException("检查镜像失败: " + response.code() + " " + response.message());
            }
            return true;
        }
    }

    public void pullImage(String imageName, Consumer<String> progressConsumer) throws IOException {
        HttpUrl url = HttpUrl.parse(buildUrl(apiPath("images/create")))
                .newBuilder()
                .addQueryParameter("fromImage", imageName)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(new byte[0], JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("拉取镜像失败: " + response.code() + " " + response.message());
            }
            ResponseBody body = response.body();
            if (body != null) {
                String payload = body.string();
                if (progressConsumer != null) {
                    progressConsumer.accept(payload);
                } else {
                    log.debug("镜像拉取返回: {}", payload);
                }
            }
        }
    }

    public List<Map<String, Object>> listContainersByName(String namePrefix) throws IOException {
        Map<String, List<String>> filters = Map.of("name", List.of(namePrefix));
        String filtersJson = objectMapper.writeValueAsString(filters);

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(buildUrl(apiPath("containers/json"))))
                .newBuilder()
                .addQueryParameter("all", "true")
                .addQueryParameter("filters", filtersJson)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("列出容器失败: " + response.code() + " " + response.message());
            }
            String body = response.body() != null ? response.body().string() : "[]";
            return objectMapper.readValue(body, new TypeReference<>() {});
        }
    }

    public String createSandboxContainer(LanguageConfigInfo config,
                                         String containerName,
                                         String hostCodePath,
                                         List<String> env) throws IOException {
        return createSandboxContainer(config, containerName, hostCodePath, env, null);
    }

    public String createSandboxContainer(LanguageConfigInfo config,
                                         String containerName,
                                         String hostCodePath,
                                         List<String> env,
                                         List<String> cmd) throws IOException {
        Map<String, Object> hostConfig = config.buildHostConfig(hostCodePath);

        Map<String, Object> payload = new HashMap<>();
        payload.put("Image", config.imageName());
        payload.put("HostConfig", hostConfig);
        payload.put("AttachStdout", true);
        payload.put("AttachStderr", true);
        payload.put("AttachStdin", true);
        payload.put("OpenStdin", true);
        payload.put("Tty", true);
        payload.put("Env", env != null ? env : Collections.emptyList());
        if (cmd != null && !cmd.isEmpty()) {
            payload.put("Cmd", cmd);
        }

        String json = objectMapper.writeValueAsString(payload);
        HttpUrl url = HttpUrl.parse(buildUrl(apiPath("containers/create")))
                .newBuilder()
                .addQueryParameter("name", containerName)
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(json, JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("创建容器失败: " + response.code() + " " + response.message());
            }
            String responseBody = response.body() != null ? response.body().string() : "{}";
            Map<String, Object> body = objectMapper.readValue(responseBody, new TypeReference<>() {});
            return (String) body.get("Id");
        }
    }

    public void startContainer(String containerId) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(apiPath("containers/" + containerId + "/start")))
                .post(RequestBody.create(new byte[0], JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 304) {
                throw new IOException("启动容器失败: " + response.code() + " " + response.message());
            }
        }
    }

    public void stopContainer(String containerId, int timeoutSeconds) throws IOException {
        HttpUrl url = HttpUrl.parse(buildUrl(apiPath("containers/" + containerId + "/stop")))
                .newBuilder()
                .addQueryParameter("t", String.valueOf(timeoutSeconds))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(new byte[0], JSON))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 304) {
                throw new IOException("停止容器失败: " + response.code() + " " + response.message());
            }
        }
    }

    public void removeContainer(String containerId, boolean force) throws IOException {
        HttpUrl url = HttpUrl.parse(buildUrl(apiPath("containers/" + containerId)))
                .newBuilder()
                .addQueryParameter("force", String.valueOf(force))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .delete()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("删除容器失败: " + response.code() + " " + response.message());
            }
        }
    }

    public Map<String, Object> inspectContainer(String containerId) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(apiPath("containers/" + containerId + "/json")))
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("检查容器状态失败: " + response.code() + " " + response.message());
            }
            String body = response.body() != null ? response.body().string() : "{}";
            return objectMapper.readValue(body, new TypeReference<>() {});
        }
    }

    public String getContainerLogs(String containerId, boolean stdout, boolean stderr) throws IOException {
        HttpUrl url = HttpUrl.parse(buildUrl(apiPath("containers/" + containerId + "/logs")))
                .newBuilder()
                .addQueryParameter("stdout", String.valueOf(stdout))
                .addQueryParameter("stderr", String.valueOf(stderr))
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("获取日志失败: " + response.code() + " " + response.message());
            }
            return response.body() != null ? response.body().string() : "";
        }
    }

    public Map<String, Object> inspectContainer(String containerId, boolean withSize) throws IOException {
        HttpUrl url = HttpUrl.parse(buildUrl(apiPath("containers/" + containerId + "/json")))
                .newBuilder()
                .addQueryParameter("size", String.valueOf(withSize))
                .build();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("检查容器失败: " + response.code() + " " + response.message());
            }
            String body = response.body() != null ? response.body().string() : "{}";
            return objectMapper.readValue(body, new TypeReference<>() {});
        }
    }

    public List<Map<String, Object>> listContainers() throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(apiPath("containers/json?all=true")))
                .get()
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("列出容器失败: " + response.code() + " " + response.message());
            }
            String body = response.body() != null ? response.body().string() : "[]";
            return objectMapper.readValue(body, new TypeReference<>() {});
        }
    }

    public boolean ensureImageExists(String imageName) throws IOException {
        if (imageExists(imageName)) {
            return true;
        }
        pullImage(imageName, message -> log.info("[docker-pull] {}", message));
        return imageExists(imageName);
    }

    public Map<String, Object> waitContainer(String containerId) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(apiPath("containers/" + containerId + "/wait")))
                .post(RequestBody.create(new byte[0], JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("等待容器失败: " + response.code() + " " + response.message());
            }
            String body = response.body() != null ? response.body().string() : "{}";
            return objectMapper.readValue(body, new TypeReference<>() {});
        }
    }

    public void killContainer(String containerId) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(apiPath("containers/" + containerId + "/kill")))
                .post(RequestBody.create(new byte[0], JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful() && response.code() != 409) {
                throw new IOException("终止容器失败: " + response.code() + " " + response.message());
            }
        }
    }

    public String createExec(String containerId, List<String> cmd) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("AttachStdout", true);
        payload.put("AttachStderr", true);
        payload.put("AttachStdin", false);
        payload.put("Tty", true);
        payload.put("Cmd", cmd);

        String json = objectMapper.writeValueAsString(payload);
        Request request = new Request.Builder()
                .url(buildUrl(apiPath("containers/" + containerId + "/exec")))
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("创建 exec 失败: " + response.code() + " " + response.message());
            }
            String body = response.body() != null ? response.body().string() : "{}";
            Map<String, Object> map = objectMapper.readValue(body, new TypeReference<>() {});
            return (String) map.get("Id");
        }
    }

    public String startExec(String execId, boolean tty) throws IOException {
        Map<String, Object> payload = new HashMap<>();
        payload.put("Detach", false);
        payload.put("Tty", tty);

        String json = objectMapper.writeValueAsString(payload);
        Request request = new Request.Builder()
                .url(buildUrl(apiPath("exec/" + execId + "/start")))
                .post(RequestBody.create(json, JSON))
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("启动 exec 失败: " + response.code() + " " + response.message());
            }
            ResponseBody body = response.body();
            return body != null ? body.string() : "";
        }
    }

    public Map<String, Object> inspectExec(String execId) throws IOException {
        Request request = new Request.Builder()
                .url(buildUrl(apiPath("exec/" + execId + "/json")))
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("检查 exec 状态失败: " + response.code() + " " + response.message());
            }
            String body = response.body() != null ? response.body().string() : "{}";
            return objectMapper.readValue(body, new TypeReference<>() {});
        }
    }

    /**
     * 获取容器的详细内存统计信息
     * @return 包含 usage(总内存), rss(物理内存), cache(缓存), limit(内存限制) 的 Map
     */
    public Map<String, Long> getContainerMemoryStats(String containerId) throws IOException {
        Map<String, Long> result = new HashMap<>();
        result.put("usage", 0L);
        result.put("rss", 0L);
        result.put("cache", 0L);
        result.put("limit", 0L);

        HttpUrl url = Objects.requireNonNull(HttpUrl.parse(buildUrl(apiPath("containers/" + containerId + "/stats"))))
                .newBuilder()
                .addQueryParameter("stream", "false")
                .build();
        Request request = new Request.Builder()
                .url(url)
                .get()
                .build();
        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("获取容器内存统计失败: " + response.code() + " " + response.message());
            }
            String body = response.body() != null ? response.body().string() : "{}";
            Map<String, Object> stats = objectMapper.readValue(body, new TypeReference<>() {});
            Map<String, Object> memory = stats.get("memory_stats") instanceof Map<?, ?> memMap ? (Map<String, Object>) memMap : null;
            if (memory == null) {
                return result;
            }

            // 总内存使用
            Object usage = memory.get("usage");
            if (usage instanceof Number) {
                result.put("usage", ((Number) usage).longValue());
            }

            // 内存限制
            Object limit = memory.get("limit");
            if (limit instanceof Number) {
                result.put("limit", ((Number) limit).longValue());
            }

            // 详细的 memory_stats.stats
            Map<String, Object> detailedStats = memory.get("stats") instanceof Map<?, ?> detailed ? (Map<String, Object>) detailed : null;
            if (detailedStats != null) {
                // RSS (Resident Set Size) - 实际物理内存，更接近程序真实内存占用
                Object rss = detailedStats.get("rss");
                if (rss instanceof Number) {
                    result.put("rss", ((Number) rss).longValue());
                }

                // Cache - 页面缓存
                Object cache = detailedStats.get("cache");
                if (cache instanceof Number) {
                    result.put("cache", ((Number) cache).longValue());
                }
            }
            return result;
        }
    }

    /**
     * 获取容器内存使用量（优先使用 RSS，更准确反映实际程序内存）
     * @deprecated 请使用 getContainerMemoryStats() 获取更详细的内存信息
     */
    @Deprecated
    public long getContainerMemoryUsage(String containerId) throws IOException {
        Map<String, Long> stats = getContainerMemoryStats(containerId);
        // 优先返回 RSS，如果没有则返回 usage
        Long rss = stats.get("rss");
        if (rss != null && rss > 0) {
            return rss;
        }
        return stats.get("usage");
    }

    /**
     * 获取容器内主进程的内存使用（最精确）
     * 通过读取 /proc/1/status 中的 VmRSS 获取主进程（PID 1）的物理内存
     */
    public long getProcessMemoryUsage(String containerId) throws IOException {
        try {
            // 执行 ps 命令查看进程内存
            String[] cmd = {"ps", "aux"};
            String output = execContainer(containerId, cmd);
            if (output == null || output.isEmpty()) {
                // 如果 ps 不可用，尝试读取 /proc
                return getContainerMemoryUsage(containerId);
            }

            // 解析 ps 输出，找最大的进程（通常是用户代码）
            // ps aux 输出格式: USER PID %CPU %MEM VSZ RSS TTY STAT START TIME COMMAND
            String[] lines = output.split("\n");
            long maxRss = 0;
            for (int i = 1; i < lines.length; i++) {  // 跳过标题行
                String line = lines[i].trim();
                if (line.isEmpty()) continue;
                String[] parts = line.split("\\s+");
                if (parts.length >= 6) {
                    try {
                        // RSS 在第6列 (索引5)，单位是 KB
                        long rss = Long.parseLong(parts[5]) * 1024;  // 转换为字节
                        if (rss > maxRss) {
                            maxRss = rss;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            return maxRss > 0 ? maxRss : getContainerMemoryUsage(containerId);
        } catch (Exception e) {
            log.debug("获取进程内存失败，使用容器内存: {}", e.getMessage());
            return getContainerMemoryUsage(containerId);
        }
    }

    /**
     * 在容器内执行命令
     */
    public String execContainer(String containerId, String[] cmd) throws IOException {
        String execId = createExec(containerId, Arrays.asList(cmd));
        return startExec(execId, false);
    }

    @Override
    public void close() {
        httpClient.connectionPool().evictAll();
        if (httpClient.cache() != null) {
            try {
                httpClient.cache().close();
            } catch (IOException e) {
                log.warn("关闭 DockerClient cache 失败: {}", e.getMessage());
            }
        }
    }
}

