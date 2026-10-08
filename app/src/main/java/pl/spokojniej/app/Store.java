package pl.spokojniej.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.time.*;
import java.util.*;

public final class Store extends SQLiteOpenHelper {
    public static final class Entry {
        public final long id, time;
        public final String kind;
        Entry(long id, long time, String kind) { this.id = id; this.time = time; this.kind = kind; }
    }
    public static final class Gratitude {
        public final String day, note;
        public final long updated;
        Gratitude(String day, String note, long updated) { this.day = day; this.note = note; this.updated = updated; }
    }
    public static final String[] MARK_KINDS = {"pause", "expression", "repair", "difficult"};
    public static final String[] MARK_LABELS = {"Zrobiłem pauzę", "Powiedziałem swoje spokojnie", "Wróciłem do rozmowy spokojniej", "Było mi trudno"};
    public static final class Mark {
        public final String day, kind;
        public final long updated;
        Mark(String day, String kind, long updated) { this.day=day; this.kind=kind; this.updated=updated; }
    }
    public Store(Context c) { super(c.getApplicationContext(), "spokojniej.db", null, 3); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, happened_at INTEGER NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('conflict','calm')), day_key TEXT, UNIQUE(kind,day_key))");
        db.execSQL("CREATE INDEX events_time ON events(happened_at DESC)");
        db.execSQL("CREATE TABLE meta (name TEXT PRIMARY KEY, value INTEGER NOT NULL)");
        createGratitude(db);
        createMarks(db);
        ContentValues v = new ContentValues(); v.put("name", "started"); v.put("value", System.currentTimeMillis()); db.insertOrThrow("meta", null, v);
    }
    private void createGratitude(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE gratitude (day TEXT PRIMARY KEY, note TEXT NOT NULL, updated_at INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int old, int next) {
        if (old < 1 || next > 3) throw new IllegalStateException("Unknown schema upgrade");
        if (old < 2) createGratitude(db);
        if (old < 3) createMarks(db);
    }
    private void createMarks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE marks (day TEXT NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('pause','expression','repair','difficult')), updated_at INTEGER NOT NULL, PRIMARY KEY(day,kind))");
    }
    public static int markIndex(String kind) { return Arrays.asList(MARK_KINDS).indexOf(kind); }
    public boolean[] marks(String day) {
        boolean[] values=new boolean[MARK_KINDS.length];
        try (Cursor c=getReadableDatabase().rawQuery("SELECT kind FROM marks WHERE day=?",new String[]{day})) {
            while (c.moveToNext()) { int i=markIndex(c.getString(0)); if (i>=0) values[i]=true; }
        }
        return values;
    }
    public void saveMarks(String day, boolean[] values) {
        if (!LocalDate.parse(day).toString().equals(day) || values.length!=MARK_KINDS.length) throw new IllegalArgumentException("Nieprawidłowy zapis dnia.");
        SQLiteDatabase sql=getWritableDatabase(); sql.beginTransaction();
        try {
            for (int i=0;i<values.length;i++) {
                if (!values[i]) sql.delete("marks","day=? AND kind=?",new String[]{day,MARK_KINDS[i]});
                else {
                    ContentValues v=new ContentValues(); v.put("day",day); v.put("kind",MARK_KINDS[i]); v.put("updated_at",System.currentTimeMillis());
                    sql.insertWithOnConflict("marks",null,v,SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            sql.setTransactionSuccessful();
        } finally { sql.endTransaction(); }
    }
    public List<Mark> markHistory() {
        List<Mark> result=new ArrayList<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT day,kind,updated_at FROM marks ORDER BY day DESC,kind",null)) {
            while (c.moveToNext()) result.add(new Mark(c.getString(0),c.getString(1),c.getLong(2)));
        }
        return result;
    }
    public static String dayKey(long now) { return Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString(); }
    public String gratitude(String day) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT note FROM gratitude WHERE day=?", new String[]{day})) { return c.moveToFirst() ? c.getString(0) : ""; }
    }
    public void saveGratitude(String day, String note) {
        LocalDate.parse(day);
        String clean = note.trim();
        if (clean.length() > 2000) throw new IllegalArgumentException("Wpis może mieć najwyżej 2000 znaków.");
        if (clean.isEmpty()) { getWritableDatabase().delete("gratitude", "day=?", new String[]{day}); return; }
        ContentValues v = new ContentValues(); v.put("day", day); v.put("note", clean); v.put("updated_at", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("gratitude", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }
    public List<Gratitude> gratitudeHistory() {
        List<Gratitude> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT day,note,updated_at FROM gratitude ORDER BY day DESC", null)) {
            while (c.moveToNext()) result.add(new Gratitude(c.getString(0), c.getString(1), c.getLong(2)));
        }
        return result;
    }
    public long started() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT value FROM meta WHERE name='started'", null)) { c.moveToFirst(); return c.getLong(0); }
    }
    public long anchor() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MAX(happened_at) FROM events WHERE kind='conflict'", null)) {
            c.moveToFirst(); return c.isNull(0) ? started() : c.getLong(0);
        }
    }
    public long addConflict(long time) {
        if (time > System.currentTimeMillis()) throw new IllegalArgumentException("Nie można zapisać przyszłej godziny.");
        ContentValues v = new ContentValues(); v.put("happened_at", time); v.put("kind", "conflict");
        return getWritableDatabase().insertOrThrow("events", null, v);
    }
    public long checkIn(long now) {
        // A recorded conflict always takes precedence over a calm check-in.
        if (todayStatus(now) == 2) return -1;
        ContentValues v = new ContentValues(); v.put("happened_at", now); v.put("kind", "calm");
        v.put("day_key", Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().toString());
        return getWritableDatabase().insertWithOnConflict("events", null, v, SQLiteDatabase.CONFLICT_IGNORE);
    }
    /** 0 = not checked in, 1 = calm check-in, 2 = conflict. */
    public int todayStatus(long now) {
        long start = Streaks.startOfDay(now, ZoneId.systemDefault());
        long end = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT kind FROM events WHERE happened_at>=? AND happened_at<?", new String[]{String.valueOf(start), String.valueOf(end)})) {
            boolean calm = false;
            while (c.moveToNext()) { if ("conflict".equals(c.getString(0))) return 2; calm = true; }
            return calm ? 1 : 0;
        }
    }
    public void delete(long id) { getWritableDatabase().delete("events", "id=?", new String[]{String.valueOf(id)}); }
    public void editConflict(long id, long time) {
        if (time > System.currentTimeMillis()) throw new IllegalArgumentException("Nie można zapisać przyszłej godziny.");
        ContentValues v = new ContentValues(); v.put("happened_at", time);
        getWritableDatabase().update("events", v, "id=? AND kind='conflict'", new String[]{String.valueOf(id)});
    }
    public List<Entry> history() {
        List<Entry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,happened_at,kind FROM events ORDER BY happened_at DESC,id DESC", null)) {
            while (c.moveToNext()) result.add(new Entry(c.getLong(0), c.getLong(1), c.getString(2)));
        }
        return result;
    }
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }
}
