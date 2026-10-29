package bj.asin.inscriptions.entity;

public enum StatutInscription {

    VALIDEE("validee"),
    EN_ATTENTE_VERIFICATION("en_attente_verification");

    private final String valeur;

    StatutInscription(String valeur) {
        this.valeur = valeur;
    }

    public String valeur() {
        return valeur;
    }
}
