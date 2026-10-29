package bj.asin.inscriptions.exception;

import org.springframework.http.HttpStatus;

public class InscriptionRefuseeException extends RuntimeException {

    public static final String CLOSES = "Inscriptions closes pour ce concours.";
    public static final String AGE = "Âge limite dépassé : 35 ans au 31 décembre de l'année du concours.";
    public static final String DEJA_INSCRIT = "Vous êtes déjà inscrit à ce concours.";
    public static final String MEME_JOUR = "Vous êtes déjà inscrit à un concours dont l'épreuve a lieu le même jour.";
    public static final String COMPLET = "Ce centre d'examen est complet.";
    public static final String NPI_INCONNU = "NPI inconnu du service d'identification.";

    private final HttpStatus statut;

    public InscriptionRefuseeException(HttpStatus statut, String message) {
        super(message);
        this.statut = statut;
    }

    public static InscriptionRefuseeException conflit(String message) {
        return new InscriptionRefuseeException(HttpStatus.CONFLICT, message);
    }

    public HttpStatus statut() {
        return statut;
    }
}
