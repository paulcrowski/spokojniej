package pl.spokojniej.app;

import android.app.*;
import android.content.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** A chosen break survives leaving the screen; it never records a success automatically. */
public final class Pause {
    public static final String END = "pl.spokojniej.app.PAUSE_END";
    public static long until(Context c) { return Store.prefs(c).getLong("pauseUntil",0); }
    public static boolean active(Context c) { return until(c)>System.currentTimeMillis(); }
    public static String returnTime(Context c) { return DateTimeFormatter.ofPattern("HH:mm",new Locale("pl","PL")).format(Instant.ofEpochMilli(until(c)).atZone(ZoneId.systemDefault())); }
    public static void start(Context c, int seconds) {
        if (seconds!=30 && seconds!=600) throw new IllegalArgumentException("Unsupported break length");
        Store.prefs(c).edit().putLong("pauseUntil",System.currentTimeMillis()+seconds*1000L).putInt("pauseSeconds",seconds).putLong("pauseDelivered",0).commit();
        c.getSystemService(NotificationManager.class).cancel(104);
        schedule(c); CalmWidget.updateAll(c); ReminderReceiver.scheduleWidget(c);
    }
    public static void cancel(Context c) {
        Store.prefs(c).edit().remove("pauseUntil").remove("pauseSeconds").remove("pauseDelivered").commit();
        c.getSystemService(AlarmManager.class).cancel(operation(c));
        c.getSystemService(NotificationManager.class).cancel(104); CalmWidget.updateAll(c); ReminderReceiver.scheduleWidget(c);
    }
    private static PendingIntent operation(Context c) {
        return PendingIntent.getBroadcast(c,104,new Intent(c,ReminderReceiver.class).setAction(END),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
    }
    public static void schedule(Context c) {
        AlarmManager alarms=c.getSystemService(AlarmManager.class);
        alarms.cancel(operation(c));
        if (active(c) && ReminderReceiver.allowed(c)) alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,until(c),operation(c));
    }
    public static void finished(Context c) {
        long end=until(c);
        if (end==0 || end>System.currentTimeMillis() || Store.prefs(c).getLong("pauseDelivered",0)==end) return;
        Store.prefs(c).edit().putLong("pauseDelivered",end).commit();
        ReminderReceiver.createChannel(c);
        if (!ReminderReceiver.allowed(c)) return;
        PendingIntent open=PendingIntent.getActivity(c,74,new Intent(c,MainActivity.class).setAction("pause_done"),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder=new Notification.Builder(c,ReminderReceiver.CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Twoja chwila przerwy minęła")
            .setContentText("Możesz wrócić do rozmowy. Jeśli potrzebujesz więcej czasu, powiedz o tym.")
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(new Notification.Builder(c,ReminderReceiver.CHANNEL).setSmallIcon(R.drawable.ic_notification).setContentTitle("Spokojniej").setContentText("Chwila dla siebie").build());
        builder.setGroup("spokojniej_gentle").setGroupAlertBehavior(Notification.GROUP_ALERT_SUMMARY);
        Notification notification=builder.build();
        c.getSystemService(NotificationManager.class).notify(104,notification);
    }
}
