package pl.spokojniej.app;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.view.accessibility.AccessibilityEvent;
import android.widget.*;
import android.text.InputFilter;
import android.text.InputType;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(246,245,239), INK = Color.rgb(36,60,51), GREEN = Color.rgb(48,94,80), MUTED = Color.rgb(84,99,89), SOFT = Color.rgb(230,235,223);
    private static final Locale PL = new Locale("pl", "PL");
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Store db;
    private LinearLayout body;
    private String screen = "home";
    private long lastAdded = -1;
    private Dialog pauseDialog;
    private boolean reopenPause;
    private boolean backupBusy;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if ("home".equals(screen)) showHome(false); handler.postDelayed(this, 60000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Store(this); db.getReadableDatabase();
        if (state != null) { lastAdded = state.getLong("lastAdded", -1); screen = state.getString("screen", "home"); reopenPause=state.getBoolean("pauseOpen",false); }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
            if (!"home".equals(screen)) showHome(false); else finish();
        });
        if ("settings".equals(screen)) showSettings(); else if ("history".equals(screen)) showHistory(); else showHome(true);
        handleIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent); }
    private void handleIntent(Intent intent) {
        if ("conflict".equals(intent.getAction())) { intent.setAction(null); showHome(false); editConflict(-1, System.currentTimeMillis()); }
        else if ("home".equals(intent.getAction())) { intent.setAction(null); showHome(false); }
        else if ("pause".equals(intent.getAction()) || "pause_done".equals(intent.getAction())) { boolean start="pause".equals(intent.getAction()); intent.setAction(null); showHome(false); showPause(start); }
    }
    @Override protected void onResume() {
        super.onResume();
        if ("home".equals(screen)) showHome(false); else if ("settings".equals(screen)) showSettings();
        CalmWidget.updateAll(this); ReminderReceiver.schedule(this);
        if (reopenPause) { reopenPause=false; showPause(false); }
        handler.removeCallbacks(refresh); handler.postDelayed(refresh, 60000);
    }
    @Override protected void onPause() {
        super.onPause(); handler.removeCallbacks(refresh);
        if (pauseDialog != null) { reopenPause=true; pauseDialog.dismiss(); }
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); db.close(); super.onDestroy(); }
    @Override public void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putLong("lastAdded", lastAdded); state.putString("screen", screen); state.putBoolean("pauseOpen",pauseDialog!=null || reopenPause); }
    @Override public void onBackPressed() { if (!"home".equals(screen)) showHome(false); else super.onBackPressed(); }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int stroke) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(18)); if (stroke != 0) g.setStroke(dp(1), stroke); return g;
    }
    private LinearLayout column() { LinearLayout v = new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); return v; }
    private TextView text(String value, float size, int color) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(size); t.setTextColor(color); t.setFontFeatureSettings("tnum"); t.setLineSpacing(dp(2), 1); return t;
    }
    private void add(View v, int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2); p.topMargin = dp(top); body.addView(v, p);
    }
    private void line() { View v = new View(this); v.setBackgroundColor(Color.rgb(214,220,209)); LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1)); p.topMargin = dp(24); body.addView(v, p); }
    private Button button(String label, boolean primary, View.OnClickListener click) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(16); b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setTextColor(primary ? BG : GREEN); b.setMinHeight(dp(56)); b.setMinimumHeight(dp(56)); b.setPadding(dp(16), dp(10), dp(16), dp(10)); b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22305E50), shape(primary ? GREEN : SOFT, 0), null));
        b.setOnClickListener(click); return b;
    }
    private void setup(String next, String title) {
        screen = next;
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        body = column(); body.setPadding(dp(24), dp(14), dp(24), dp(28)); scroll.addView(body);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        setContentView(scroll); scroll.requestApplyInsets();
        if (!"home".equals(next)) {
            Button back = button("‹  Wróć", false, v -> showHome(false));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(48)); body.addView(back, p);
            TextView heading = text(title, 30, INK); heading.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); add(heading, 24);
        }
    }
    private String format(long timestamp, String pattern) { return DateTimeFormatter.ofPattern(pattern, PL).format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())); }
    private void showHome(boolean animate) {
        setup("home", "Spokojniej");
        TextView date = text(format(System.currentTimeMillis(), "EEEE, d MMMM"), 13, MUTED); add(date, 2);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("Spokojniej", 32, INK); brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        header.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = button("Ustawienia", false, v -> showSettings()); settings.setTextSize(12); settings.setMinHeight(dp(48)); settings.setMinimumHeight(dp(48)); settings.setPadding(dp(10), 0, dp(10), 0);
        header.addView(settings, new LinearLayout.LayoutParams(-2, dp(48))); add(header, 4);
        long now = System.currentTimeMillis();
        TextView intention=text("Mogę mieć własne zdanie i dać sobie chwilę, zanim odpowiem.",19,INK); add(intention,26);
        Button pause=button(Pause.active(this) ? "Przerwa do " + Pause.returnTime(this) : "Potrzebuję chwili",true,v -> showPause(true)); add(pause,18);
        add(text("Nie musisz niczego zapisywać w chwili napięcia. Możesz wrócić później.",13,MUTED),10);
        line();
        TextView gratitudeTitle = text("Za co jestem dziś wdzięczny?", 19, INK); gratitudeTitle.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL)); add(gratitudeTitle, 22);
        String gratitudeDay = Store.dayKey(now), gratitudeNote = db.gratitude(gratitudeDay);
        TextView gratitudePreview=text(gratitudeNote.isEmpty() ? "Jedna drobna rzecz. Możesz też pominąć ten dzień." : gratitudeNote,15,MUTED);
        gratitudePreview.setMaxLines(3); gratitudePreview.setEllipsize(android.text.TextUtils.TruncateAt.END); add(gratitudePreview,6);
        add(button(gratitudeNote.isEmpty() ? "Zapisz wdzięczność" : "Edytuj wdzięczność", false, v -> editGratitude(gratitudeDay)), 14);
        TextView steps=text("Moje małe kroki",19,INK); steps.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); add(steps,26);
        String summary=markSummary(gratitudeDay);
        add(text(summary.isEmpty() ? "Pauza, spokojne zdanie albo powrót do rozmowy. Możesz zauważyć to także po trudnym dniu." : summary,14,MUTED),6);
        add(button(summary.isEmpty() ? "Zapisz mały krok" : "Edytuj zapis dnia",false,v -> editMarks(gratitudeDay)),12);
        add(button("Historia wpisów  ›",false,v -> showHistory()),18);
        line();
        int status=db.todayStatus(now);
        add(text("Zapis dnia · opcjonalnie",19,INK),22);
        add(text(status==2 ? "Dziś zapisałeś trudną rozmowę. Twoje małe kroki i wdzięczność zostają." : status==1 ? "Spokojny dzień zapisany." : "Możesz zapisać dzień teraz albo później.",14,MUTED),6);
        add(button(status==1 ? "✓  Dziś spokojnie — zapisano" : "Dziś spokojnie",false,v -> checkIn()),12);
        add(button("Zapisz kłótnię",false,v -> editConflict(-1,System.currentTimeMillis())),8);
        if (lastAdded>=0) add(button("Cofnij ostatni wpis",false,v -> { db.delete(lastAdded); lastAdded=-1; changed(); toast("Ostatni wpis cofnięty."); }),8);
        long anchor=db.anchor(), days=Streaks.fullDays(anchor,now);
        add(text(days + " " + Streaks.dayWord(days) + " bez kłótni",22,GREEN),24);
        add(text("Od " + format(anchor,"d MMM, HH:mm") + " · pełne 24 godziny",12,MUTED),4);
        add(text("Ten licznik jest tylko częścią historii. Trudna rozmowa nie usuwa dobrych momentów.",13,MUTED),8);
        TextView privacy=text("Tylko w Twoim telefonie. Bez konta i internetu.",12,MUTED); privacy.setGravity(Gravity.CENTER); add(privacy,24);
        if (animate) { intention.setAlpha(0); intention.setTranslationY(dp(6)); intention.animate().alpha(1).translationY(0).setDuration(250).start(); }
    }
    private String markSummary(String day) {
        boolean[] values=db.marks(day); StringBuilder summary=new StringBuilder();
        for (int i=0;i<values.length;i++) if (values[i]) { if (summary.length()>0) summary.append("\n"); summary.append(Store.MARK_LABELS[i]); }
        return summary.toString();
    }
    private void editMarks(String day) {
        boolean[] selected=db.marks(day);
        new AlertDialog.Builder(this).setTitle("Mój dzień · " + formatDay(day))
            .setMultiChoiceItems(Store.MARK_LABELS,selected,(dialog,which,checked) -> selected[which]=checked)
            .setNegativeButton("Anuluj",null).setNeutralButton("Inny dzień",(d,w) -> {
                LocalDate current=LocalDate.parse(day);
                DatePickerDialog picker=new DatePickerDialog(this,(view,y,m,date) -> editMarks(LocalDate.of(y,m+1,date).toString()),current.getYear(),current.getMonthValue()-1,current.getDayOfMonth());
                picker.getDatePicker().setMaxDate(System.currentTimeMillis()); picker.show();
            }).setPositiveButton("Zapisz",(d,w) -> { db.saveMarks(day,selected); changed(); toast("Zapis dnia zachowany."); }).show();
    }

    private void checkIn() {
        if (db.todayStatus(System.currentTimeMillis()) == 2) {
            new AlertDialog.Builder(this).setTitle("Dziś był trudny moment")
                .setMessage("Wpis o kłótni zostaje w historii. Możesz zadbać o spokój w następnej rozmowie. Jeśli wpis był pomyłką, usuń go w historii.")
                .setPositiveButton("Rozumiem", null).setNeutralButton("Otwórz historię", (d,w) -> showHistory()).show(); return;
        }
        long id = db.checkIn(System.currentTimeMillis()); if (id >= 0) lastAdded = id;
        changed(); toast(id >= 0 ? "Spokój na dziś zapisany." : "Ten dzień jest już zapisany.");
    }
    private void changed() { CalmWidget.updateAll(this); ReminderReceiver.schedule(this); if ("history".equals(screen)) showHistory(); else showHome(false); }
    private void toast(String value) { Toast.makeText(this, value, Toast.LENGTH_SHORT).show(); }
    private String formatDay(String day) { return LocalDate.parse(day).format(DateTimeFormatter.ofPattern("d MMMM yyyy", PL)); }
    private void editGratitude(String day) {
        LinearLayout box = column(); box.setPadding(dp(24), dp(8), dp(24), dp(12));
        box.addView(text(formatDay(day) + "\nMożesz docenić coś dobrego także po trudnej rozmowie.", 14, MUTED));
        EditText input = new EditText(this); input.setText(db.gratitude(day)); input.setTextSize(16); input.setTextColor(INK);
        input.setHint("Np. za wspólny spacer, dobrą rozmowę, chwilę ciszy…"); input.setMinLines(3); input.setMaxLines(6); input.setGravity(Gravity.TOP);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)}); input.setContentDescription("Za co jestem wdzięczny — treść wpisu");
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1,-2); p.topMargin=dp(12); box.addView(input,p);
        ScrollView scroll = new ScrollView(this); scroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("Wdzięczność").setView(scroll).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz", (d,w) -> {
            db.saveGratitude(day,input.getText().toString()); changed(); toast(input.getText().toString().trim().isEmpty() ? "Wpis wdzięczności usunięty." : "Wdzięczność zapisana.");
        }).create();
        dialog.show();
    }
    private void editConflict(long id, long initial) {
        Calendar selected = Calendar.getInstance(); selected.setTimeInMillis(initial); selected.set(Calendar.SECOND, 0); selected.set(Calendar.MILLISECOND, 0);
        LinearLayout box = column(); box.setPadding(dp(24), dp(12), dp(24), dp(12));
        TextView detail = text("Wystarczy data i godzina. Bez opisywania ani oceniania.", 15, MUTED); box.addView(detail);
        Button date = button(format(selected.getTimeInMillis(), "d MMMM yyyy"), false, null);
        Button time = button(format(selected.getTimeInMillis(), "HH:mm"), false, null);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, -2); gap.topMargin = dp(16); box.addView(date, gap);
        LinearLayout.LayoutParams smallGap = new LinearLayout.LayoutParams(-1, -2); smallGap.topMargin = dp(8); box.addView(time, smallGap);
        date.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(this, (p,y,m,d) -> {
                selected.set(Calendar.YEAR,y); selected.set(Calendar.MONTH,m); selected.set(Calendar.DAY_OF_MONTH,d); date.setText(format(selected.getTimeInMillis(), "d MMMM yyyy"));
            }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMaxDate(System.currentTimeMillis()); picker.show();
        });
        time.setOnClickListener(v -> new TimePickerDialog(this, (p,h,m) -> { selected.set(Calendar.HOUR_OF_DAY,h); selected.set(Calendar.MINUTE,m); time.setText(format(selected.getTimeInMillis(), "HH:mm")); }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), true).show());
        ScrollView editorScroll = new ScrollView(this); editorScroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(id < 0 ? "Zapisz kłótnię" : "Popraw datę i godzinę").setView(editorScroll)
            .setNegativeButton("Anuluj", null).setPositiveButton("Zapisz", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long chosen = selected.getTimeInMillis();
            if (chosen > System.currentTimeMillis()) { toast("Wybierz godzinę, która już minęła."); return; }
            if (id < 0) lastAdded = db.addConflict(chosen); else db.editConflict(id, chosen);
            dialog.dismiss(); changed(); toast("Wpis zapisany. Twoje małe kroki zostają.");
        })); dialog.show();
    }
    private void showHistory() {
        setup("history", "Historia wpisów");
        add(text("Dobre momenty i trudniejsze chwile mogą być w tym samym dniu.", 15, MUTED), 8);
        java.util.List<Store.Entry> entries = db.history();
        TreeMap<String, java.util.List<Store.Entry>> days = new TreeMap<>(Collections.reverseOrder());
        Map<String, Store.Gratitude> notes = new HashMap<>();
        for (Store.Entry entry : entries) days.computeIfAbsent(Store.dayKey(entry.time), key -> new ArrayList<>()).add(entry);
        for (Store.Gratitude note : db.gratitudeHistory()) { notes.put(note.day,note); days.computeIfAbsent(note.day,key -> new ArrayList<>()); }
        for (Store.Mark mark : db.markHistory()) days.computeIfAbsent(mark.day,key -> new ArrayList<>());
        if (days.isEmpty()) { add(text("Jeszcze nie ma wpisów.\nMożesz zacząć od jednej rzeczy, za którą jesteś wdzięczny.", 17, MUTED), 40); return; }
        for (Map.Entry<String, java.util.List<Store.Entry>> group : days.entrySet()) {
            String day = group.getKey(); add(text(formatDay(day), 13, MUTED),24);
            String summary=markSummary(day);
            if (!summary.isEmpty()) { add(text(summary,16,INK),12); add(button("Edytuj zapis dnia",false,v -> editMarks(day)),8); }
            Store.Gratitude gratitude = notes.get(day);
            if (gratitude != null) {
                TextView title = text("Wdzięczność",17,INK); title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL)); add(title,12);
                add(text(gratitude.note,15,MUTED),4);
                add(button("Edytuj wdzięczność",false,v -> editGratitude(day)),8);
            }
            for (Store.Entry entry : group.getValue()) {
            Button row = button(("conflict".equals(entry.kind) ? "Kłótnia" : "Spokojny dzień") + "  ·  " + format(entry.time, "HH:mm") + "  ›", false, v -> {
                boolean conflict = "conflict".equals(entry.kind);
                String[] choices = conflict ? new String[]{"Popraw datę i godzinę", "Usuń wpis"} : new String[]{"Usuń wpis"};
                new AlertDialog.Builder(this).setTitle(format(entry.time, "d MMM, HH:mm")).setItems(choices, (dialog, choice) -> {
                    if (conflict && choice == 0) editConflict(entry.id, entry.time);
                    else new AlertDialog.Builder(this).setTitle("Usunąć ten wpis?").setMessage("Licznik zostanie przeliczony z pozostałych wpisów.")
                        .setNegativeButton("Zostaw", null).setPositiveButton("Usuń", (d,w) -> { db.delete(entry.id); if (lastAdded == entry.id) lastAdded = -1; changed(); }).show();
                }).show();
            }); row.setGravity(Gravity.CENTER_VERTICAL | Gravity.START); add(row, 8);
            }
        }
    }
    private void showSettings() {
        setup("settings", "Ustawienia");
        add(text("Krótkie przypomnienia", 20, INK), 28);
        add(text("Wybierz jedną porę przed trudniejszym wieczorem. Poranek też jest opcjonalny. Bez ponagleń i nadrabiania wpisów.", 15, MUTED), 8);
        reminderRow("morning", "Rano · spokojna intencja", 8, 0);
        reminderRow("evening", "Przed wieczorem · chwila dla siebie", 18, 0);
        if (!ReminderReceiver.allowed(this)) {
            add(text("Powiadomienia są wyłączone w Androidzie. Zezwól na nie, aby przypomnienia mogły dotrzeć.", 14, MUTED), 16);
            add(button("Zezwól na powiadomienia", false, v -> enableNotifications()), 10);
        } else add(button("Wyślij próbne przypomnienie", false, v -> { ReminderReceiver.notifyUser(this, true); toast("Próbne przypomnienie wysłane."); }), 16);
        add(text("Android może dostarczyć przypomnienie z opóźnieniem, szczególnie przy oszczędzaniu baterii.", 12, MUTED), 16);
        line();
        add(text("Widget na ekranie głównym", 20, INK), 22);
        add(text("Przytrzymaj wolne miejsce na ekranie telefonu → Widgety → Spokojniej. Przycisk „Potrzebuję chwili” od razu otwiera przerwę. Dolny napis otwiera Twoje wpisy.", 15, MUTED), 8);
        if (Build.VERSION.SDK_INT >= 26 && android.appwidget.AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported()) {
            add(button("Dodaj widget", false, v -> android.appwidget.AppWidgetManager.getInstance(this).requestPinAppWidget(new ComponentName(this, CalmWidget.class), null, null)), 14);
        }
        line();
        add(text("Przerwa i powrót do rozmowy",20,INK),22);
        add(text("W spokojnym momencie uzgodnij z bliskimi zdanie: „Potrzebuję chwili. Wrócę o…”. Powiedz, o której wrócisz, i wróć do rozmowy. Jeśli potrzebujesz więcej czasu, uprzedź o tym. Przycisk jest pomocą; możesz użyć tego zdania bez telefonu.",14,MUTED),8);
        line();
        add(text("Twoje dane", 20, INK), 22);
        add(text("Historia, małe kroki i wdzięczność zostają w Twoim telefonie. Przed zmianą telefonu zapisz kopię i wczytaj ją na nowym urządzeniu.", 14, MUTED), 8);
        add(button("Zapisz kopię",false,v -> createBackup()),14);
        add(button("Wczytaj kopię",false,v -> openBackup()),10);
        add(text("Sam wybierasz miejsce zapisu pliku. Wczytanie dodaje brakujące wpisy. Jeśli wdzięczność na dany dzień już jest, zachowa obecny tekst. Na nowym telefonie odtworzy też ustawienia przypomnień; nadal trzeba zezwolić na powiadomienia i dodać widget.",14,MUTED),12);
        add(text("Odinstalowanie aplikacji usuwa dane z telefonu. Zachowaj kopię poza aplikacją. Nie ma automatycznego wysyłania ani synchronizacji.",14,MUTED),12);
        add(text("Licznik mierzy pełne 24 godziny od ostatniej kłótni, a przed pierwszym wpisem — od uruchomienia aplikacji. Brak wpisu nie jest potwierdzeniem spokojnego dnia. Widget odświeża się okresowo i po zapisaniu wpisu.", 14, MUTED), 16);
        add(text("Spokojniej 1.2.0 · Android 8 lub nowszy", 12, MUTED), 24);
    }
    private void createBackup() {
        if (backupBusy) { toast("Poczekaj na zakończenie kopiowania."); return; }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_TITLE,"Spokojniej-kopia-"+Store.dayKey(System.currentTimeMillis())+".json");
        try { startActivityForResult(intent,201); } catch (ActivityNotFoundException e) { backupError("Nie znaleziono aplikacji do zapisu plików."); }
    }
    private void openBackup() {
        if (backupBusy) { toast("Poczekaj na zakończenie kopiowania."); return; }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/json","text/plain","application/octet-stream"});
        try { startActivityForResult(intent,202); } catch (ActivityNotFoundException e) { backupError("Nie znaleziono aplikacji do wyboru plików."); }
    }
    private void backupError(String message) { new AlertDialog.Builder(this).setTitle("Kopia danych").setMessage(message).setPositiveButton("Rozumiem",null).show(); }
    private void backupDone(Runnable result) { handler.post(() -> { backupBusy=false; if (!isDestroyed() && !isFinishing()) result.run(); }); }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request,result,data);
        if ((request!=201 && request!=202) || result!=RESULT_OK || data==null || data.getData()==null) return;
        android.net.Uri uri=data.getData(); backupBusy=true; toast("Przygotowuję kopię…");
        new Thread(() -> {
            try {
                if (request==201) {
                    String json;
                    try (Store store=new Store(this)) { json=Backup.export(this,store); }
                    try (OutputStream out=getContentResolver().openOutputStream(uri,"wt")) {
                        if (out==null) throw new IOException("Brak dostępu do pliku."); out.write(json.getBytes(StandardCharsets.UTF_8)); out.flush();
                    }
                    backupDone(() -> new AlertDialog.Builder(this).setTitle("Kopia zapisana").setMessage("Plik zawiera historię, małe kroki, wpisy wdzięczności i ustawienia przypomnień. Przenieś go na nowy telefon i wybierz Wczytaj kopię.").setPositiveButton("Gotowe",null).show());
                } else {
                    Backup.Data backup;
                    try (InputStream in=getContentResolver().openInputStream(uri)) { backup=Backup.read(in); }
                    backupDone(() -> new AlertDialog.Builder(this).setTitle("Wczytać tę kopię?")
                        .setMessage("Wpisy historii: "+backup.eventCount()+"\nDni z wdzięcznością: "+backup.gratitudeCount()+"\nZapisy małych kroków i trudności: "+backup.markCount()+"\n\nDoda brakujące wpisy i zachowa obecne. Wdzięczność na ten sam dzień zachowa z telefonu. Ustawienia przypomnień odtworzy na telefonie bez wcześniejszych wpisów.")
                        .setNegativeButton("Anuluj",null).setPositiveButton("Wczytaj",(d,w) -> restoreBackup(backup)).show());
                }
            } catch (Exception e) { backupDone(() -> backupError(request==201 ? "Nie udało się zapisać kopii. Sprawdź wolne miejsce i spróbuj ponownie." : "Nie udało się odczytać poprawnej kopii Spokojniej. Twoje dane pozostają bez zmian.")); }
        },"spokojniej-backup").start();
    }
    private void restoreBackup(Backup.Data backup) {
        if (backupBusy) return; backupBusy=true;
        new Thread(() -> {
            try (Store store=new Store(this)) {
                Backup.Result restored=Backup.restore(this,store,backup);
                backupDone(() -> {
                    lastAdded=-1; CalmWidget.updateAll(this); ReminderReceiver.schedule(this); showSettings();
                    String message="Dodano wpisy historii: "+restored.events+"\nDodano dni z wdzięcznością: "+restored.gratitude+"\nDodano zapisy małych kroków i trudności: "+restored.marks;
                    if (restored.keptGratitude>0) message+="\nZachowano obecne teksty wdzięczności: "+restored.keptGratitude;
                    new AlertDialog.Builder(this).setTitle("Kopia wczytana").setMessage(message).setPositiveButton("Gotowe",null).show();
                });
            } catch (Exception e) { backupDone(() -> backupError("Nie udało się wczytać kopii. Wpisy pozostają bez zmian.")); }
        },"spokojniej-restore").start();
    }
    private void reminderRow(String key, String label, int defaultHour, int defaultMinute) {
        SharedPreferences prefs = Store.prefs(this);
        Switch toggle = new Switch(this); toggle.setText(label); toggle.setTextColor(INK); toggle.setTextSize(15); toggle.setMinHeight(dp(56)); toggle.setChecked(prefs.getBoolean(key, false)); add(toggle, 18);
        toggle.setOnCheckedChangeListener((b,enabled) -> { prefs.edit().putBoolean(key, enabled).apply(); ReminderReceiver.schedule(this); if (enabled && !ReminderReceiver.allowed(this)) enableNotifications(); });
        int h = prefs.getInt(key + "Hour", defaultHour), m = prefs.getInt(key + "Minute", defaultMinute);
        Button time = button("Godzina: " + String.format(PL, "%02d:%02d",h,m), false, v -> new TimePickerDialog(this, (p,hour,minute) -> {
            prefs.edit().putInt(key + "Hour", hour).putInt(key + "Minute", minute).apply(); ReminderReceiver.schedule(this); showSettings();
        }, prefs.getInt(key + "Hour", defaultHour), prefs.getInt(key + "Minute", defaultMinute), true).show()); add(time, 2);
    }
    private void enableNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED && (!Store.prefs(this).getBoolean("notificationAsked", false) || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS))) {
            Store.prefs(this).edit().putBoolean("notificationAsked", true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
        else startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] result) {
        super.onRequestPermissionsResult(request, permissions, result);
        if (request == 10) { ReminderReceiver.schedule(this); showSettings(); }
    }
    private void showPause(boolean start) {
        if (pauseDialog!=null) { pauseDialog.dismiss(); }
        if (start && !Pause.active(this)) Pause.start(this,600);
        LinearLayout box=column(); box.setPadding(dp(24),dp(8),dp(24),dp(16));
        TextView phrase=text("",19,INK); box.addView(phrase);
        TextView instruction=text("Możesz odłożyć telefon i zrobić sobie miejsce na chwilę ciszy.",14,MUTED); box.addView(instruction);
        BreathView circle=new BreathView(this); box.addView(circle,new LinearLayout.LayoutParams(-1,dp(120)));
        TextView remaining=text("",30,GREEN); remaining.setGravity(Gravity.CENTER); box.addView(remaining);
        TextView cue=text("",16,MUTED);
        cue.setText("Do wpisów możesz wrócić później."); box.addView(cue);
        ScrollView scroll=new ScrollView(this); scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Potrzebuję chwili").setView(scroll)
            .setNegativeButton("Zamknij ekran",null).setNeutralButton("30 sekund",null).setPositiveButton("Wracam",(d,w) -> { Pause.cancel(this); showHome(false); }).create();
        pauseDialog=dialog;
        Runnable tick=new Runnable() {
            long last=-1;
            @Override public void run() {
                long seconds=Math.max(0,(Pause.until(MainActivity.this)-System.currentTimeMillis()+999)/1000);
                int duration=Store.prefs(MainActivity.this).getInt("pauseSeconds",600);
                if (seconds!=last) {
                    last=seconds;
                    remaining.setText(seconds>0 ? String.format(PL,"%d:%02d",seconds/60,seconds%60) : "Chwila minęła");
                    phrase.setText(seconds==0 ? "„Mam inne zdanie. Dla mnie ważne jest…”" : duration==30 ? "„Potrzebuję chwili, zanim odpowiem.”" : "„Potrzebuję chwili. Wrócę do rozmowy o " + Pause.returnTime(MainActivity.this) + ".”");
                }
                circle.phase=1f-Math.min(1f,seconds/(float)duration); circle.invalidate();
                if (seconds>0) handler.postDelayed(this,250);
                else { Store.prefs(MainActivity.this).edit().putLong("pauseDelivered",Pause.until(MainActivity.this)).apply(); remaining.sendAccessibilityEvent(AccessibilityEvent.TYPE_ANNOUNCEMENT); }
            }
        };
        dialog.setOnDismissListener(d -> { handler.removeCallbacks(tick); if (pauseDialog==dialog) pauseDialog=null; });
        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
            Pause.start(this,Store.prefs(this).getInt("pauseSeconds",600)==30 ? 600 : 30);
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText(Store.prefs(this).getInt("pauseSeconds",600)==30 ? "10 minut" : "30 sekund");
            handler.removeCallbacks(tick); handler.post(tick);
        });
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setText(Store.prefs(this).getInt("pauseSeconds",600)==30 ? "10 minut" : "30 sekund");
        handler.post(tick);
    }
    private final class BreathView extends View {
        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        float phase;
        BreathView(Context c) { super(c); setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float scale = Settings.Global.getFloat(getContentResolver(), Settings.Global.ANIMATOR_DURATION_SCALE, 1f);
            float wave = scale == 0 ? .5f : (float)(.5 - .5 * Math.cos(phase * 10 * Math.PI));
            float radius = dp(30) + dp(10) * wave;
            paint.setColor(SOFT); canvas.drawCircle(getWidth()/2f, getHeight()/2f, radius + dp(10), paint);
            paint.setColor(GREEN); canvas.drawCircle(getWidth()/2f, getHeight()/2f, radius, paint);
        }
    }
}
