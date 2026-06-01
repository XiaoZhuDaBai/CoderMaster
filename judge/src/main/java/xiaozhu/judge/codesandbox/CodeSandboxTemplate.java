package xiaozhu.judge.codesandbox;

import jakarta.annotation.PostConstruct;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;
import xiaozhu.judge.config.RunCodeExceptionConfig;
import xiaozhu.judge.config.SandboxPoolConfig;
import xiaozhu.judge.model.ExecuteCodeResponse;
import xiaozhu.judge.model.ExecuteMessage;
import xiaozhu.judge.model.JudgeInfo;
import xiaozhu.judge.model.LanguageConfigInfo;
import xiaozhu.judge.pool.MultiLanguageDockerSandBoxPool;
import xiaozhu.judge.pool.exception.ContainerPoolException;
import xiaozhu.judge.pool.model.ContainerInfo;
import xiaozhu.judge.util.DockerClient;
import xiaozhu.common.dto.TestCaseDTO;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 统一的代码执行模板，负责：
 * 1. 管理代码落盘和映射路径
 * 2. 调度 Docker 容器或容器池执行命令
 * 3. 统计运行时间、内存、输出等信息
 * 4. 聚合执行结果给上层 AI 判题服务使用
 */
@Component
public class CodeSandboxTemplate {

    private static final Logger log = LoggerFactory.getLogger(CodeSandboxTemplate.class);
    private static final long MAX_RUN_TIME_MS = 10_000L;
    private static final String GLOBAL_CODE_DIR_NAME = "UserCode";

    private final MultiLanguageDockerSandBoxPool containerPool;
    private final DockerClient dockerClient;
    private final Path hostBaseDir;
    private final Path globalCodeDir;
    private final RunCodeExceptionConfig errorConfig;
    private final MeterRegistry meterRegistry;
    
    // 缓存 Timer 实例，避免每次都创建新实例
    private final Map<String, Timer> executionTimers = new ConcurrentHashMap<>();

