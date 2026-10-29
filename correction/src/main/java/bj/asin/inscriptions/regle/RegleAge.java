package bj.asin.inscriptions.regle;

import java.time.LocalDate;

/**
 * 35 ans au plus au 31/12 de l'année du concours (INC-205).
 * Au 31/12 l'anniversaire est forcément passé, donc l'âge = différence des années.
 */
public final class RegleAge {

    public static final int AGE_MAX = 35;

    private RegleAge() {
    }

    public static int ageAu31Decembre(LocalDate naissance, int anneeConcours) {
        return anneeConcours - naissance.getYear();
    }

    public static boolean estEligible(LocalDate naissance, LocalDate dateEpreuve) {
        return ageAu31Decembre(naissance, dateEpreuve.getYear()) <= AGE_MAX;
    }
}
