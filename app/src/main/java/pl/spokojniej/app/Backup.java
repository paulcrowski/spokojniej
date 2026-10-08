package pl.spokojniej.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Portable explicit backups; no automatic upload and no Android account dependency. */
public final class Backup {
    public static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final String FORMAT = "spokojniej-backup";
    public static final class Data {
        final JSONArray events, gratitude, marks;
        final long started;
        final JSONObject settings;
        Data(JSONArray e, JSONArray g, JSONArray m, long s, JSONObject p) { events=e; gratitude=g; marks=m; started=s; settings=p; }
        public int eventCount() { return events.length(); }
        public int gratitudeCount() { return gratitude.length(); }
        public int markCount() { return marks.length(); }
    }
    public static final class Result {
        public int events, gratitude, marks, keptGratitude;
        public boolean settingsRestored;
    }
    private Backup() {}
    public static String export(Context c, Store db) throws JSONException {
        SQLiteDatabase snapshot=db.getReadableDatabase(); snapshot.beginTransaction();
        try {
        JSONObject root = new JSONObject().put("format", FORMAT).put("version", 2).put("createdAt", System.currentTimeMillis()).put("startedAt", db.started());
        JSONArray events = new JSONArray();
        try (Cursor cursor = db.getReadableDatabase().rawQuery("SELECT happened_at,kind,day_key FROM events ORDER BY happened_at,id", null)) {
            while (cursor.moveToNext()) events.put(new JSONObject().put("at", cursor.getLong(0)).put("kind", cursor.getString(1)).put("day", cursor.isNull(2) ? JSONObject.NULL : cursor.getString(2)));
        }
        JSONArray gratitude = new JSONArray();
        for (Store.Gratitude g : db.gratitudeHistory()) gratitude.put(new JSONObject().put("day", g.day).put("note", g.note).put("updatedAt", g.updated));
        JSONArray marks=new JSONArray();
        for (Store.Mark m : db.markHistory()) marks.put(new JSONObject().put("day",m.day).put("kind",m.kind).put("updatedAt",m.updated));
        SharedPreferences p = Store.prefs(c);
        JSONObject settings = new JSONObject();
        for (String key : new String[]{"morning", "evening"}) {
            settings.put(key, p.getBoolean(key, false));
            settings.put(key+"Hour", p.getInt(key+"Hour", key.equals("morning") ? 8 : 18));
            settings.put(key+"Minute", p.getInt(key+"Minute", 0));
        }
        String json = root.put("events", events).put("gratitude", gratitude).put("marks",marks).put("settings", settings).toString(2);
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new IllegalArgumentException("Kopia przekracza limit 5 MB.");
        return json;
        } finally { snapshot.endTransaction(); }
    }
    public static Data read(InputStream in) throws IOException, JSONException {
        if (in == null) throw new IOException("Nie można otworzyć pliku.");
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int size;
        while ((size=in.read(buffer)) != -1) {
            if (output.size()+size > MAX_BYTES) throw new IOException("Kopia przekracza limit 5 MB.");
            output.write(buffer,0,size);
        }
        return parse(new String(output.toByteArray(), StandardCharsets.UTF_8));
    }
    private static long number(JSONObject object, String key) throws JSONException {
        Object value=object.get(key);
        if (!(value instanceof Number) || value instanceof Double || value instanceof Float) throw new JSONException("Nieprawidłowe pole: "+key);
        return ((Number)value).longValue();
    }
    private static String day(JSONObject object, String key) throws JSONException {
        Object value=object.get(key);
        if (!(value instanceof String)) throw new JSONException("Nieprawidłowa data.");
        String text=(String)value;
        try { if (!LocalDate.parse(text).toString().equals(text)) throw new IllegalArgumentException(); }
        catch (RuntimeException e) { throw new JSONException("Nieprawidłowa data."); }
        return text;
    }
    public static Data parse(String text) throws JSONException {
        if (text.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) throw new JSONException("Kopia przekracza limit 5 MB.");
        JSONObject root = new JSONObject(text);
        long version=number(root,"version");
        if (!FORMAT.equals(root.optString("format")) || (version!=1 && version!=2)) throw new JSONException("To nie jest obsługiwana kopia Spokojniej.");
        long started=number(root,"startedAt");
        if (started < 0) throw new JSONException("Nieprawidłowa data rozpoczęcia.");
        JSONArray events=root.getJSONArray("events"), gratitude=root.getJSONArray("gratitude");
        if (events.length() > 50000 || gratitude.length() > 10000) throw new JSONException("Kopia zawiera zbyt wiele wpisów.");
        Set<String> calmDays=new HashSet<>(), gratitudeDays=new HashSet<>();
        for (int i=0; i<events.length(); i++) {
            JSONObject event=events.getJSONObject(i); long at=number(event,"at"); String kind=event.getString("kind");
            if (at < 0 || !("conflict".equals(kind) || "calm".equals(kind))) throw new JSONException("Nieprawidłowy wpis historii.");
            if ("calm".equals(kind)) { if (!calmDays.add(day(event,"day"))) throw new JSONException("Powtórzony spokojny dzień w kopii."); }
            else if (!event.has("day") || !event.isNull("day")) throw new JSONException("Nieprawidłowy wpis kłótni.");
        }
        for (int i=0; i<gratitude.length(); i++) {
            JSONObject g=gratitude.getJSONObject(i); String date=day(g,"day"); Object note=g.get("note");
            if (!gratitudeDays.add(date) || !(note instanceof String) || ((String)note).trim().isEmpty() || ((String)note).length()>2000 || number(g,"updatedAt")<0) throw new JSONException("Nieprawidłowy wpis wdzięczności.");
        }
        JSONArray marks=version==1 ? new JSONArray() : root.getJSONArray("marks");
        if (marks.length()>40000) throw new JSONException("Kopia zawiera zbyt wiele zapisów dnia.");
        Set<String> identities=new HashSet<>();
        for (int i=0;i<marks.length();i++) {
            JSONObject m=marks.getJSONObject(i); String date=day(m,"day"); Object kind=m.get("kind");
            if (!(kind instanceof String) || Store.markIndex((String)kind)<0 || number(m,"updatedAt")<0 || !identities.add(date+"|"+kind)) throw new JSONException("Nieprawidłowy zapis dnia.");
        }
        JSONObject settings=root.getJSONObject("settings");
        for (String key : new String[]{"morning", "evening"}) {
            if (!(settings.get(key) instanceof Boolean) || number(settings,key+"Hour")<0 || number(settings,key+"Hour")>23 || number(settings,key+"Minute")<0 || number(settings,key+"Minute")>59) throw new JSONException("Nieprawidłowe ustawienia przypomnień.");
        }
        return new Data(events,gratitude,marks,started,settings);
    }
    public static Result restore(Context c, Store store, Data data) throws JSONException {
        SQLiteDatabase db=store.getWritableDatabase(); Result result=new Result();
        // Validate the entire document before the first write, including caller-created data.
        Data validated=parse(new JSONObject().put("format",FORMAT).put("version",2).put("startedAt",data.started).put("events",data.events).put("gratitude",data.gratitude).put("marks",data.marks).put("settings",data.settings).toString());
        boolean initiallyEmpty=store.history().isEmpty() && store.gratitudeHistory().isEmpty() && store.markHistory().isEmpty();
        db.beginTransaction();
        try {
            Map<String,Integer> occurrences=new HashMap<>();
            for (int i=0;i<validated.events.length();i++) {
                JSONObject e=validated.events.getJSONObject(i); long at=e.getLong("at"); String kind=e.getString("kind");
                String identity=kind+"|"+at; int occurrence=occurrences.getOrDefault(identity,0)+1; occurrences.put(identity,occurrence);
                // Preserve two intentionally recorded events in the same minute while
                // avoiding multiplication when the same backup is imported again.
                try (Cursor cursor=db.rawQuery("SELECT COUNT(*) FROM events WHERE happened_at=? AND kind=?",new String[]{String.valueOf(at),kind})) { cursor.moveToFirst(); if (cursor.getInt(0)>=occurrence) continue; }
                ContentValues v=new ContentValues(); v.put("happened_at",at); v.put("kind",kind); if ("calm".equals(kind)) v.put("day_key",e.getString("day"));
                if (db.insertWithOnConflict("events",null,v,SQLiteDatabase.CONFLICT_IGNORE)>=0) result.events++;
            }
            for (int i=0;i<validated.gratitude.length();i++) {
                JSONObject g=validated.gratitude.getJSONObject(i); String date=g.getString("day");
                String existing=store.gratitude(date);
                if (!existing.isEmpty()) { if (!existing.equals(g.getString("note"))) result.keptGratitude++; continue; }
                ContentValues v=new ContentValues(); v.put("day",date); v.put("note",g.getString("note")); v.put("updated_at",g.getLong("updatedAt"));
                db.insertOrThrow("gratitude",null,v); result.gratitude++;
            }
            for (int i=0;i<validated.marks.length();i++) {
                JSONObject m=validated.marks.getJSONObject(i); ContentValues v=new ContentValues();
                v.put("day",m.getString("day")); v.put("kind",m.getString("kind")); v.put("updated_at",m.getLong("updatedAt"));
                if (db.insertWithOnConflict("marks",null,v,SQLiteDatabase.CONFLICT_IGNORE)>=0) result.marks++;
            }
            db.execSQL("UPDATE meta SET value=MIN(value,?) WHERE name='started'",new Object[]{validated.started});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        if (initiallyEmpty) {
            SharedPreferences.Editor editor=Store.prefs(c).edit();
            for (String key : new String[]{"morning", "evening"}) { editor.putBoolean(key,validated.settings.getBoolean(key)); editor.putInt(key+"Hour",validated.settings.getInt(key+"Hour")); editor.putInt(key+"Minute",validated.settings.getInt(key+"Minute")); }
            result.settingsRestored=editor.commit();
        }
        return result;
    }
}
