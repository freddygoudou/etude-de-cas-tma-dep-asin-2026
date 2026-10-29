package bj.asin.inscriptions.regle;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RegleAgeTest {

    private static final LocalDate EPREUVE_2026 = LocalDate.of(2026, 12, 19);

    @Test
    void inc205_candidat_ne_le_20_juin_1991_a_35_ans_et_est_eligible() {
        assertThat(RegleAge.ageAu31Decembre(LocalDate.of(1991, 6, 20), 2026)).isEqualTo(35);
        assertThat(RegleAge.estEligible(LocalDate.of(1991, 6, 20), EPREUVE_2026)).isTrue();
    }

    @ParameterizedTest(name = "né le {0} -> éligible={1}")
    @CsvSource({
            "1991-01-01, true",   // ancien calcul : 36,01
            "1991-12-31, true",
            "1992-01-01, true",   // ancien calcul : 35,02
            "1992-01-08, true",
            "1990-12-31, false",
            "1986-08-09, false",
            "2004-09-08, true"})
    void age_revolu_au_31_decembre(LocalDate naissance, boolean eligible) {
        assertThat(RegleAge.estEligible(naissance, EPREUVE_2026)).isEqualTo(eligible);
    }
}
