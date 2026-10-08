package pl.spokojniej.app.tests;

import android.app.*;
import android.content.*;
import android.database.sqlite.SQLiteDatabase;
import android.os.*;
import pl.spokojniej.app.*;
import java.time.*;

/** Run only on the dedicated clean test emulator: it resets test data. */
public final class DeviceChecks extends Instrumentation {
    private int checks;
    private Notification active(NotificationManager manager,int id) {
        for (android.service.notification.StatusBarNotification item : manager.getActiveNotifications()) if (item.getId()==id) return item.getNotification();
        return null;
    }
    private void check(boolean okay, String name) { checks++; if (!okay) throw new AssertionError(name); }
    @Override public void onCreate(Bundle args) { super.onCreate(args); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        Context c = getTargetContext();
        try (Store db = new Store(c)) {
            SQLiteDatabase sql = db.getWritableDatabase();
            sql.delete("events", null, null);
            sql.delete("gratitude", null, null);
            sql.delete("marks", null, null); Pause.cancel(c); Store.prefs(c).edit().clear().commit();
            long now = System.currentTimeMillis(), start = now - 3 * 86400000L;
            sql.execSQL("UPDATE meta SET value=? WHERE name='started'", new Object[]{start});
            check(db.anchor() == start, "Initial tracking anchor");
            check(Streaks.fullDays(db.anchor(), now) == 3, "Initial streak");
            check(db.todayStatus(now) == 0, "Missing check-in remains unknown");
            long calm = db.checkIn(now);
            check(calm >= 0 && db.todayStatus(now) == 1, "Calm check-in");
            check(db.checkIn(now) == -1 && db.history().size() == 1, "Double check-in is idempotent");
            long conflict = db.addConflict(now - 60000);
            check(db.todayStatus(now) == 2 && db.checkIn(now) == -1, "Conflict takes precedence");
            check(Streaks.fullDays(db.anchor(), now) == 0, "Conflict resets counter");
            long older = db.addConflict(now - 86400000L);
            check(db.anchor() == now - 60000, "Backdated entry does not replace newer conflict");
            db.delete(conflict);
            check(db.anchor() == now - 86400000L, "Undo restores earlier conflict");
            check(db.todayStatus(now) == 1, "Calm status restored after correcting conflict");
            db.editConflict(older, now - 2 * 86400000L);
            check(Streaks.fullDays(db.anchor(), now) == 2, "Edit recomputes streak");
            boolean rejected = false;
            try { db.addConflict(now + 86400000L); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "Future entry rejected");
            try (Store reopened = new Store(c)) { check(reopened.history().size() == 2, "SQLite data survives reopen"); }
            db.delete(older); db.delete(calm);
            check(db.history().isEmpty() && db.anchor() == start, "Delete restores initial state");
            long next = Streaks.nextTime(now, 8, 0, ZoneId.systemDefault());
            check(next > now, "Reminder always scheduled in future");
            Store.prefs(c).edit().putBoolean("morning", true).putBoolean("evening", true).commit();
            ReminderReceiver.schedule(c); CalmWidget.updateAll(c);
            check(c.getSystemService(NotificationManager.class).getNotificationChannel("daily") != null, "Notification channel created");
            check((c.getPackageManager().getPackageInfo(c.getPackageName(), android.content.pm.PackageManager.GET_PERMISSIONS).requestedPermissions.length == 2), "Only notification and reboot permissions");
            if (Build.VERSION.SDK_INT < 33 || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                NotificationManager notifications = c.getSystemService(NotificationManager.class);
                new ReminderReceiver().onReceive(c, new Intent().setAction("pl.spokojniej.app.EVENING"));
                Notification evening = active(notifications,102);
                long postedDeadline=SystemClock.elapsedRealtime()+5000;
                while (evening==null && SystemClock.elapsedRealtime()<postedDeadline) { SystemClock.sleep(50); evening=active(notifications,102); }
                check(evening != null, "Evening receiver posts actual notification");
                check(evening.actions != null && evening.actions.length == 2, "Evening notification offers pause and open actions");
                check(evening.actions[0].title.toString().equals("Potrzebuję chwili"),"Notification first action supports a break");
                evening.actions[0].actionIntent.send();
                long limit = SystemClock.elapsedRealtime() + 5000;
                while (!Pause.active(c) && SystemClock.elapsedRealtime() < limit) SystemClock.sleep(100);
                check(Pause.active(c) && db.todayStatus(System.currentTimeMillis())==0, "Notification opens real break without fabricating calm day");
                Pause.cancel(c); db.checkIn(System.currentTimeMillis());
                notifications.cancel(102);
                long cancelledDeadline=SystemClock.elapsedRealtime()+5000;
                while (active(notifications,102)!=null && SystemClock.elapsedRealtime()<cancelledDeadline) SystemClock.sleep(50);
                db.saveGratitude(Store.dayKey(System.currentTimeMillis()),"Za wspólny spacer.");
                new ReminderReceiver().onReceive(c, new Intent().setAction("pl.spokojniej.app.EVENING"));
                SystemClock.sleep(200);
                boolean reposted = false;
                for (android.service.notification.StatusBarNotification notification : notifications.getActiveNotifications()) if (notification.getId() == 102) reposted = true;
                check(!reposted, "Daily reminder is not repeated on the same day");
                sql.delete("events", null, null);
                sql.delete("gratitude", null, null);
            }
            String today=Store.dayKey(now), yesterday=LocalDate.parse(today).minusDays(1).toString();
            db.saveMarks(today,new boolean[]{true,true,false,true});
            db.addConflict(now-60000);
            check(db.marks(today)[0] && db.marks(today)[1] && db.marks(today)[3],"Calm steps and difficult feeling coexist and survive a conflict");
            db.saveMarks(today,new boolean[]{true,true,false,true});
            check(db.markHistory().size()==3,"Daily choices are idempotent");
            db.saveMarks(yesterday,new boolean[]{false,false,true,false});
            check(db.marks(yesterday)[2],"Can record a step after the day has passed");
            db.saveMarks(today,new boolean[]{true,false,false,true});
            check(!db.marks(today)[1] && db.markHistory().size()==3,"Editing removes only deselected choice");
            try (Store reopened=new Store(c)) { check(reopened.markHistory().size()==3,"Small steps persist after reopen"); }
            sql.delete("events",null,null);
            db.saveGratitude(today,"  Za wspólny spacer.\nZa spokojną rozmowę.  ");
            check(db.gratitude(today).equals("Za wspólny spacer.\nZa spokojną rozmowę."),"Gratitude trims outside whitespace and preserves Polish/multiline text");
            db.saveGratitude(today,"Za rodzinę.");
            check(db.gratitudeHistory().size()==1 && db.gratitude(today).equals("Za rodzinę."),"Gratitude edits one entry per day");
            db.saveGratitude(yesterday,"Za chwilę ciszy.");
            db.addConflict(now-3600000); db.checkIn(now-86400000L);
            long exportedAnchor=db.anchor(), exportedStarted=db.started();
            String json=Backup.export(c,db);
            Backup.Data backup=Backup.parse(json);
            check(backup.eventCount()==2 && backup.gratitudeCount()==2,"Backup includes events and gratitude");
            check(backup.markCount()==3,"Backup contains positive steps and difficult feeling");
            check(Backup.read(new java.io.ByteArrayInputStream(json.getBytes(java.nio.charset.StandardCharsets.UTF_8))).gratitudeCount()==2,"Backup stream round-trip");
            sql.delete("events",null,null); sql.delete("gratitude",null,null); sql.delete("marks",null,null);
            sql.execSQL("UPDATE meta SET value=? WHERE name='started'",new Object[]{now});
            Store.prefs(c).edit().clear().commit();
            Backup.Result imported=Backup.restore(c,db,backup);
            check(imported.events==2 && imported.gratitude==2 && imported.settingsRestored,"Restore on new phone adds all rows and settings");
            check(imported.marks==3 && db.marks(today)[0] && db.marks(today)[3] && db.marks(yesterday)[2],"Restore includes all daily choices");
            check(db.anchor()==exportedAnchor && db.started()==exportedStarted,"Restore preserves counter and tracking start");
            check(db.gratitude(today).equals("Za rodzinę.") && db.gratitude(yesterday).equals("Za chwilę ciszy."),"Restore preserves dated gratitude");
            Backup.Result repeated=Backup.restore(c,db,backup);
            check(repeated.events==0 && repeated.gratitude==0 && db.history().size()==2,"Repeated import is idempotent");
            check(repeated.marks==0 && db.markHistory().size()==3,"Repeat import does not multiply steps");
            db.saveGratitude(today,"Obecny tekst na telefonie.");
            Backup.Result merged=Backup.restore(c,db,backup);
            check(merged.keptGratitude==1 && db.gratitude(today).equals("Obecny tekst na telefonie."),"Merge keeps existing gratitude and reports conflict");
            boolean badBackup=false;
            try { Backup.parse(json.replace("spokojniej-backup","other-app")); } catch (org.json.JSONException expected) { badBackup=true; }
            check(badBackup && db.history().size()==2,"Wrong backup rejected without changing data");
            org.json.JSONObject invalid=new org.json.JSONObject(json);
            invalid.getJSONArray("gratitude").getJSONObject(0).put("day","2026-02-30"); badBackup=false;
            try { Backup.parse(invalid.toString()); } catch (org.json.JSONException expected) { badBackup=true; }
            check(badBackup,"Invalid calendar date rejected");
            invalid=new org.json.JSONObject(json); invalid.getJSONObject("settings").put("eveningHour",99); badBackup=false;
            try { Backup.parse(invalid.toString()); } catch (org.json.JSONException expected) { badBackup=true; }
            check(badBackup,"Invalid reminder settings rejected");
            org.json.JSONObject legacy=new org.json.JSONObject(json); legacy.put("version",1); legacy.remove("marks");
            check(Backup.parse(legacy.toString()).markCount()==0 && Backup.parse(legacy.toString()).eventCount()==2,"Reads old v1 backup format");
            invalid=new org.json.JSONObject(json); invalid.getJSONArray("marks").getJSONObject(0).put("kind","score"); badBackup=false;
            try { Backup.parse(invalid.toString()); } catch (org.json.JSONException expected) { badBackup=true; }
            check(badBackup && db.markHistory().size()==3,"Invalid step kind rejected without any write");
            invalid=new org.json.JSONObject(json); invalid.getJSONArray("marks").put(invalid.getJSONArray("marks").getJSONObject(0)); badBackup=false;
            try { Backup.parse(invalid.toString()); } catch (org.json.JSONException expected) { badBackup=true; }
            check(badBackup,"Duplicate daily choice rejected");
            Store.prefs(c).edit().putInt("eveningHour",17).commit();
            sql.delete("events",null,null); sql.delete("gratitude",null,null);
            check(!Backup.restore(c,db,backup).settingsRestored && Store.prefs(c).getInt("eveningHour",0)==17,"A phone with only daily steps keeps its reminder settings");
            db.addConflict(db.anchor());
            Backup.Data duplicateTimes=Backup.parse(Backup.export(c,db));
            sql.delete("events",null,null); sql.delete("gratitude",null,null);
            Backup.restore(c,db,duplicateTimes);
            check(db.history().size()==3,"Backup preserves separate events recorded at identical time");
            Backup.Result duplicateRepeat=Backup.restore(c,db,duplicateTimes);
            check(db.history().size()==3 && duplicateRepeat.events==0,"Repeated backup with identical timestamps stays idempotent");
            db.saveGratitude(today,"  ");
            check(db.gratitude(today).isEmpty(),"Blank edit removes gratitude");
            try (Store reopened=new Store(c)) { check(reopened.gratitude(yesterday).equals("Za chwilę ciszy."),"Gratitude survives database reopen"); }
            sql.delete("events",null,null); sql.delete("gratitude",null,null);
            Store.prefs(c).edit().clear().commit(); ReminderReceiver.schedule(c);
            sql.execSQL("UPDATE meta SET value=? WHERE name='started'", new Object[]{System.currentTimeMillis()});
            Pause.start(c,600); long until=Pause.until(c);
            check(Pause.active(c) && until>System.currentTimeMillis()+590000,"Ten-minute break starts with durable end time");
            check(!Pause.returnTime(c).isEmpty(),"Break offers a concrete return time");
            Pause.start(c,30);
            check(Pause.until(c)<System.currentTimeMillis()+31000,"Can choose short break");
            check(db.markHistory().size()==3,"Break never marks an achievement automatically");
            if (Build.VERSION.SDK_INT<33 || c.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED) {
                Store.prefs(c).edit().putLong("pauseUntil",System.currentTimeMillis()-1000).commit();
                new ReminderReceiver().onReceive(c,new Intent().setAction(Pause.END));
                NotificationManager notifications=c.getSystemService(NotificationManager.class);
                long deadline=SystemClock.elapsedRealtime()+5000;
                while (active(notifications,104)==null && SystemClock.elapsedRealtime()<deadline) SystemClock.sleep(50);
                check(active(notifications,104)!=null,"Completed break posts gentle return notification");
                notifications.cancel(104); SystemClock.sleep(200);
                new ReminderReceiver().onReceive(c,new Intent().setAction(Pause.END)); SystemClock.sleep(200);
                check(active(notifications,104)==null,"Completed break notification is not repeated");
            }
            Pause.cancel(c);
            check(!Pause.active(c) && Pause.until(c)==0,"Returning cancels the break");
            sql.delete("marks",null,null);
            result.putString("stream", "PASS: " + checks + " Android storage and scheduling checks\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) { result.putString("stream", "FAIL: " + e.toString() + "\n" + android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED, result); }
    }
}
