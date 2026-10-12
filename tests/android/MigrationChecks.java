package pl.spokojniej.app.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import pl.spokojniej.app.*;
import org.json.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.io.File;

/** Seeds through the old app's Store, then verifies after installing the new APK. */
public final class MigrationChecks extends Instrumentation {
    private String mode;
    private int checks;
    @Override public void onCreate(Bundle args) { mode=args.getString("mode","verify"); super.onCreate(args); start(); }
    private void check(boolean okay,String name) { checks++; if (!okay) throw new AssertionError(name); }
    @Override public void onStart() {
        Bundle result=new Bundle(); Context c=getTargetContext(); File fixture=new File(c.getFilesDir(),"migration-fixture.json");
        try (Store db=new Store(c)) {
            if (mode.startsWith("seed")) {
                long now=System.currentTimeMillis(), start=now-5*86400000L, conflict=now-3*86400000L, calm=now-2*86400000L;
                db.getWritableDatabase().delete("events",null,null);
                db.getWritableDatabase().execSQL("UPDATE meta SET value=? WHERE name='started'",new Object[]{start});
                db.addConflict(conflict); db.checkIn(calm);
                JSONObject data=new JSONObject().put("started",start).put("conflict",conflict).put("calm",calm).put("schema",db.getReadableDatabase().getVersion());
                if (db.getReadableDatabase().getVersion()>=2) {
                    String note="Za wspólny spacer przed aktualizacją.";
                    db.saveGratitude(Store.dayKey(now),note); data.put("note",note).put("day",Store.dayKey(now));
                    Store.prefs(c).edit().putBoolean("evening",true).putInt("eveningHour",20).putInt("eveningMinute",30).commit();
                    if(db.getReadableDatabase().getVersion()>=3)db.saveMarks(Store.dayKey(now),new boolean[]{true,false,true,false});
                    Files.write(new File(c.getFilesDir(),"old-backup.json").toPath(),Backup.export(c,db).getBytes(StandardCharsets.UTF_8));
                }
                Files.write(fixture.toPath(),data.toString().getBytes(StandardCharsets.UTF_8));
                result.putString("stream","SEEDED: old app database with two events\n");
            } else {
                JSONObject expected=new JSONObject(new String(Files.readAllBytes(fixture.toPath()),StandardCharsets.UTF_8));
                check(db.getReadableDatabase().getVersion()==4,"Schema upgraded to v4");
                check(db.history().size()==2,"Old event rows preserved");
                check(db.started()==expected.getLong("started"),"Old tracking start preserved");
                check(db.anchor()==expected.getLong("conflict"),"Old last conflict preserved");
                check(db.history().get(0).time==expected.getLong("calm"),"Old calm timestamp preserved");
                check(db.markHistory().size()==(expected.getInt("schema")>=3?2:0),"Existing daily steps preserved, no new choices invented");
                check(db.doneHistory().isEmpty(),"New done table starts empty");
                for(Store.Entry entry:db.history())for(boolean tag:db.contexts(entry.id))check(!tag,"Old conversations have no invented circumstances");
                if (expected.has("note")) {
                    check(db.gratitude(expected.getString("day")).equals(expected.getString("note")),"Old gratitude preserved exactly");
                    check(Store.prefs(c).getBoolean("evening",false) && Store.prefs(c).getInt("eveningHour",0)==20 && Store.prefs(c).getInt("eveningMinute",0)==30,"Existing reminder settings preserved");
                    String old=new String(Files.readAllBytes(new File(c.getFilesDir(),"old-backup.json").toPath()),StandardCharsets.UTF_8);
                    check(Backup.parse(old).gratitudeCount()==1 && Backup.parse(old).markCount()==(expected.getInt("schema")>=3?2:0),"Old backup remains readable after APK upgrade");
                } else check(db.gratitudeHistory().isEmpty(),"New gratitude table initially empty");
                db.saveGratitude(Store.dayKey(System.currentTimeMillis()),"Pozytywny wpis po aktualizacji.");
                check(db.gratitudeHistory().size()==1 && db.anchor()==expected.getLong("conflict"),"Gratitude works without changing old counter");
                check(Backup.parse(Backup.export(c,db)).eventCount()==2,"Backup includes preserved old rows");
                db.saveMarks(Store.dayKey(System.currentTimeMillis()),new boolean[]{true,false,false,true});
                check(db.markHistory().size()==2 && db.anchor()==expected.getLong("conflict"),"New daily steps work without changing old counter");
                fixture.delete(); result.putString("stream","PASS: "+checks+" APK upgrade and migration checks\n");
            }
            finish(Activity.RESULT_OK,result);
        } catch (Throwable e) { result.putString("stream","FAIL: "+android.util.Log.getStackTraceString(e)); finish(Activity.RESULT_CANCELED,result); }
    }
}
