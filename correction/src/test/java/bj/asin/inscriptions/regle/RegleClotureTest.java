package bj.asin.inscriptions.regle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

class RegleClotureTest {

    private static final LocalDateTime CLOTURE_TRESOR_UTC = LocalDateTime.of(2026, 11, 20, 22, 59); // 23h59 au Bénin

    private static Clock serveurAuBenin(String instantUtc) {
        return Clock.fixed(Instant.parse(instantUtc), ZoneId.of("Africa/Porto-Novo"));
    }

    @Test
    void la_cloture_stockee_en_utc_correspond_a_23h59_au_benin() {
        assertThat(RegleCloture.enHeureDuBenin(CLOTURE_TRESOR_UTC)).isEqualTo(LocalDateTime.of(2026, 11, 20, 23, 59));
    }

    @Test
    void inc204_a_23h00_heure_du_benin_les_inscriptions_sont_encore_ouvertes() {
        // 23h00 au Bénin : l'ancien code refusait déjà
        assertThat(RegleCloture.inscriptionsOuvertes(CLOTURE_TRESOR_UTC, serveurAuBenin("2026-11-20T22:00:00Z")))
                .isTrue();
    }

    @Test
    void ouvert_jusqu_a_23h59_inclus_puis_clos() {
        assertThat(RegleCloture.inscriptionsOuvertes(CLOTURE_TRESOR_UTC, serveurAuBenin("2026-11-20T22:59:00Z")))
                .isTrue();
        assertThat(RegleCloture.inscriptionsOuvertes(CLOTURE_TRESOR_UTC, serveurAuBenin("2026-11-20T22:59:01Z")))
                .isFalse();
    }
}
