package bj.asin.inscriptions.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import bj.asin.inscriptions.config.IdentiteProperties;
import bj.asin.inscriptions.dto.ResultatIdentite.Verdict;
import bj.asin.inscriptions.support.ServiceIdentificationSimule;

class IdentificationServiceImplTest {

    private static final ServiceIdentificationSimule IDENTITE = new ServiceIdentificationSimule();

    private final IdentificationServiceImpl service = new IdentificationServiceImpl(
            new IdentiteProperties(IDENTITE.url(), Duration.ofSeconds(5)), new ObjectMapper());

    @BeforeEach
    void reinitialiser() {
        IDENTITE.reinitialiser();
    }

    @Test
    void npi_connu() {
        assertThat(service.verifier("1612538412").verdict()).isEqualTo(Verdict.CONNU);
    }

    @Test
    void npi_inconnu() {
        IDENTITE.declarerInconnu("9999999999");

        assertThat(service.verifier("9999999999").verdict()).isEqualTo(Verdict.INCONNU);
    }

    @Test
    void service_lent_en_attente_en_5_s_au_plus() {
        IDENTITE.latence(8_000);

        long debut = System.nanoTime();
        Verdict verdict = service.verifier("8649722949").verdict();

        assertThat(verdict).isEqualTo(Verdict.EN_ATTENTE);
        assertThat(Duration.ofNanos(System.nanoTime() - debut)).isLessThan(Duration.ofMillis(5_500));
    }

    @Test
    void delai_superieur_a_5_s_refuse_en_configuration() {
        assertThatThrownBy(() -> new IdentiteProperties("http://localhost", Duration.ofSeconds(6)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
