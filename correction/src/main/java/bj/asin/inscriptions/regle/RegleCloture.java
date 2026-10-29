package bj.asin.inscriptions.regle;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Clôture (INC-204) : concours.cloture est en UTC sans fuseau, on la compare à l'instant courant en UTC
 * et plus à l'heure locale du serveur.
 */
public final class RegleCloture {

    public static final ZoneId BENIN = ZoneId.of("Africa/Porto-Novo");

    private RegleCloture() {
    }

    public static Instant depuisBaseUtc(LocalDateTime clotureUtc) {
        return clotureUtc.toInstant(ZoneOffset.UTC);
    }

    public static boolean inscriptionsOuvertes(LocalDateTime clotureUtc, Clock clock) {
        return !clock.instant().isAfter(depuisBaseUtc(clotureUtc));
    }

    public static LocalDateTime enHeureDuBenin(LocalDateTime clotureUtc) {
        return LocalDateTime.ofInstant(depuisBaseUtc(clotureUtc), BENIN);
    }
}
