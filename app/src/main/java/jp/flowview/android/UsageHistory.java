package jp.flowview.android;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;

/** Daily totals only: no URLs, playback details or persisted running session. */
final class UsageHistory {
    final Map<LocalDate, Long> days = new TreeMap<>();
    final LocalDate firstDay;
    private boolean running;
    private long lastWall, lastElapsed;

    UsageHistory(LocalDate firstDay) { this.firstDay = firstDay; }

    void start(long wall, long elapsed) {
        if (running) return;
        running = true;
        lastWall = wall;
        lastElapsed = elapsed;
    }

    void checkpoint(long wall, long elapsed, ZoneId zone) {
        if (!running) return;
        long duration = Math.max(0, elapsed - lastElapsed);
        // Measure with a monotonic clock; wall-clock changes cannot inflate usage.
        long cursor = lastWall;
        long end = cursor + duration;
        while (cursor < end) {
            LocalDate date = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate();
            long next = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            long until = Math.min(end, next);
            days.merge(date, until - cursor, Long::sum);
            cursor = until;
        }
        lastWall = wall;
        lastElapsed = elapsed;
        prune(Instant.ofEpochMilli(wall).atZone(zone).toLocalDate());
    }

    void stop(long wall, long elapsed, ZoneId zone) {
        checkpoint(wall, elapsed, zone);
        running = false;
    }

    void prune(LocalDate today) { days.keySet().removeIf(d -> d.isBefore(today.minusDays(364))); }

    long total(LocalDate today, int count) {
        LocalDate from = today.minusDays(count - 1L);
        return days.entrySet().stream().filter(e -> !e.getKey().isBefore(from) && !e.getKey().isAfter(today))
                .mapToLong(Map.Entry::getValue).sum();
    }

    long average(LocalDate today) {
        long count = Math.max(1, Math.min(365, ChronoUnit.DAYS.between(firstDay, today) + 1));
        return total(today, 365) / count;
    }
}
