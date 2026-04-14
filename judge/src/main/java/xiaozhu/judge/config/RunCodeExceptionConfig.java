package xiaozhu.judge.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * @author XiaoZhuDaBai
 * @version 1.0
 * @date 2025/11/17 16:05
 */
@Component
@ConfigurationProperties(prefix = "error-patterns")
@Validated
@Data
public class RunCodeExceptionConfig {
    @Valid
    private CompileErrors compileErrors = new CompileErrors();

    @Valid
    private RuntimeErrors runtimeErrors = new RuntimeErrors();

    /**
     * 编译错误模式配置
     */
    @Data
    public static class CompileErrors {
        @NotEmpty(message = "Java编译错误模式不能为空")
        private List<String> java;

        @NotEmpty(message = "C++编译错误模式不能为空")
        private List<String> cpp;

        @NotEmpty(message = "C编译错误模式不能为空")
        private List<String> c;

        @NotEmpty(message = "Python编译错误模式不能为空")
        private List<String> python3;

        @NotEmpty(message = "JavaScript编译错误模式不能为空")
        private List<String> javascript;

        @NotEmpty(message = "Go编译错误模式不能为空")
        private List<String> go;

        @NotEmpty(message = "Rust编译错误模式不能为空")
        private List<String> rust;
    }

    /**
     * 运行时错误模式配置
     */
    @Data
    public static class RuntimeErrors {
        @NotEmpty(message = "Java运行时错误模式不能为空")
        private List<String> java;

        @NotEmpty(message = "C++运行时错误模式不能为空")
        private List<String> cpp;

        @NotEmpty(message = "C运行时错误模式不能为空")
        private List<String> c;

        @NotEmpty(message = "Python运行时错误模式不能为空")
        private List<String> python3;

        @NotEmpty(message = "JavaScript运行时错误模式不能为空")
        private List<String> javascript;

        @NotEmpty(message = "Go运行时错误模式不能为空")
        private List<String> go;

        @NotEmpty(message = "Rust运行时错误模式不能为空")
        private List<String> rust;
    }

    /**
     * 根据语言获取编译错误模式
     */
    public List<String> getCompileErrorPatterns(String language) {
        return switch (language.toLowerCase()) {
            case "java" -> compileErrors.getJava();
            case "cpp" -> compileErrors.getCpp();
            case "c" -> compileErrors.getC();
            case "python3" -> compileErrors.getPython3();
            case "javascript" -> compileErrors.getJavascript();
            case "go" -> compileErrors.getGo();
            case "rust" -> compileErrors.getRust();
            default -> List.of("error:", "Exception", "Error");
        };
    }

    /**
     * 根据语言获取运行时错误模式
     */
    public List<String> getRuntimeErrorPatterns(String language) {
        return switch (language.toLowerCase()) {
            case "java" -> runtimeErrors.getJava();
            case "cpp" -> runtimeErrors.getCpp();
            case "c" -> runtimeErrors.getC();
            case "python3" -> runtimeErrors.getPython3();
            case "javascript" -> runtimeErrors.getJavascript();
            case "go" -> runtimeErrors.getGo();
            case "rust" -> runtimeErrors.getRust();
            default -> List.of("Exception", "Error", "at java.", "at sun.");
        };
    }

}
