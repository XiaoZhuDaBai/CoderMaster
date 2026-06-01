package xiaozhu.submission;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@MapperScan("xiaozhu.submission.mapper")
@ComponentScan(basePackages = {"xiaozhu.submission", "xiaozhu.common.metrics"})
@EnableFeignClients(basePackages = "xiaozhu.common.feign")
public class SubmissionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SubmissionServiceApplication.class, args);
    }

}
