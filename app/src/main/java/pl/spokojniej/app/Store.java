package pl.spokojniej.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.time.*;
import java.util.*;

public final class Store extends SQLiteOpenHelper {
    public static final class Entry {
        public final long id, time;
        public final String kind, day;
        Entry(long id, long time, String kind,String day) { this.id=id;this.time=time;this.kind=kind;this.day=day; }
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
    public Store(Context c) { super(c.getApplicationContext(), "spokojniej.db", null, 5); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE events (id INTEGER PRIMARY KEY AUTOINCREMENT, happened_at INTEGER NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('conflict','calm')), day_key TEXT, UNIQUE(kind,day_key))");
        db.execSQL("CREATE INDEX events_time ON events(happened_at DESC)");
        db.execSQL("CREATE TABLE meta (name TEXT PRIMARY KEY, value INTEGER NOT NULL)");
        createGratitude(db);
        createMarks(db);
        createJournal(db);
        createMoments(db);
        ContentValues v = new ContentValues(); v.put("name", "started"); v.put("value", System.currentTimeMillis()); db.insertOrThrow("meta", null, v);
    }
    private void createGratitude(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE gratitude (day TEXT PRIMARY KEY, note TEXT NOT NULL, updated_at INTEGER NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int old, int next) {
        if (old < 1 || next > 5) throw new IllegalStateException("Unknown schema upgrade");
        if (old < 2) createGratitude(db);
        if (old < 3) createMarks(db);
        if (old < 4) createJournal(db);
        if (old < 5) createMoments(db);
    }
    private void createMarks(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE marks (day TEXT NOT NULL, kind TEXT NOT NULL CHECK(kind IN ('pause','expression','repair','difficult')), updated_at INTEGER NOT NULL, PRIMARY KEY(day,kind))");
    }
    public static final String[] CONTEXT_KINDS = {"work", "family", "health", "other", "fatigue", "illness", "surprise"};
    public static final String[] CONTEXT_LABELS = {"Praca", "Rodzina", "Zdrowie", "Inna rzecz", "Zmęczenie", "Choroba", "Zaskoczenie"};
    public static final class Done {
        public final String id, day, note; public final long updated;
        Done(String id, String day, String note, long updated) { this.id=id; this.day=day; this.note=note; this.updated=updated; }
    }
    private void createJournal(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE done (id TEXT PRIMARY KEY, day TEXT NOT NULL, note TEXT NOT NULL, updated_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX done_day ON done(day)");
        db.execSQL("CREATE TABLE event_contexts (event_id INTEGER NOT NULL REFERENCES events(id) ON DELETE CASCADE, kind TEXT NOT NULL CHECK(kind IN ('work','family','health','other','fatigue','illness')), PRIMARY KEY(event_id,kind))");
        db.execSQL("CREATE TRIGGER delete_contexts AFTER DELETE ON events BEGIN DELETE FROM event_contexts WHERE event_id=OLD.id; END");
    }
    public static final String[] MOMENT_KINDS = {"tension", "impulse", "conflict"};
    public static final String[] MOMENT_LABELS = {"Czułem napięcie", "Zareagowałem impulsywnie", "Rozmowa się zaostrzyła"};
    public static final String[] HELP_KINDS = {"break", "calm_response", "return", "apology"};
    public static final String[] HELP_LABELS = {"Przerwa", "Spokojna odpowiedź", "Powrót do rozmowy", "Przeprosiny"};
    public static boolean momentKind(String kind) { return Arrays.asList(MOMENT_KINDS).contains(kind); }
    public static String momentLabel(String kind) { return MOMENT_LABELS[Arrays.asList(MOMENT_KINDS).indexOf(kind)]; }
    private void createMoments(SQLiteDatabase db) {
        // Keep original event IDs and timestamps so upgrades preserve all existing relationships.
        db.execSQL("ALTER TABLE event_contexts RENAME TO old_contexts");
        db.execSQL("DROP TRIGGER delete_contexts");
        db.execSQL("CREATE TABLE event_contexts (event_id INTEGER NOT NULL REFERENCES events(id) ON DELETE CASCADE, kind TEXT NOT NULL CHECK(kind IN ('work','family','health','other','fatigue','illness','surprise')), PRIMARY KEY(event_id,kind))");
        db.execSQL("INSERT INTO event_contexts SELECT * FROM old_contexts");
        db.execSQL("DROP TABLE old_contexts");
        db.execSQL("CREATE TRIGGER delete_contexts AFTER DELETE ON events BEGIN DELETE FROM event_contexts WHERE event_id=OLD.id; END");
        db.execSQL("CREATE TABLE moment_details (event_id INTEGER PRIMARY KEY REFERENCES events(id) ON DELETE CASCADE, kind TEXT NOT NULL CHECK(kind IN ('tension','impulse','conflict')))");
        db.execSQL("CREATE TABLE event_help (event_id INTEGER NOT NULL REFERENCES events(id) ON DELETE CASCADE, kind TEXT NOT NULL CHECK(kind IN ('break','calm_response','return','apology')), PRIMARY KEY(event_id,kind))");
        db.execSQL("CREATE TRIGGER delete_moment_details AFTER DELETE ON events BEGIN DELETE FROM moment_details WHERE event_id=OLD.id; DELETE FROM event_help WHERE event_id=OLD.id; END");
    }
    public String momentKind(long id) {
        try(Cursor c=getReadableDatabase().rawQuery("SELECT kind FROM moment_details WHERE event_id=?",new String[]{""+id})) { return c.moveToFirst()?c.getString(0):"conflict"; }
    }
    public boolean[] help(long id) {
        boolean[] result=new boolean[HELP_KINDS.length];
        try(Cursor c=getReadableDatabase().rawQuery("SELECT kind FROM event_help WHERE event_id=?",new String[]{""+id})) {
            while(c.moveToNext()) {int i=Arrays.asList(HELP_KINDS).indexOf(c.getString(0));if(i>=0)result[i]=true;}
        } return result;
    }
    private void saveMomentDetails(long id,String kind,boolean[] help) {
        if(!momentKind(kind) || help.length!=HELP_KINDS.length)throw new IllegalArgumentException("Wybierz rodzaj trudnego momentu.");
        SQLiteDatabase sql=getWritableDatabase();ContentValues detail=new ContentValues();detail.put("event_id",id);detail.put("kind",kind);
        sql.insertWithOnConflict("moment_details",null,detail,SQLiteDatabase.CONFLICT_REPLACE);
        sql.delete("event_help","event_id=?",new String[]{""+id});
        for(int i=0;i<help.length;i++)if(help[i]) {ContentValues v=new ContentValues();v.put("event_id",id);v.put("kind",HELP_KINDS[i]);sql.insertOrThrow("event_help",null,v);}
    }
    public long addMoment(long time,String kind,boolean[] contexts,boolean[] help) {
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try {
            long id=addConflict(time);saveContexts(id,contexts);saveMomentDetails(id,kind,help);sql.setTransactionSuccessful();return id;
        }finally{sql.endTransaction();}
    }
    public void editMoment(long id,long time,String kind,boolean[] contexts,boolean[] help) {
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try {
            editConflict(id,time);saveContexts(id,contexts);saveMomentDetails(id,kind,help);sql.setTransactionSuccessful();
        }finally{sql.endTransaction();}
    }
    public static void validateDay(String day) {
        LocalDate date=LocalDate.parse(day);
        if (!date.toString().equals(day) || date.isBefore(LocalDate.of(1970,1,1)) || date.isAfter(LocalDate.now())) throw new IllegalArgumentException("Wybierz dzień, który już nadszedł.");
    }
    public String saveDone(String id, String day, String note) {
        validateDay(day); String clean=note.trim();
        if (clean.isEmpty() || clean.length()>1000) throw new IllegalArgumentException("Wpisz od 1 do 1000 znaków.");
        String key=id==null ? UUID.randomUUID().toString() : id;
        ContentValues v=new ContentValues(); v.put("id",key); v.put("day",day); v.put("note",clean); v.put("updated_at",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("done",null,v,SQLiteDatabase.CONFLICT_REPLACE); return key;
    }
    public void deleteDone(String id) { getWritableDatabase().delete("done","id=?",new String[]{id}); }
    public List<Done> doneHistory() {
        List<Done> result=new ArrayList<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT id,day,note,updated_at FROM done ORDER BY day DESC,updated_at,id",null)) {
            while(c.moveToNext()) result.add(new Done(c.getString(0),c.getString(1),c.getString(2),c.getLong(3)));
        } return result;
    }
    public List<Done> done(String day) {
        List<Done> result=new ArrayList<>();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT id,day,note,updated_at FROM done WHERE day=? ORDER BY updated_at,id",new String[]{day})) {
            while(c.moveToNext())result.add(new Done(c.getString(0),c.getString(1),c.getString(2),c.getLong(3)));
        }return result;
    }
    public boolean[] contexts(long id) {
        boolean[] result=new boolean[CONTEXT_KINDS.length];
        try(Cursor c=getReadableDatabase().rawQuery("SELECT kind FROM event_contexts WHERE event_id=?",new String[]{String.valueOf(id)})) {
            while(c.moveToNext()) { int i=Arrays.asList(CONTEXT_KINDS).indexOf(c.getString(0)); if(i>=0) result[i]=true; }
        } return result;
    }
    private void saveContexts(long id,boolean[] values) {
        if(values.length!=CONTEXT_KINDS.length && values.length!=6) throw new IllegalArgumentException("Nieprawidłowe okoliczności.");
        SQLiteDatabase sql=getWritableDatabase(); sql.delete("event_contexts","event_id=?",new String[]{String.valueOf(id)});
        for(int i=0;i<values.length;i++) if(values[i]) { ContentValues v=new ContentValues();v.put("event_id",id);v.put("kind",CONTEXT_KINDS[i]);sql.insertOrThrow("event_contexts",null,v); }
    }
    public long addConflict(long time,boolean[] contexts) {
        if(contexts.length!=CONTEXT_KINDS.length && contexts.length!=6) throw new IllegalArgumentException();
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try {
            long id=addConflict(time);saveContexts(id,contexts);sql.setTransactionSuccessful();return id;
        } finally { sql.endTransaction(); }
    }
    public void editConflict(long id,long time,boolean[] contexts) {
        SQLiteDatabase sql=getWritableDatabase();sql.beginTransaction();try { editConflict(id,time);saveContexts(id,contexts);sql.setTransactionSuccessful(); } finally {sql.endTransaction();}
    }
    public long checkInDay(String day) {
        validateDay(day);
        long time=LocalDate.parse(day).atTime(12,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return checkIn(Math.min(time,System.currentTimeMillis()));
    }
    public int dayStatus(String day) {
        LocalDate date=LocalDate.parse(day);long start=date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),end=date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try(Cursor c=getReadableDatabase().rawQuery("SELECT e.kind FROM events e LEFT JOIN moment_details m ON e.id=m.event_id WHERE (e.kind='conflict' AND COALESCE(m.kind,'conflict')!='tension' AND happened_at>=? AND happened_at<?) OR (e.kind='calm' AND day_key=?)",new String[]{""+start,""+end,day})) {
            boolean calm=false;while(c.moveToNext()){if("conflict".equals(c.getString(0)))return 2;calm=true;}return calm?1:0;
        }
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
        validateDay(day);
        if (values.length!=MARK_KINDS.length) throw new IllegalArgumentException("Nieprawidłowy zapis dnia.");
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
        validateDay(day);
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
        try (Cursor c = getReadableDatabase().rawQuery("SELECT MAX(happened_at) FROM events e LEFT JOIN moment_details m ON e.id=m.event_id WHERE e.kind='conflict' AND COALESCE(m.kind,'conflict')!='tension'", null)) {
            c.moveToFirst(); return c.isNull(0) ? started() : c.getLong(0);
        }
    }
    public long addConflict(long time) {
        if (time < 0 || time > System.currentTimeMillis()) throw new IllegalArgumentException("Nie można zapisać przyszłej godziny.");
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
        return dayStatus(dayKey(now));
    }
    public void removeCalm(String day) { validateDay(day);getWritableDatabase().delete("events","kind='calm' AND day_key=?",new String[]{day}); }
    public void delete(long id) { getWritableDatabase().delete("events", "id=?", new String[]{String.valueOf(id)}); }
    public void editConflict(long id, long time) {
        if (time < 0 || time > System.currentTimeMillis()) throw new IllegalArgumentException("Nie można zapisać przyszłej godziny.");
        ContentValues v = new ContentValues(); v.put("happened_at", time);
        if(getWritableDatabase().update("events",v,"id=? AND kind='conflict'",new String[]{String.valueOf(id)})!=1)throw new IllegalArgumentException("Nie ma takiego wpisu trudnego momentu.");
    }
    public List<Entry> history() {
        List<Entry> result = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT e.id,happened_at,CASE WHEN e.kind='calm' THEN 'calm' ELSE COALESCE(m.kind,'conflict') END,day_key FROM events e LEFT JOIN moment_details m ON e.id=m.event_id ORDER BY happened_at DESC,e.id DESC", null)) {
            while (c.moveToNext()) result.add(new Entry(c.getLong(0),c.getLong(1),c.getString(2),c.getString(3)));
        }
        return result;
    }
    public static SharedPreferences prefs(Context c) { return c.getSharedPreferences("settings", Context.MODE_PRIVATE); }
}