    public CodeSandboxTemplate(MultiLanguageDockerSandBoxPool containerPool,
                               DockerClient dockerClient,
                               SandboxPoolConfig sandboxConfig,
                               RunCodeExceptionConfig errorConfig,
                               MeterRegistry meterRegistry) {
        this.containerPool = containerPool;
        this.dockerClient = dockerClient;
        this.hostBaseDir = Paths.get(sandboxConfig.getHostCodeBaseDir()).toAbsolutePath().normalize();
        this.globalCodeDir = this.hostBaseDir.resolve(GLOBAL_CODE_DIR_NAME);
        this.errorConfig = errorConfig;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initWorkspace() {
        try {
            Files.createDirectories(globalCodeDir);
            log.info("代码沙箱工作目录: {}", globalCodeDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建代码沙箱工作目录: " + globalCodeDir, e);
        }
    }

    /**
     * 将用户代码或输入保存为临时文件（位于宿主机挂载目录中）
     */
    public File saveCodeToFile(String content, String fileName) {
        try {
            Files.createDirectories(globalCodeDir);
            Path userDir = globalCodeDir.resolve(UUID.randomUUID().toString());
            Files.createDirectories(userDir);
            Path filePath = userDir.resolve(fileName);
            Files.writeString(filePath, content == null ? "" : content, StandardCharsets.UTF_8);
            return filePath.toFile();
        } catch (IOException e) {
            throw new RuntimeException("保存代码文件失败", e);
        }
    }

    /* -------------------- 对外暴露的执行入口 -------------------- */

    public List<ExecuteMessage> runFileWithPool(File userCodeFile, String problemId, String language, LanguageConfigInfo config) {
        return executeWithTestCases(userCodeFile, problemId, config, language, false, null, null);
    }

    public List<ExecuteMessage> compileAndRunFileWithPool(File userCodeFile,
                                                          String problemId,
                                                          String language,
                                                          LanguageConfigInfo config,
                                                          String codeFileName) {
        return executeWithTestCases(userCodeFile, problemId, config, language, true, config.compileCmd(), codeFileName);
    }

    /**
     * 使用内存中的测试用例执行代码（不依赖文件系统）
     * @param userCodeFile 用户代码文件
     * @param testCases 测试用例列表
     * @param language 语言
     * @param config 语言配置
     * @param needCompile 是否需要编译
     * @param compileCmd 编译命令
     * @param codeFileName 代码文件名
     * @return 执行结果列表
     */
    public List<ExecuteMessage> executeWithMemoryTestCases(File userCodeFile,
                                                           List<TestCaseDTO> testCases,
                                                           String language,
                                                           LanguageConfigInfo config,
                                                           boolean needCompile,
                                                           String compileCmd,
                                                           String codeFileName) {
        List<ExecuteMessage> results = new ArrayList<>();
        
        if (testCases == null || testCases.isEmpty()) {
            // 没有测试用例，执行一次空输入
            ExecuteMessage message = runSingleCaseWithStdin(userCodeFile, null, config, language, needCompile, compileCmd, codeFileName);
            results.add(message);
            return results;
        }
        
        for (TestCaseDTO testCase : testCases) {
            String input = testCase.getInput() != null ? testCase.getInput() : "";
            String expectedOutput = testCase.getExpectedOutput();
            
            ExecuteMessage message = runSingleCaseWithStdin(userCodeFile, input, config, language, needCompile, compileCmd, codeFileName);
            message.setCorrect(check(message.getMessage(), expectedOutput));
            results.add(message);
        }
        
        return results;
    }

    public List<ExecuteMessage> runFileWithInputUsingPool(File userCodeFile,
                                                          File userInputFile,
                                                          String language,
                                                          LanguageConfigInfo config) {
        return executeWithUserInput(userCodeFile, userInputFile, config, language, false, null, null);
    }

    public List<ExecuteMessage> compileAndRunFileWithInputUsingPool(File userCodeFile,
                                                                    File userInputFile,
                                                                    String language,
                                                                    LanguageConfigInfo config,
                                                                    String codeFileName) {
        return executeWithUserInput(userCodeFile, userInputFile, config, language, true, config.compileCmd(), codeFileName);
    }

    /* -------------------- 结果聚合 -------------------- */

    public ExecuteCodeResponse getOutputResponse(List<ExecuteMessage> executeMessageList) {
        ExecuteCodeResponse response = new ExecuteCodeResponse();
        JudgeInfo judgeInfo = new JudgeInfo();

        List<String> outputs = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        long maxTime = 0;
        long maxMemory = 0;

        boolean[] correctFlags = new boolean[executeMessageList.size()];
        for (int i = 0; i < executeMessageList.size(); i++) {
            ExecuteMessage message = executeMessageList.get(i);
            correctFlags[i] = message.isCorrect();
            maxTime = Math.max(maxTime, message.getTime() == null ? 0 : message.getTime());
            maxMemory = Math.max(maxMemory, message.getMemory() == null ? 0 : message.getMemory());

            if (message.getExitValue() != null && message.getExitValue() != 0) {
                errors.add(message.getErrorMessage());
                response.setExitCode(message.getExitValue());
                break;
            }

            outputs.add(message.getMessage());
        }

        judgeInfo.setCorrect(correctFlags);
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);
        judgeInfo.setErrorMessages(errors);
        response.setJudgeInfo(judgeInfo);
        response.setOutputList(outputs);
        return response;
    }

    /* -------------------- 核心执行流程 -------------------- */

    private List<ExecuteMessage> executeWithTestCases(File userCodeFile,
                                                      String problemId,
                                                      LanguageConfigInfo config,
                                                      String language,
                                                      boolean needCompile,
                                                      String compileCmd,
                                                      String codeFileName) {
        List<ExecuteMessage> results = new ArrayList<>();
        Path inputDir = hostBaseDir.resolve(Paths.get("Problems", problemId == null ? "" : problemId, "input"));
        Path answerDir = hostBaseDir.resolve(Paths.get("Problems", problemId == null ? "" : problemId, "answer"));
        long caseCount = getDirChildFileCount(inputDir);

        if (caseCount == 0) {
            ExecuteMessage message = runSingleCase(userCodeFile, null, config, language, needCompile, compileCmd, codeFileName);
            results.add(message);
            return results;
        }

        for (int i = 1; i <= caseCount; i++) {
            Path inputFile = inputDir.resolve("input" + i + ".txt");
            Path answerFile = answerDir.resolve("answer" + i + ".txt");

            ExecuteMessage message = runSingleCase(userCodeFile, inputFile, config, language, needCompile, compileCmd, codeFileName);
            message.setCorrect(check(message.getMessage(), answerFile));
            results.add(message);
        }

        return results;
    }

    private List<ExecuteMessage> executeWithUserInput(File userCodeFile,
                                                      File userInputFile,
                                                      LanguageConfigInfo config,
                                                      String language,
                                                      boolean needCompile,
                                                      String compileCmd,
                                                      String codeFileName) {
        Path inputPath = userInputFile != null ? userInputFile.toPath() : null;
        ExecuteMessage message = runSingleCase(userCodeFile, inputPath, config, language, needCompile, compileCmd, codeFileName);
        return Collections.singletonList(message);
    }

    private ExecuteMessage runSingleCase(File userCodeFile,
                                         Path inputPath,
                                         LanguageConfigInfo config,
                                         String language,
                                         boolean needCompile,
                                         String compileCmd,
                                         String codeFileName) {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("使用容器池执行代码时必须提供语言标识");
        }
        String codeDirInContainer = resolveContainerPath(userCodeFile.getParentFile().toPath(), config);
        String inputInContainer = inputPath != null ? resolveContainerPath(inputPath, config) : "";
        String codePathInContainer = normalizeContainerPath(Paths.get(codeDirInContainer, codeFileName));

        String runSegment = buildRunCommand(config.runCmd(), codeDirInContainer, inputInContainer);
        String compileSegment = needCompile && compileCmd != null && !compileCmd.isBlank()
                ? String.format(compileCmd, codePathInContainer)
                : null;

        // 【关键修复】当需要从文件读取输入时，runSegment 需要用括号包裹
        // 原因：javac ... && java ... < input.txt 中，< input.txt 会被 shell 先解析，
        // stdin 会同时被 javac 读到（javac 把 "42" 当源文件编译就失败了）。
        // 改为：javac ... && (java ... < input.txt) 让 stdin 只流向 java
        log.info("runSingleCase: needCompile={}, inputPath={}, inputInContainer='{}', inputInContainer.isBlank={}",
                needCompile, inputPath, inputInContainer, inputInContainer.isBlank());
        if (inputInContainer != null && !inputInContainer.isBlank()) {
            log.info("runSingleCase: 检测到文件输入，输入将用括号隔离，修复 stdin 被 javac 读到的问题");
            runSegment = "(" + runSegment + ")";
        }

        String finalCmd = compileSegment != null ? compileSegment + " && " + runSegment : runSegment;
        log.info("runSingleCase: codeDirInContainer={}, codeFileName={}, compileSegment={}, runSegment={}, finalCmd={}",
                codeDirInContainer, codeFileName, compileSegment, runSegment, finalCmd);
        return executeContainerUsingPool(language, finalCmd, needCompile);
    }

