package bj.asin.inscriptions.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HorlogeConfig {

    // UTC, pas l'heure locale du serveur (INC-204)
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
