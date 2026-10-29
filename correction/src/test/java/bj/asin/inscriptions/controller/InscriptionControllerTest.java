package bj.asin.inscriptions.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import bj.asin.inscriptions.exception.InscriptionRefuseeException;

class InscriptionControllerTest extends InscriptionApiTestBase {

    @Test
    void inscription_nominale() throws Exception {
        Reponse r = poster(demande("1612538412", "DOUANES", "P"));

        assertThat(r.statut()).isEqualTo(201);
        assertThat(r.corps()).contains("\"numeroTable\":\"P-0001\"", "\"statut\":\"validee\"");
    }

    @Test
    void inc206_service_d_identification_lent_inscription_en_attente_en_5_s_au_plus() throws Exception {
        IDENTITE.latence(8_000);

        long debut = System.nanoTime();
        Reponse r = poster(demande("8649722949", "DOUANES", "C"));
        Duration duree = Duration.ofNanos(System.nanoTime() - debut);

        assertThat(r.statut()).isEqualTo(201);
        assertThat(r.corps()).contains("\"statut\":\"en_attente_verification\"");
        assertThat(duree).isLessThan(Duration.ofMillis(5_800));
    }

    @Test
    void npi_inconnu_du_service_refuse() throws Exception {
        IDENTITE.declarerInconnu("9999999999");

        Reponse r = poster(demande("9999999999", "DOUANES", "C"));

        assertThat(r.statut()).isEqualTo(409);
        assertThat(r.corps()).contains(InscriptionRefuseeException.NPI_INCONNU);
    }

    @Test
    void inc205_candidat_de_35_ans_accepte() throws Exception {
        // Né le 20/06/1991 : 35 ans au 31/12/2026 (cas du signalement).
        Reponse r = poster("""
                {"npi":"9269739419","dateNaissance":"1991-06-20","concours":"DOUANES","centre":"C"}""");
        assertThat(r.statut()).isEqualTo(201);

        Reponse refus = poster("""
                {"npi":"9269739420","dateNaissance":"1990-12-31","concours":"DOUANES","centre":"C"}""");
        assertThat(refus.statut()).isEqualTo(409);
        assertThat(refus.corps()).contains("Âge limite");
    }

    @Test
    void concours_clos_refuse() throws Exception {
        Reponse r = poster(demande("1612538412", "TRESOR", "C"));

        assertThat(r.statut()).isEqualTo(409);
        assertThat(r.corps()).contains(InscriptionRefuseeException.CLOSES);
    }

    @Test
    void inc208_deux_concours_le_meme_jour_refuse_comportement_conforme() throws Exception {
        assertThat(poster(demande("1612538412", "DOUANES", "C")).statut()).isEqualTo(201);

        Reponse r = poster(demande("1612538412", "IMPOTS", "C"));

        assertThat(r.statut()).isEqualTo(409);
        assertThat(r.corps()).contains(InscriptionRefuseeException.MEME_JOUR);
    }

    @Test
    void npi_mal_forme_refuse_400() throws Exception {
        Reponse r = poster(demande("12 345", "DOUANES", "C"));

        assertThat(r.statut()).isEqualTo(400);
    }
}
