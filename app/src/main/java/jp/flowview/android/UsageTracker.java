package jp.flowview.android;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import java.time.LocalDate;
import java.time.ZoneId;

/** Owned by the Activity, never the background playback service. */
final class UsageTracker {
    private final Context context;
    private final SharedPreferences prefs;
    final UsageHistory history;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean visible;
    private final Runnable tick = new Runnable() {
        @Override public void run() { update(); }
    };

    UsageTracker(Context context) {
        this.context = context;
        prefs = context.getSharedPreferences("satimod_usage", Context.MODE_PRIVATE);
        LocalDate first;
        try { first = LocalDate.parse(prefs.getString("first", LocalDate.now().toString())); }
        catch (RuntimeException e) { first = LocalDate.now(); }
        history = new UsageHistory(first);
        for (String key : prefs.getAll().keySet()) {
            if (!key.startsWith("day:")) continue;
            try { history.days.put(LocalDate.parse(key.substring(4)), Math.max(0, prefs.getLong(key, 0))); }
            catch (RuntimeException ignored) { /* Ignore a malformed daily entry. */ }
        }
        save();
    }

    void visible(boolean value) { visible = value; update(); }

    void update() {
        handler.removeCallbacks(tick);
        long wall = System.currentTimeMillis(), elapsed = SystemClock.elapsedRealtime();
        ZoneId zone = ZoneId.systemDefault();
        PowerManager power = context.getSystemService(PowerManager.class);
        KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);
        if (visible && power.isInteractive() && !keyguard.isKeyguardLocked()) {
            history.checkpoint(wall, elapsed, zone);
            history.start(wall, elapsed);
        } else history.stop(wall, elapsed, zone);
        save();
        if (visible) handler.postDelayed(tick, 15_000);
    }

    private void save() {
        history.prune(LocalDate.now());
        SharedPreferences.Editor editor = prefs.edit().clear().putString("first", history.firstDay.toString());
        history.days.forEach((day, duration) -> editor.putLong("day:" + day, duration));
        editor.apply();
    }
}
