package jp.flowview.android;

import java.time.LocalDate;
import java.time.ZoneId;

/** No Android runtime required: javac -d <dir> UsageHistory.java UsageHistoryCheck.java. */
public final class UsageHistoryCheck {
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");
    private static void equal(long expected, long actual) {
        if (expected != actual) throw new AssertionError("Expected " + expected + ", got " + actual);
    }
    private static long midnight(LocalDate date, ZoneId zone) {
        return date.atStartOfDay(zone).toInstant().toEpochMilli();
    }
    public static void main(String[] args) {
        LocalDate today = LocalDate.of(2026, 11, 11);
        long wall = midnight(today, TOKYO);
        UsageHistory h = new UsageHistory(today);
        h.start(wall, 0); h.start(wall + 1000, 1000);
        h.checkpoint(wall + 2000, 2000, TOKYO);
        h.stop(wall + 5000, 5000, TOKYO);
        h.stop(wall + 10000, 10000, TOKYO);
        equal(5000, h.total(today, 1)); // no double start/checkpoint/stop counting
        h.start(wall + 60000, 60000); h.stop(wall + 61000, 61000, TOKYO);
        equal(6000, h.total(today, 1)); // inactive gap excluded

        h = new UsageHistory(today.minusDays(1));
        h.start(wall - 30000, 0); h.stop(wall + 30000, 60000, TOKYO);
        equal(30000, h.total(today, 1)); equal(60000, h.total(today, 7));
        equal(30000, h.average(today)); // midnight split and zero-use days denominator

        h = new UsageHistory(today.minusDays(500));
        for (int i=0;i<400;i++) h.days.put(today.minusDays(i), 60000L);
        h.days.put(today.plusDays(1), 999999L);
        h.prune(today);
        equal(7*60000L,h.total(today,7)); equal(30*60000L,h.total(today,30));
        equal(365*60000L,h.total(today,365)); equal(60000,h.average(today));
        if(h.days.containsKey(today.minusDays(365))) throw new AssertionError("Expired entry retained");

        h = new UsageHistory(today);
        h.start(wall,0);h.checkpoint(wall+3600000,1000,TOKYO);
        h.stop(wall,2000,TOKYO);equal(2000,h.total(today,1)); // wall clock jump
        h = new UsageHistory(today); // restart never restores a running timer
        h.stop(wall+86400000,86400000,TOKYO);equal(0,h.total(today,365));

        ZoneId ny = ZoneId.of("America/New_York");
        LocalDate spring = LocalDate.of(2026,3,8);
        h = new UsageHistory(spring);
        long start=midnight(spring,ny), end=midnight(spring.plusDays(1),ny);
        h.start(start,0);h.stop(end,end-start,ny);
        equal(23*3600000L,h.total(spring,1)); // DST day length
        System.out.println("UsageHistory: 13 assertions passed");
    }
}
