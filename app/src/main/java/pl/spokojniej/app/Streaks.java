package pl.spokojniej.app;

import java.time.*;

/** The counter measures complete 24-hour periods, not changes of calendar date. */
public final class Streaks {
    private Streaks() {}
    public static long fullDays(long anchor, long now) {
        return Math.max(0L, (now - anchor) / 86_400_000L);
    }
    public static long startOfDay(long now, ZoneId zone) {
        return Instant.ofEpochMilli(now).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli();
    }
    public static long nextTime(long now, int hour, int minute, ZoneId zone) {
        ZonedDateTime current = Instant.ofEpochMilli(now).atZone(zone);
        ZonedDateTime next = current.toLocalDate().atTime(hour, minute).atZone(zone);
        if (!next.isAfter(current)) next = current.toLocalDate().plusDays(1).atTime(hour, minute).atZone(zone);
        return next.toInstant().toEpochMilli();
    }
    public static String dayWord(long value) {
        return value == 1 ? "dzień" : "dni";
    }
}
