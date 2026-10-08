import pl.spokojniej.app.Streaks;
import java.time.*;

public class StreaksTest {
    static int checks;
    static void eq(long actual, long expected, String reason) {
        checks++; if (actual != expected) throw new AssertionError(reason + ": " + actual + " != " + expected);
    }
    static long ms(String instant) { return Instant.parse(instant).toEpochMilli(); }
    public static void main(String[] args) {
        long anchor = ms("2026-10-08T10:00:00Z");
        eq(Streaks.fullDays(anchor, anchor), 0, "first launch");
        eq(Streaks.fullDays(anchor, anchor + 86399999), 0, "less than 24h");
        eq(Streaks.fullDays(anchor, anchor + 86400000), 1, "exactly 24h");
        eq(Streaks.fullDays(anchor, anchor + 7 * 86400000L), 7, "one week");
        eq(Streaks.fullDays(anchor, anchor - 1000), 0, "clock moved backwards");
        ZoneId warsaw = ZoneId.of("Europe/Warsaw");
        eq(Streaks.startOfDay(ms("2026-10-08T13:10:00Z"), warsaw), ms("2026-10-07T22:00:00Z"), "local day");
        eq(Streaks.nextTime(ms("2026-10-08T05:00:00Z"),8,0,warsaw), ms("2026-10-08T06:00:00Z"), "morning today");
        eq(Streaks.nextTime(ms("2026-10-08T06:00:00Z"),8,0,warsaw), ms("2026-10-09T06:00:00Z"), "schedule tomorrow at exact slot");
        eq(Streaks.nextTime(ms("2026-10-24T19:00:00Z"),8,0,warsaw), ms("2026-10-25T07:00:00Z"), "autumn DST");
        eq(Streaks.nextTime(ms("2026-03-28T20:00:00Z"),8,0,warsaw), ms("2026-03-29T06:00:00Z"), "spring DST");
        eq(Streaks.nextTime(ms("2026-10-08T15:00:00Z"),20,30,warsaw), ms("2026-10-08T18:30:00Z"), "evening today");
        eq(Streaks.nextTime(ms("2026-03-28T12:00:00Z"),2,30,warsaw), ms("2026-03-29T01:30:00Z"), "missing DST hour resolves forward");
        if (!"dzień".equals(Streaks.dayWord(1)) || !"dni".equals(Streaks.dayWord(0)) || !"dni".equals(Streaks.dayWord(22))) throw new AssertionError("Polish day wording");
        System.out.println("PASS: " + checks + " time and streak checks + Polish wording");
    }
}
