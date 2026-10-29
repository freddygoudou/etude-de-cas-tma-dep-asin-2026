package bj.asin.inscriptions.dto;

public record ResultatIdentite(Verdict verdict, String nom, String prenoms) {

    public enum Verdict {
        CONNU,
        INCONNU,
        EN_ATTENTE // timeout ou service KO
    }

    public static ResultatIdentite connu(String nom, String prenoms) {
        return new ResultatIdentite(Verdict.CONNU, nom, prenoms);
    }

    public static ResultatIdentite inconnu() {
        return new ResultatIdentite(Verdict.INCONNU, null, null);
    }

    public static ResultatIdentite enAttente() {
        return new ResultatIdentite(Verdict.EN_ATTENTE, null, null);
    }
}