    /**
     * 使用stdin输入执行单个测试用例（不依赖文件）
     * 通过echo命令将输入通过管道传入程序
     */
    private ExecuteMessage runSingleCaseWithStdin(File userCodeFile,
                                                  String stdinInput,
                                                  LanguageConfigInfo config,
                                                  String language,
                                                  boolean needCompile,
                                                  String compileCmd,
                                                  String codeFileName) {
        if (language == null || language.isBlank()) {
            throw new IllegalArgumentException("使用容器池执行代码时必须提供语言标识");
        }
        String codeDirInContainer = resolveContainerPath(userCodeFile.getParentFile().toPath(), config);
        String codePathInContainer = normalizeContainerPath(Paths.get(codeDirInContainer, codeFileName));
        String runCmdTemplate = config.runCmd();
        
        // 构建运行命令：使用printf将输入通过管道传入
        // 原命令格式可能是: 
        //   - "python3 %s < %s" (两个%s: 代码路径, 输入文件)
        //   - "./Main < %s" (一个%s: 输入文件)
        //   - "java -cp %s Main < %s" (两个%s: 代码路径, 输入文件)
        // 我们需要改为: printf "input" | python3 %s (移除 < %s 部分)
        String runCmd;
        if (runCmdTemplate.contains("%s")) {
            // 计算%s的数量（直接计算%s而不是%的数量）
            int placeholderCount = runCmdTemplate.split("%s", -1).length - 1;
            
            // 检查是否包含输入重定向（可能有空格）
            boolean hasInputRedirect = runCmdTemplate.contains("< %s") || runCmdTemplate.contains("<%s");
            
            // 调试日志
            log.info("runCmdTemplate: {}, placeholderCount: {}, hasInputRedirect: {}", runCmdTemplate, placeholderCount, hasInputRedirect);
            
            if (placeholderCount == 2 && hasInputRedirect) {
                // 两个占位符，且第二个是输入文件: "xxx %s xxx < %s"
                // 替换第一个%s为代码路径，移除 " < %s" 部分
                // 注意：Java的-cp需要的是目录路径，不是文件路径
                int firstPlaceholder = runCmdTemplate.indexOf("%s");
                int secondPlaceholder = runCmdTemplate.indexOf("%s", firstPlaceholder + 2);
                String beforeFirst = runCmdTemplate.substring(0, firstPlaceholder);
                // 移除输入重定向部分（包括可能的空格）
                String between = runCmdTemplate.substring(firstPlaceholder + 2, secondPlaceholder);
                // 清理 between 中的 < 和空格
                between = between.replaceAll("\\s*<\\s*", "").trim();
                runCmd = beforeFirst + codeDirInContainer + " " + between;
                log.info("Generated runCmd (2 placeholders): {}", runCmd);
            } else if (placeholderCount == 1 && hasInputRedirect) {
                // 一个占位符，且是输入文件: "./Main < %s"
                // 直接移除 " < %s" 部分
                runCmd = runCmdTemplate.replaceAll("\\s*<\\s*%s", "").trim();
            } else {
                // 其他情况：替换第一个%s为代码路径（使用目录路径）
                runCmd = runCmdTemplate.replaceFirst("%s", codeDirInContainer);
                // 如果还有第二个%s（输入文件），移除它及其前面的部分
                if (runCmd.contains("%s")) {
                    int idx = runCmd.indexOf("%s");
                    String before = runCmd.substring(0, idx);
                    // 移除 " < %s" 或类似的部分
                    if (before.trim().endsWith("<")) {
                        runCmd = before.substring(0, before.lastIndexOf("<")).trim();
                    } else {
                        runCmd = before;
                    }
                }
            }
        } else {
            runCmd = runCmdTemplate.replace("{code}", codePathInContainer);
        }
        
        // 如果有输入，通过管道传入
        String finalRunCmd;
        if (stdinInput != null && !stdinInput.trim().isEmpty()) {
            // 使用 printf 代替 echo，避免 echo 自带的换行和转义问题
            // printf %s 不追加换行，%b 解释转义字符
            String escaped = stdinInput
                    .replace("\\", "\\\\")
                    .replace("'", "'\\''")
                    .replace("\n", "\\n")
                    .replace("\r", "\\r");
            if (escaped.contains("\\n") || escaped.contains("\\r")) {
                // 多行输入：printf %b 解释转义字符（\n \r），-v VAR 通过变量传入避免引号问题
                finalRunCmd = String.format("printf %%b '%s' | %s", escaped, runCmd);
            } else {
                // 单行输入：直接 echo
                finalRunCmd = String.format("printf '%%s\\n' '%s' | %s", escaped, runCmd);
            }
        } else {
            finalRunCmd = runCmd;
        }
        
        String compileSegment = needCompile && compileCmd != null && !compileCmd.isBlank()
                ? String.format(compileCmd, codePathInContainer)
                : null;

        String finalCmd = compileSegment != null ? compileSegment + " && " + finalRunCmd : finalRunCmd;
        
        // 调试日志：输出完整命令
        log.info("最终执行命令: {}", finalCmd);
        
        return executeContainerUsingPool(language, finalCmd, needCompile, null);
    }

