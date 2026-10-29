package bj.asin.inscriptions;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class InscriptionsApplication {

    public static void main(String[] args) {
        SpringApplication.run(InscriptionsApplication.class, args);
    }
}
