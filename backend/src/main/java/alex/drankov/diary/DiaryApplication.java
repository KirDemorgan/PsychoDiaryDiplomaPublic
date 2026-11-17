package alex.drankov.diary;

import org.springframework.boot.SpringApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@EnableScheduling
@SpringBootApplication
@ConfigurationPropertiesScan
public class DiaryApplication {
    public static void main(String[] args) {
        SpringApplication.run(DiaryApplication.class, args);
    }
}
