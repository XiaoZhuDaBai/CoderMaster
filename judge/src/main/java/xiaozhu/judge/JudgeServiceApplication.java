package xiaozhu.judge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableFeignClients(basePackages = {"xiaozhu.common.feign"})
@ComponentScan(basePackages = {"xiaozhu.judge", "xiaozhu.common.metrics"})
public class JudgeServiceApplication {

        public static void main(String[] args) {
            SpringApplication.run(JudgeServiceApplication.class, args);
    }

}
