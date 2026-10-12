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
            sql.delete("done",null,null);sql.delete("marks", null, null); Pause.cancel(c); Store.prefs(c).edit().clear().commit();
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
            sql.delete("events",null,null);sql.delete("gratitude",null,null);sql.delete("done",null,null);
            check(db.checkInDay(yesterday)>=0 && db.dayStatus(yesterday)==1 && db.dayStatus(today)==0,"Yesterday calm check-in targets only selected day");
            check(db.checkInDay(yesterday)==-1,"Past calm check-in remains idempotent");
            boolean future=false;try{db.checkInDay(LocalDate.now().plusDays(1).toString());}catch(IllegalArgumentException expected){future=true;}check(future,"Future calm day is rejected");
            String doneId=db.saveDone(null,yesterday,"  Zrobiłem kawę.  ");
            check(db.done(yesterday).size()==1 && db.done(today).isEmpty() && db.done(yesterday).get(0).note.equals("Zrobiłem kawę."),"Done list trims and belongs to selected date");
            db.saveDone(doneId,yesterday,"Wziąłem prysznic.");check(db.done(yesterday).size()==1 && db.done(yesterday).get(0).note.equals("Wziąłem prysznic."),"Editing done does not duplicate item");
            future=false;try{db.saveDone(null,today,"   ");}catch(IllegalArgumentException expected){future=true;}check(future && db.done(today).isEmpty(),"Empty done item rejected");
            future=false;try{db.saveDone(null,LocalDate.now().plusDays(1).toString(),"Spacer");}catch(IllegalArgumentException expected){future=true;}check(future,"Future done day rejected");
            long same=now-60000;boolean[] tags={true,true,false,false,true,false};
            long first=db.addConflict(same,tags),second=db.addConflict(same,new boolean[]{false,false,true,false,false,true});
            check(db.contexts(first)[0] && db.contexts(first)[1] && db.contexts(first)[4] && !db.contexts(first)[5],"Several optional conversation circumstances persist");
            db.editConflict(first,same,new boolean[]{false,true,false,false,true,false});check(!db.contexts(first)[0] && db.contexts(first)[1],"Editing circumstances removes only deselected tags");
            check(db.done(yesterday).size()==1 && db.dayStatus(yesterday)==1,"Conversation does not erase done items or past calm day");
            String journalJson=Backup.export(c,db);Backup.Data journal=Backup.parse(journalJson);check(journal.doneCount()==1,"Backup v3 includes completed items");
            sql.delete("events",null,null);sql.delete("done",null,null);check(sql.rawQuery("SELECT * FROM event_contexts",null).getCount()==0,"Deleting conversations cleans metadata");
            Backup.Result journalImport=Backup.restore(c,db,journal);check(journalImport.done==1 && journalImport.contexts==4 && db.done(yesterday).size()==1,"New-phone restore includes done and circumstances");
            int[] counts={0,0};for(Store.Entry entry:db.history())if("conflict".equals(entry.kind)){if(db.contexts(entry.id)[1])counts[0]++;if(db.contexts(entry.id)[5])counts[1]++;}
            check(counts[0]==1 && counts[1]==1,"Identical-time conversations keep their separate circumstances");
            Backup.Result again=Backup.restore(c,db,journal);check(again.done==0 && again.contexts==0 && again.events==0,"Repeat import does not duplicate journal metadata");
            db.saveDone(doneId,yesterday,"Nowy tekst na telefonie.");Backup.Result preserve=Backup.restore(c,db,journal);check(preserve.keptDone==1 && db.done(yesterday).get(0).note.equals("Nowy tekst na telefonie."),"Restore preserves edited done item and reports it");
            org.json.JSONObject oldV2=new org.json.JSONObject(journalJson);oldV2.put("version",2);oldV2.remove("done");for(int i=0;i<oldV2.getJSONArray("events").length();i++)oldV2.getJSONArray("events").getJSONObject(i).remove("contexts");check(Backup.parse(oldV2.toString()).doneCount()==0,"Old v2 backup remains readable");
            org.json.JSONObject bad=new org.json.JSONObject(journalJson);bad.getJSONArray("events").getJSONObject(0).put("contexts",new org.json.JSONArray().put("diagnosis"));future=false;try{Backup.parse(bad.toString());}catch(org.json.JSONException expected){future=true;}check(future,"Unknown circumstances rejected before any writes");
            bad=new org.json.JSONObject(journalJson);bad.getJSONArray("done").put(bad.getJSONArray("done").getJSONObject(0));future=false;try{Backup.parse(bad.toString());}catch(org.json.JSONException expected){future=true;}check(future,"Duplicate done identities rejected");
            db.deleteDone(doneId);check(db.done(yesterday).isEmpty(),"Done item can be deleted");
            sql.delete("events",null,null);sql.delete("gratitude",null,null);sql.delete("marks",null,null);sql.delete("done",null,null);
            {
            long momentTime=now-120000,originalAnchor=db.anchor();
            String momentDay=Store.dayKey(momentTime);
            boolean[] contexts7={true,false,false,false,true,false,true},help4={true,true,false,false};
            long tension=db.addMoment(momentTime,"tension",contexts7,help4);
            check(db.anchor()==originalAnchor && db.dayStatus(momentDay)==0,"Tension alone never resets counter or implies a difficult day");
            check(db.checkInDay(momentDay)>=0 && db.dayStatus(momentDay)==1,"A calm day can be recorded alongside tension");
            check(db.momentKind(tension).equals("tension") && db.contexts(tension)[6] && db.help(tension)[0] && db.help(tension)[1],"Moment type, surprise and several helpful actions persist");
            db.editMoment(tension,momentTime,"impulse",contexts7,help4);
            check(db.anchor()==momentTime && db.dayStatus(momentDay)==2,"Impulsive reaction changes counter and day status");
            db.editMoment(tension,momentTime,"tension",contexts7,new boolean[]{false,false,true,true});
            check(db.anchor()==originalAnchor && db.dayStatus(momentDay)==1,"Reclassifying as tension restores previous calm check-in and counter");
            check(!db.help(tension)[0] && db.help(tension)[2] && db.help(tension)[3],"Editing helpful actions removes deselected choices");
            long impulse=db.addMoment(momentTime,"impulse",new boolean[7],new boolean[4]);
            long escalatedMoment=db.addMoment(momentTime,"conflict",new boolean[7],new boolean[4]);
            check(db.checkInDay(momentDay)==-1,"Impulsive and escalated moments still prevent contradictory calm check-in");
            int beforeCount=db.history().size();boolean invalidMoment=false;
            try{db.addMoment(momentTime,"unknown",contexts7,help4);}catch(IllegalArgumentException expected){invalidMoment=true;}
            check(invalidMoment && db.history().size()==beforeCount,"Invalid moment rolls back event and circumstances together");
            String momentsJson=Backup.export(c,db);org.json.JSONObject momentsRoot=new org.json.JSONObject(momentsJson);
            check(momentsRoot.getInt("version")==4,"Backup format 4 supports distinct moment types and helpful actions");
            Backup.Data moments=Backup.parse(momentsJson);sql.delete("events",null,null);
            check(sql.rawQuery("SELECT * FROM moment_details",null).getCount()==0 && sql.rawQuery("SELECT * FROM event_help",null).getCount()==0,"Deleting moments cleans type and help metadata");
            Backup.restore(c,db,moments);int[] types={0,0,0};
            for(Store.Entry entry:db.history())if(Store.momentKind(entry.kind))types[java.util.Arrays.asList(Store.MOMENT_KINDS).indexOf(entry.kind)]++;
            check(types[0]==1 && types[1]==1 && types[2]==1,"New-phone restore preserves different moment types at identical timestamps");
            Store.Entry restoredTension=null;for(Store.Entry entry:db.history())if(entry.kind.equals("tension"))restoredTension=entry;
            check(restoredTension!=null && db.contexts(restoredTension.id)[6] && db.help(restoredTension.id)[2] && db.help(restoredTension.id)[3],"Restore preserves surprise and helpful actions on correct moment");
            check(Backup.restore(c,db,moments).events==0 && db.history().size()==4,"Repeated format 4 import does not duplicate any moment");
            org.json.JSONObject invalidHelp=new org.json.JSONObject(momentsJson);invalidHelp.getJSONArray("events").getJSONObject(0).put("help",new org.json.JSONArray().put("unknown"));invalidMoment=false;
            try{Backup.parse(invalidHelp.toString());}catch(org.json.JSONException expected){invalidMoment=true;}check(invalidMoment,"Unknown helpful actions are rejected before restore writes");
            org.json.JSONObject oldV3=new org.json.JSONObject(journalJson).put("version",3);for(int i=0;i<oldV3.getJSONArray("events").length();i++)oldV3.getJSONArray("events").getJSONObject(i).remove("help");
            sql.delete("events",null,null);Backup.restore(c,db,Backup.parse(oldV3.toString()));
            check(db.history().size()==3 && db.anchor()==same,"Old format 3 conversations retain their original meaning and times");
            for(Store.Entry entry:db.history())if(entry.kind.equals("conflict"))check(!db.help(entry.id)[0],"No helpful actions invented for old records");
            sql.delete("events",null,null);sql.delete("done",null,null);
            }
            for(int i=0;i<25;i++)db.saveDone(null,today,"Testowy wpis do sprawdzenia przewijania "+i);
            Activity diary=startActivitySync(new Intent(c,MainActivity.class).setAction(Intent.ACTION_MAIN).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));waitForIdleSync();
            java.lang.reflect.Field scrollField=MainActivity.class.getDeclaredField("contentScroll");scrollField.setAccessible(true);
            java.lang.reflect.Field refreshField=MainActivity.class.getDeclaredField("refresh");refreshField.setAccessible(true);
            android.widget.ScrollView beforeScroll=(android.widget.ScrollView)scrollField.get(diary);
            long ready=SystemClock.elapsedRealtime()+5000;
            while((beforeScroll.getHeight()==0 || beforeScroll.getChildAt(0).getHeight()<=beforeScroll.getHeight()) && SystemClock.elapsedRealtime()<ready)SystemClock.sleep(50);
            check(beforeScroll.getChildAt(0).getHeight()>beforeScroll.getHeight(),"Long diary layout is ready before scroll test");
            runOnMainSync(() -> beforeScroll.scrollTo(0,beforeScroll.getChildAt(0).getHeight()));waitForIdleSync();int position=beforeScroll.getScrollY();check(position>0,"A long done list scrolls");
            Runnable refresh=(Runnable)refreshField.get(diary);runOnMainSync(refresh);waitForIdleSync();
            android.widget.ScrollView afterScroll=(android.widget.ScrollView)scrollField.get(diary);ready=SystemClock.elapsedRealtime()+5000;while(afterScroll.getHeight()==0 && SystemClock.elapsedRealtime()<ready)SystemClock.sleep(50);waitForIdleSync();check(afterScroll.getScrollY()==position,"Periodic diary refresh preserves actual scroll position");
            runOnMainSync(() -> diary.finish());waitForIdleSync();sql.delete("done",null,null);
            result.putString("stream", "PASS: " + checks + " Android storage and scheduling checks\n");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable e) { result.putString("stream", "FAIL: " + e.toString() + "\n" + android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED, result); }
    }
}
