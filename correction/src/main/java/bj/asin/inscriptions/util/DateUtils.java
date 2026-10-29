package bj.asin.inscriptions.util;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class DateUtils {

    private DateUtils() {
    }

    // les instants sont stockés en UTC dans des TIMESTAMP sans fuseau
    public static LocalDateTime enUtc(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