    private ExecuteMessage executeContainerUsingPool(String language, String command, boolean needCompile) {
        return executeContainerUsingPool(language, command, needCompile, null);
    }

    /**
     * 使用容器池执行命令
     * @param stdinInput 预留参数，当前通过命令管道传入，不使用此参数
     */
    private ExecuteMessage executeContainerUsingPool(String language, String command, boolean needCompile, String stdinInput) {
        ExecuteMessage message = new ExecuteMessage();
        ContainerInfo containerInfo = null;
        String containerId = null;

        CompletableFuture<String> outputFuture = null;
        try {
            containerInfo = containerPool.getContainer(language, MAX_RUN_TIME_MS);
            containerId = containerInfo.getContainerId();
            long baseline = safeMemoryUsage(containerId);

            // 当前通过命令管道传入输入，不需要启用stdin
            String execId = dockerClient.createExec(containerId, List.of("sh", "-c", command));
            StopWatch sw = new StopWatch();
            sw.start();

            outputFuture = CompletableFuture.supplyAsync(() -> {
                try {
                    return dockerClient.startExec(execId, true);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });

            String output = outputFuture.get(MAX_RUN_TIME_MS, TimeUnit.MILLISECONDS);
            sw.stop();

            Map<String, Object> execState = dockerClient.inspectExec(execId);
            long exitCode = extractExitCode(execState.get("ExitCode"));
            message.setExitValue(exitCode);
            message.setTime(sw.getTotalTimeMillis());
            applyOutput(message, output, language, needCompile);

            // 获取进程内存使用（更精确，只计算用户代码的内存）
            long finalMemory = getProcessMemoryUsage(containerId);
            message.setMemory(Math.max(0, finalMemory - baseline));

            // 记录执行时间到 Prometheus
            recordExecutionTime(language, needCompile, message.getTime());
        } catch (TimeoutException e) {
            message.setExitValue(-1L);
            message.setErrorMessage("执行超时");
            message.setTime(MAX_RUN_TIME_MS);
            outputFuture.cancel(true);
            if (containerId != null) {
                try {
                    dockerClient.stopContainer(containerId, 2);
                } catch (IOException ignore) {
                    log.debug("停止容器失败: {}", ignore.getMessage());
                }
            }
            containerInfo.markUnhealthy();
        } catch (ContainerPoolException e) {
            message.setExitValue(-1L);
            message.setErrorMessage("容器池不可用: " + e.getMessage());
        } catch (ExecutionException ee) {
            message.setExitValue(-1L);
            message.setErrorMessage("执行失败: " + ee.getCause().getMessage());
            containerInfo.markUnhealthy();
        } catch (Exception e) {
            message.setExitValue(-1L);
            message.setErrorMessage("容器执行异常: " + e.getMessage());
            if (containerInfo != null) {
                containerInfo.markUnhealthy();
            }
        } finally {
            if (containerInfo != null) {
                containerPool.returnContainer(containerInfo);
            }
        }

        // 调试日志：输出执行结果
//        log.info("执行完成 - exitValue: {}, time: {}ms, memory: {}KB, output: {}, error: {}",
//                message.getExitValue(), message.getTime(), message.getMemory(),
//                message.getMessage(), message.getErrorMessage());
        log.info("执行完成 - exitValue: {}, time: {}ms, memory: {}KB, error: {}",
                message.getExitValue(), message.getTime(), message.getMemory(), message.getErrorMessage());

        return message;
    }

    /* -------------------- 工具方法 -------------------- */

    private String resolveContainerPath(Path hostPath, LanguageConfigInfo config) {
        Path normalized = hostPath.toAbsolutePath().normalize();
        if (!normalized.startsWith(hostBaseDir)) {
            return normalizeContainerPath(Paths.get(config.volumeDir()).resolve(normalized.getFileName()));
        }
        Path relative = hostBaseDir.relativize(normalized);
        Path containerPath = Paths.get(config.volumeDir()).resolve(relative);
        return normalizeContainerPath(containerPath);
    }

    private String normalizeContainerPath(Path path) {
        return path.toString().replace("\\", "/");
    }

    private String buildRunCommand(String runTemplate, String codePath, String inputPath) {
        if (runTemplate == null || runTemplate.isBlank()) {
            throw new IllegalArgumentException("未配置运行命令");
        }
        String input = inputPath == null ? "" : inputPath;

        // 对于 Java，-cp 参数需要目录路径而非 .java 文件路径
        // 编译时 javac 接收 Main.java（文件），运行 java -cp 需要目录（包含 .class）
        String adjustedCodePath = codePath;
        if (runTemplate.contains("-cp %s") && codePath != null && codePath.endsWith(".java")) {
            adjustedCodePath = Paths.get(codePath).getParent().toString();
            log.warn("buildRunCommand: Java classpath 调整为目录: {} -> {}", codePath, adjustedCodePath);
        }

        return String.format(runTemplate, adjustedCodePath, input);
    }

    /**
     * 获取容器内进程内存使用（优先使用进程内存，更准确反映实际程序内存占用）
     * 如果无法获取进程内存，则回退到容器 RSS
     */
    private long getProcessMemoryUsage(String containerId) {
        try {
            // 优先尝试获取进程级内存（最精确）
            return dockerClient.getProcessMemoryUsage(containerId);
        } catch (IOException e) {
            log.debug("获取进程内存失败，尝试容器内存: {}", e.getMessage());
            try {
                // 回退到使用 RSS（比 total usage 更准确）
                Map<String, Long> stats = dockerClient.getContainerMemoryStats(containerId);
                Long rss = stats.get("rss");
                if (rss != null && rss > 0) {
                    return rss;
                }
                return stats.get("usage");
            } catch (IOException ex) {
                log.debug("获取容器内存失败: {}", ex.getMessage());
                return 0L;
            }
        }
    }

    /**
     * @deprecated 请使用 getProcessMemoryUsage()
     */
    @Deprecated
    private long safeMemoryUsage(String containerId) {
        return getProcessMemoryUsage(containerId);
    }

    /**
     * 处理执行输出，区分编译错误和运行时错误
     */
    private void applyOutput(ExecuteMessage message, String rawOutput, String language, boolean needCompile) {
        String sanitized = rawOutput == null ? "" : rawOutput.replace("\r", "").trim();
        
        if (message.getExitValue() != null && message.getExitValue() == 0) {
            // 执行成功
            message.setMessage(sanitized);
            message.setErrorMessage(null);
        } else {
            // 执行失败，需要判断是编译错误还是运行时错误
            String errorType = detectErrorType(sanitized, language, needCompile);
            String errorMessage = sanitized.isEmpty() ? "执行失败" : sanitized;
            
            // 在错误消息前添加错误类型标识
            if (!sanitized.isEmpty()) {
                errorMessage = String.format("[%s] %s", errorType, errorMessage);
            } else {
                errorMessage = errorType;
            }
            
            message.setErrorMessage(errorMessage);
            message.setMessage(null);
        }
    }

    /**
     * 检测错误类型：编译错误或运行时错误
     */
    private String detectErrorType(String errorOutput, String language, boolean needCompile) {
        if (errorOutput == null || errorOutput.trim().isEmpty()) {
            return "未知错误";
        }

        String lowerOutput = errorOutput.toLowerCase();
        
        // 如果进行了编译，优先检查编译错误模式
        if (needCompile) {
            List<String> compilePatterns = errorConfig.getCompileErrorPatterns(language);
            for (String pattern : compilePatterns) {
                String lowerPattern = pattern.toLowerCase();
                // 先尝试简单的字符串匹配
                if (lowerOutput.contains(lowerPattern)) {
                    return "编译错误";
                }
                // 如果模式包含正则表达式特殊字符，尝试正则匹配
                if (pattern.contains(".*") || pattern.contains("^") || pattern.contains("$")) {
                    try {
                        if (errorOutput.matches("(?i).*" + pattern + ".*")) {
                            return "编译错误";
                        }
                    } catch (Exception e) {
                        // 正则表达式无效，忽略
                        log.debug("编译错误模式正则表达式无效: {}", pattern);
                    }
                }
            }
        }

        // 检查运行时错误模式
        List<String> runtimePatterns = errorConfig.getRuntimeErrorPatterns(language);
        for (String pattern : runtimePatterns) {
            String lowerPattern = pattern.toLowerCase();
            // 先尝试简单的字符串匹配
            if (lowerOutput.contains(lowerPattern)) {
                return "运行时错误";
            }
            // 如果模式包含正则表达式特殊字符，尝试正则匹配
            if (pattern.contains(".*") || pattern.contains("^") || pattern.contains("$")) {
                try {
                    if (errorOutput.matches("(?i).*" + pattern + ".*")) {
                        return "运行时错误";
                    }
                } catch (Exception e) {
                    // 正则表达式无效，忽略
                    log.debug("运行时错误模式正则表达式无效: {}", pattern);
                }
            }
        }

        // 如果进行了编译但没匹配到任何模式，默认认为是编译错误
        if (needCompile) {
            return "编译错误";
        }

        // 默认返回运行时错误
        return "运行时错误";
    }

    private long extractExitCodeFromState(Object stateObj) {
        if (stateObj instanceof java.util.Map<?, ?> map) {
            Object exitCode = map.get("ExitCode");
            return extractExitCode(exitCode);
        }
        return 0L;
    }

    private long extractExitCode(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        return 0L;
    }

    public boolean check(String outputStr, Path answerFilePath) {
        if (answerFilePath == null || !Files.exists(answerFilePath)) {
            // 没有答案文件时默认通过
            return true;
        }
        if (outputStr == null || outputStr.trim().isEmpty()) {
            return false;
        }

        try {
            List<String> output = List.of(outputStr.split("\\R"));
            List<String> answer = Files.readAllLines(answerFilePath, StandardCharsets.UTF_8);
            if (output.size() != answer.size()) {
                return false;
            }
            for (int i = 0; i < output.size(); i++) {
                if (!output.get(i).trim().equals(answer.get(i).trim())) {
                    return false;
                }
            }
            return true;
        } catch (IOException e) {
            log.warn("读取答案文件失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 检查输出是否与期望输出匹配（内存版本，不依赖文件）
     */
    public boolean check(String outputStr, String expectedOutput) {
        if (expectedOutput == null || expectedOutput.trim().isEmpty()) {
            // 没有期望输出时默认通过
            return true;
        }
        if (outputStr == null || outputStr.trim().isEmpty()) {
            return false;
        }

        // 按行比较
        String[] outputLines = outputStr.split("\\R");
        String[] expectedLines = expectedOutput.split("\\R");
        
        if (outputLines.length != expectedLines.length) {
            return false;
        }
        
        for (int i = 0; i < outputLines.length; i++) {
            if (!outputLines[i].trim().equals(expectedLines[i].trim())) {
                return false;
            }
        }
        
        return true;
    }

    public long getDirChildFileCount(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return 0;
        }
        try (var files = Files.walk(dir)) {
            return files.filter(Files::isRegularFile).count();
        } catch (IOException e) {
            log.warn("统计用例文件失败: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * 记录代码执行时间到 Prometheus，用于监控执行延迟
     */
    private void recordExecutionTime(String language, boolean needCompile, long durationMs) {
        if (meterRegistry == null) {
            log.warn("MeterRegistry 为空，无法记录执行时间指标");
            return;
        }
        
        String timerKey = language + "_" + needCompile;
        Timer timer = executionTimers.computeIfAbsent(timerKey, key -> 
            Timer.builder("judge.sandbox.execution.duration")
                    .description("代码沙箱执行耗时")
                    .tag("language", language)
                    .tag("compile", String.valueOf(needCompile))
                    .publishPercentileHistogram(true)
                    .register(meterRegistry)
        );
        
        timer.record(durationMs, TimeUnit.MILLISECONDS);
        log.debug("已记录执行时间指标: language={}, compile={}, duration={}ms", language, needCompile, durationMs);
    }
}
