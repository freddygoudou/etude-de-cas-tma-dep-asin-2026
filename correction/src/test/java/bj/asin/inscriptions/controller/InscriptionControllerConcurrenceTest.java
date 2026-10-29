package bj.asin.inscriptions.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import bj.asin.inscriptions.exception.InscriptionRefuseeException;

// Scénario du 19/11 : requêtes simultanées avec un service d'identification lent (INC-201/202/203)
class InscriptionControllerConcurrenceTest extends InscriptionApiTestBase {

    private static final int CAPACITE_L = 10;

    @Test
    void quarante_inscriptions_simultanees_sur_un_centre_de_dix_places() throws Exception {
        IDENTITE.latence(300);
        List<String> demandes = IntStream.range(0, 40)
                .mapToObj(i -> demande("10000000%02d".formatted(i), "IMPOTS", "L"))
                .toList();

        List<Reponse> reponses = posterSimultanement(demandes);

        assertThat(reponses).filteredOn(r -> r.statut() == 201).hasSize(CAPACITE_L);
        assertThat(reponses).filteredOn(r -> r.statut() == 409)
                .hasSize(30)
                .allSatisfy(r -> assertThat(r.corps()).contains(InscriptionRefuseeException.COMPLET));
        verifierInvariants();
        assertThat(jdbc.queryForList("SELECT numero_table FROM inscriptions ORDER BY numero_table", String.class))
                .containsExactlyElementsOf(IntStream.rangeClosed(1, CAPACITE_L).mapToObj("L-%04d"::formatted).toList());
    }

    @Test
    void double_clic_dix_requetes_identiques_simultanees_une_seule_inscription() throws Exception {
        IDENTITE.latence(300);
        List<Reponse> reponses = posterSimultanement(Collections.nCopies(10, demande("2256599771", "DOUANES", "C")));

        assertThat(reponses).filteredOn(r -> r.statut() == 201).hasSize(1);
        assertThat(reponses).filteredOn(r -> r.statut() == 409)
                .hasSize(9)
                .allSatisfy(r -> assertThat(r.corps()).contains(InscriptionRefuseeException.DEJA_INSCRIT));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM inscriptions WHERE npi = '2256599771'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM candidats WHERE npi = '2256599771'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void meme_candidat_simultanement_dans_deux_centres_et_deux_concours_du_meme_jour() throws Exception {
        IDENTITE.latence(300);
        // centres différents donc verrous différents : c'est la contrainte unique qui bloque
        List<Reponse> reponses = posterSimultanement(List.of(
                demande("3076016910", "DOUANES", "P"),
                demande("3076016910", "DOUANES", "C"),
                demande("3076016910", "IMPOTS", "L"),
                demande("3076016910", "IMPOTS", "C")));

        assertThat(reponses).filteredOn(r -> r.statut() == 201).hasSize(1);
        assertThat(reponses).filteredOn(r -> r.statut() == 409).hasSize(3);
        verifierInvariants();
    }

    @Test
    void cent_inscriptions_simultanees_reparties_sur_trois_centres() throws Exception {
        IDENTITE.latence(200);
        String[] centres = {"C", "P", "L"};
        List<String> demandes = IntStream.range(0, 100)
                .mapToObj(i -> demande("20000000%02d".formatted(i), "DOUANES", centres[i % 3]))
                .toList();

        List<Reponse> reponses = posterSimultanement(demandes);

        // C : 34, P : 33, L : 33 demandes pour 10 places
        assertThat(reponses).filteredOn(r -> r.statut() == 201).hasSize(34 + 33 + CAPACITE_L);
        verifierInvariants();
    }

    private void verifierInvariants() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM (SELECT centre_id, numero_table FROM inscriptions
                                      GROUP BY centre_id, numero_table HAVING COUNT(*) > 1) d""", Integer.class))
                .as("numéros de table en double").isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM (SELECT npi, concours_id FROM inscriptions
                                      GROUP BY npi, concours_id HAVING COUNT(*) > 1) d""", Integer.class))
                .as("inscriptions en double").isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM centres ce
                WHERE (SELECT COUNT(*) FROM inscriptions i WHERE i.centre_id = ce.id) > ce.capacite""", Integer.class))
                .as("centres en surréservation").isZero();
    }
}
