package xiaozhu.common.config;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * 环境变量加载配置
 * 自动加载项目根目录的 .env 文件
 */
@Configuration
public class DotEnvConfig {

    @PostConstruct
    public void init() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();

        dotenv.entries().forEach(entry ->
                System.setProperty(entry.key(), entry.value())
        );
    }
}
