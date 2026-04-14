package xiaozhu.judge.codesandbox;

import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;
import xiaozhu.judge.config.SandboxPoolConfig;
import xiaozhu.judge.model.ExecuteCodeRequest;
import xiaozhu.judge.model.ExecuteCodeResponse;
import xiaozhu.judge.model.ExecuteMessage;
import xiaozhu.judge.model.LanguageConfigInfo;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/**
 * 配置驱动的代码沙箱
 * 完全基于配置文件支持多语言，无需为每种语言创建单独的沙箱类
 */
@Slf4j
@Component
public class CodeSandboxImpl implements CodeSandbox {

    private final SandboxPoolConfig properties;

    private final CodeSandboxTemplate codeSandboxTemplate;

    public CodeSandboxImpl(SandboxPoolConfig properties, CodeSandboxTemplate codeSandboxTemplate) {
        this.properties = properties;
        this.codeSandboxTemplate = codeSandboxTemplate;
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        String problemId = executeCodeRequest.getProblemId();
        java.util.List<xiaozhu.common.dto.TestCaseDTO> testCases = executeCodeRequest.getTestCases();

        // 调试日志：打印接收到的代码
        log.info("接收到的代码:\n{}", code);

        log.info("开始执行代码，语言: {}, 题目ID: {}, 测试用例数量: {}", 
                language, problemId, testCases != null ? testCases.size() : 0);

        try {
            // 从配置中获取语言配置
            LanguageConfigInfo config = getLanguageConfig(language);
            String codeFileName = "Main." + getFileExtension(language);

            // 保存代码
            File userCodeFile = codeSandboxTemplate.saveCodeToFile(code, codeFileName);

            // 开始编译和执行
            List<ExecuteMessage> executeMessageList;
            try {
                // 如果提供了内存测试用例，使用内存方式执行（不依赖文件系统）
                if (testCases != null && !testCases.isEmpty()) {
                    log.info("使用内存测试用例执行，数量: {}", testCases.size());
                    if (config.needCompile()) {
                        executeMessageList = codeSandboxTemplate.executeWithMemoryTestCases(
                                userCodeFile, testCases, language, config, true, config.compileCmd(), codeFileName);
                    } else {
                        executeMessageList = codeSandboxTemplate.executeWithMemoryTestCases(
                                userCodeFile, testCases, language, config, false, null, codeFileName);
                    }
                } else {
                    // 回退到文件系统方式（兼容旧逻辑）
                    log.info("使用文件系统测试用例执行");
                    if (config.needCompile()) {
                        executeMessageList = codeSandboxTemplate.compileAndRunFileWithPool(
                                userCodeFile, problemId, language, config, codeFileName);
                    } else {
                        executeMessageList = codeSandboxTemplate.runFileWithPool(
                                userCodeFile, problemId, language, config);
                    }
                }
            } catch (Exception e) {
                log.error("执行代码时发生错误: {}", e.getMessage(), e);
                throw new RuntimeException("代码执行失败: " + e.getMessage(), e);
            } finally {
                boolean deletedFile = deleteFile(userCodeFile);
                if (!deletedFile) {
                    log.warn("删除代码文件失败: {}", userCodeFile.getAbsolutePath());
                }
            }

            // 返回运行结果
            return codeSandboxTemplate.getOutputResponse(executeMessageList);

        } catch (Exception e) {
            log.error("代码沙箱执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("代码沙箱执行失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ExecuteCodeResponse userTestCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        String input = executeCodeRequest.getUserInput();
        String language = executeCodeRequest.getLanguage();

        log.info("开始用户测试代码，语言: {}", language);

        try {
            // 从配置中获取语言配置
            LanguageConfigInfo config = getLanguageConfig(language);
            String codeFileName = "Main." + getFileExtension(language);

            // 【关键修复】代码和输入必须保存在同一个目录下，否则容器内路径会不一致
            File userCodeFile = codeSandboxTemplate.saveCodeToFile(code, codeFileName);
            // input.txt 保存到与 code 相同的父目录
            File inputDir = userCodeFile.getParentFile();
            File userInputFile = new File(inputDir, "input.txt");
            Files.writeString(userInputFile.toPath(), input != null ? input : "", StandardCharsets.UTF_8);
//            log.info("userTestCode: userCodeFile={}, userInputFile={}, 同一目录={}, 输入内容='{}'",
//                    userCodeFile.getAbsolutePath(), userInputFile.getAbsolutePath(),
//                    userCodeFile.getParentFile().equals(userInputFile.getParentFile()), input);
            log.info("userTestCode: userCodeFile={}, userInputFile={}, 同一目录={}",
                    userCodeFile.getAbsolutePath(), userInputFile.getAbsolutePath(),
                    userCodeFile.getParentFile().equals(userInputFile.getParentFile()));
            // 开始执行
            List<ExecuteMessage> executeMessageList;
            try {
                if (config.needCompile()) {
                    executeMessageList = codeSandboxTemplate.compileAndRunFileWithInputUsingPool(
                            userCodeFile, userInputFile, language, config, codeFileName);
                } else {
                    executeMessageList = codeSandboxTemplate.runFileWithInputUsingPool(
                            userCodeFile, userInputFile, language, config);
                }
            } catch (Exception e) {
                log.error("用户测试代码时发生错误: {}", e.getMessage(), e);
                throw new RuntimeException("用户测试代码失败: " + e.getMessage(), e);
            } finally {
                // 【DEBUG】删除前打印文件内容
                try {
                    String codeContent = Files.readString(userCodeFile.toPath(), StandardCharsets.UTF_8);
                    String inputContent = Files.readString(userInputFile.toPath(), StandardCharsets.UTF_8);
                    log.warn("[DEBUG] 删除前 - Main.java ({}字符)：\n{}", codeContent.length(), codeContent);
//                    log.warn("[DEBUG] 删除前 - input.txt：{}", inputContent);
                } catch (Exception ex) {
                    log.warn("[DEBUG] 读取文件内容失败: {}", ex.getMessage());
                }
                boolean codeFileDeleted = deleteFile(userCodeFile);
                boolean inputFileDeleted = deleteFile(userInputFile);
                if (!codeFileDeleted || !inputFileDeleted) {
                    log.warn("删除文件失败 - 代码文件: {}, 输入文件: {}",
                            codeFileDeleted ? "成功" : "失败",
                            inputFileDeleted ? "成功" : "失败");
                }
            }

            // 返回运行结果
            return codeSandboxTemplate.getOutputResponse(executeMessageList);

        } catch (Exception e) {
            log.error("用户测试代码沙箱执行失败: {}", e.getMessage(), e);
            throw new RuntimeException("用户测试代码沙箱执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取语言配置
     */
    private LanguageConfigInfo getLanguageConfig(String language) {
        Map<String, LanguageConfigInfo> languageConfigs = properties.getLanguages();
        LanguageConfigInfo config = languageConfigs.get(language);
        if (config == null) {
            throw new RuntimeException("不支持的语言: " + language);
        }
        return config;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String language) {
        return switch (language) {
            case "java" -> "java";
            case "cpp" -> "cpp";
            case "c" -> "c";
            case "python", "python3" -> "py";
            case "javascript", "js" -> "js";
            case "go" -> "go";
            case "rust" -> "rs";
            case "php" -> "php";
            default -> language;
        };
    }

    /**
     * 删除文件
     */
    private boolean deleteFile(File userCodeFile) {
        if (userCodeFile.getParentFile() != null) {
            String userCodeParentPath = userCodeFile.getParentFile().getAbsolutePath();
            final boolean del = cn.hutool.core.io.FileUtil.del(userCodeParentPath);
            log.debug("删除文件: {}", del ? "成功" : "失败");
            return del;
        }
        return true;
    }

}
