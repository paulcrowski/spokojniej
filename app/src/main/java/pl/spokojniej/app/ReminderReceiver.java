package pl.spokojniej.app;

import android.Manifest;
import android.app.*;
import android.appwidget.AppWidgetManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import java.time.ZoneId;

public final class ReminderReceiver extends BroadcastReceiver {
    static final String MORNING = "pl.spokojniej.app.MORNING", EVENING = "pl.spokojniej.app.EVENING", REFRESH = "pl.spokojniej.app.REFRESH", CHECK = "pl.spokojniej.app.CHECK";
    static final String CHANNEL = "daily";
    @Override public void onReceive(Context c, Intent intent) {
        String action = intent.getAction();
        if (CHECK.equals(action)) {
            try (Store db = new Store(c)) { db.checkIn(System.currentTimeMillis()); }
            c.getSystemService(NotificationManager.class).cancel(102);
        } else if (MORNING.equals(action) || EVENING.equals(action)) {
            boolean morning = MORNING.equals(action);
            String key=morning ? "morning" : "evening";
            String day=Store.dayKey(System.currentTimeMillis());
            if (Store.prefs(c).getBoolean(key,false) && allowed(c) && !day.equals(Store.prefs(c).getString(key+"Delivered",""))) {
                Store.prefs(c).edit().putString(key+"Delivered",day).commit();
                notifyUser(c,morning);
            }
        } else if (Pause.END.equals(action)) Pause.finished(c);
        CalmWidget.updateAll(c);
        // A widget refresh must not cancel a daily alarm awaiting delayed delivery.
        // Reschedule only the reminder that just fired (both may share a time).
        if (MORNING.equals(action) || EVENING.equals(action)) {
            scheduleOne(c, MORNING.equals(action) ? 0 : 1);
            scheduleWidget(c);
        } else if (CHECK.equals(action) || REFRESH.equals(action) || Pause.END.equals(action)) scheduleWidget(c);
        else schedule(c);
    }
    static void createChannel(Context c) {
        NotificationChannel channel = new NotificationChannel(CHANNEL, "Krótka chwila dla siebie", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Dobrowolna chwila przed wieczorem i powrót po przerwie");
        channel.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
        c.getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }
    static boolean allowed(Context c) {
        return (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
            && c.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }
    static void notifyUser(Context c, boolean morning) {
        createChannel(c);
        if (!allowed(c)) return;
        Intent open = new Intent(c, MainActivity.class).setAction("home");
        PendingIntent content = PendingIntent.getActivity(c, 70, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(morning ? "Twoje zdanie ma znaczenie" : "Chwila dla Ciebie przed wieczorem")
            .setContentText(morning ? "Możesz dać sobie chwilę, zanim odpowiesz." : "Jak się czujesz? Czy potrzebujesz odpoczynku, czegoś do jedzenia lub chwili ciszy?")
            .setContentIntent(content).setAutoCancel(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(new Notification.Builder(c, CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("Spokojniej").setContentText("Chwila dla siebie").build());
        PendingIntent pause=PendingIntent.getActivity(c,73,new Intent(c,MainActivity.class).setAction("pause"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        b.addAction(new Notification.Action.Builder(null,"Potrzebuję chwili",pause).build());
        b.addAction(new Notification.Action.Builder(null,"Otwórz",content).build());
        b.setGroup("spokojniej_gentle").setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY);
        c.getSystemService(NotificationManager.class).notify(morning ? 101 : 102, b.build());
    }
    public static void schedule(Context c) {
        createChannel(c);
        scheduleOne(c, 0); scheduleOne(c, 1); scheduleWidget(c); Pause.schedule(c);
    }
    private static void scheduleOne(Context c, int i) {
        android.content.SharedPreferences p = Store.prefs(c);
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        long now = System.currentTimeMillis();
            String key = i == 0 ? "morning" : "evening";
            PendingIntent operation = PendingIntent.getBroadcast(c, 100 + i, new Intent(c, ReminderReceiver.class).setAction(i == 0 ? MORNING : EVENING), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            alarms.cancel(operation);
            if (p.getBoolean(key, false) && allowed(c)) {
                long next = Streaks.nextTime(now, p.getInt(key + "Hour", i == 0 ? 8 : 18), p.getInt(key + "Minute", 0), ZoneId.systemDefault());
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, operation);
            }
    }
    static void scheduleWidget(Context c) {
        AlarmManager alarms = c.getSystemService(AlarmManager.class);
        long now = System.currentTimeMillis();
        PendingIntent refresh = PendingIntent.getBroadcast(c, 103, new Intent(c, ReminderReceiver.class).setAction(REFRESH), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        alarms.cancel(refresh);
        if (AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, CalmWidget.class)).length > 0) {
            long next = Streaks.nextTime(now, 0, 0, ZoneId.systemDefault());
            long end=Pause.until(c);
            if (end>now) next=Math.min(next,end);
            alarms.set(AlarmManager.RTC, next, refresh);
        }
    }
}
