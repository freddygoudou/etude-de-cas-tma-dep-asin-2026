package bj.asin.inscriptions.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

// delai : 5 s max (règle métier)
@ConfigurationProperties(prefix = "identite")
public record IdentiteProperties(String url, Duration delai) {

    public static final Duration DELAI_MAX = Duration.ofSeconds(5);

    public IdentiteProperties {
        if (delai == null) {
            delai = DELAI_MAX;
        }
        if (delai.compareTo(DELAI_MAX) > 0) {
            throw new IllegalArgumentException("identite.delai ne peut pas dépasser 5 s (reçu " + delai + ")");
        }
    }
}
