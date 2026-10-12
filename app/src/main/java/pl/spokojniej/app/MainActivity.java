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
    private ScrollView contentScroll;
    private String screen = "home";
    private long lastAdded = -1;
    private Dialog pauseDialog;
    private String selectedDay = LocalDate.now().toString();
    private boolean backupBusy;
    private int historyLimit=40;
    private final Runnable refresh = new Runnable() {
        @Override public void run() { if ("home".equals(screen) && pauseDialog==null) refreshHome(); handler.postDelayed(this, 60000); }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        db = new Store(this); db.getReadableDatabase();
        if (state != null) { lastAdded = state.getLong("lastAdded", -1); screen = state.getString("screen", "home"); selectedDay=state.getString("day",LocalDate.now().toString()); }
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        if (Build.VERSION.SDK_INT >= 33) getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, () -> {
            if (!"home".equals(screen)) showHome(false); else finish();
        });
        if ("settings".equals(screen)) showSettings(); else if ("history".equals(screen)) showHistory(); else showHome(true);
        handleIntent(getIntent());
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); handleIntent(intent); }
    private void handleIntent(Intent intent) {
        String action=intent.getAction();
        // Consume external actions once; opening the launcher must not reopen a pause.
        intent.setAction(null);
        if(pauseDialog!=null) pauseDialog.dismiss();
        if("conflict".equals(action)) { selectedDay=LocalDate.now().toString();showHome(false);editConflict(-1,System.currentTimeMillis()); }
        else if("pause".equals(action) || "pause_start".equals(action) || "pause_done".equals(action)) {
            selectedDay=LocalDate.now().toString();showHome(false);showPause(!"pause_done".equals(action));
        } else if("home".equals(action) || Intent.ACTION_MAIN.equals(action)) {
            selectedDay=LocalDate.now().toString();showHome(false);
        }
    }
    @Override protected void onResume() {
        super.onResume();
        if ("home".equals(screen)) refreshHome(); else if ("settings".equals(screen)) showSettings();
        CalmWidget.updateAll(this); ReminderReceiver.schedule(this);
        handler.removeCallbacks(refresh); handler.postDelayed(refresh, 60000);
    }
    @Override protected void onPause() {
        super.onPause(); handler.removeCallbacks(refresh);
        if (pauseDialog != null) pauseDialog.dismiss();
    }
    @Override protected void onDestroy() { handler.removeCallbacksAndMessages(null); db.close(); super.onDestroy(); }
    @Override public void onSaveInstanceState(Bundle state) { super.onSaveInstanceState(state); state.putLong("lastAdded", lastAdded); state.putString("screen", screen); state.putString("day",selectedDay); }
    @Override public void onBackPressed() { if (!"home".equals(screen)) showHome(false); else super.onBackPressed(); }
    private int dp(float value) { return (int) (value * getResources().getDisplayMetrics().density + .5f); }
    private GradientDrawable shape(int color, int stroke) {
        GradientDrawable g = new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(12)); if (stroke != 0) g.setStroke(dp(1), stroke); return g;
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
        b.setTextColor(primary ? BG : GREEN); b.setMinHeight(dp(48)); b.setMinimumHeight(dp(48)); b.setPadding(dp(14), dp(8), dp(14), dp(8)); b.setStateListAnimator(null);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22305E50), shape(primary ? GREEN : SOFT, 0), null));
        b.setOnClickListener(click); return b;
    }
    private Button quiet(String label,View.OnClickListener click) {
        Button b=button(label,false,click);
        b.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x18305E50),shape(BG,0),null));return b;
    }
    private void section(String title,String action,View.OnClickListener click,int gap) {
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading=text(title,21,INK);heading.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));if(Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);
        row.addView(heading,new LinearLayout.LayoutParams(0,-2,1));
        if(action!=null) {Button b=quiet(action,click);b.setTextSize(14);b.setContentDescription(action+" · "+title);row.addView(b,new LinearLayout.LayoutParams(-2,-2));}
        add(row,gap);
    }
    private void setup(String next,String title) {
        screen=next;
        LinearLayout root=column();root.setBackgroundColor(BG);
        ScrollView scroll=new ScrollView(this);contentScroll=scroll;scroll.setFillViewport(true);
        body=column();body.setPadding(dp(22),dp(12),dp(22),dp(28));scroll.addView(body);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(6),dp(4),dp(6),dp(4));nav.setBackgroundColor(Color.rgb(237,240,232));
        String[] names={"Dziennik","Historia","Ustawienia"},screens={"home","history","settings"};
        for(int i=0;i<names.length;i++) {
            final int index=i;
            Button tab=quiet(names[i],v -> {if(index==0)showHome(false);else if(index==1)showHistory();else showSettings();});
            tab.setTextSize(14);tab.setPadding(dp(4),dp(8),dp(4),dp(8));
            tab.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22305E50),shape(Color.TRANSPARENT,0),null));
            if(next.equals(screens[i]))tab.setBackground(new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22305E50),shape(SOFT,0),null));
            nav.addView(tab,new LinearLayout.LayoutParams(0,-2,1));
        }
        root.addView(nav,new LinearLayout.LayoutParams(-1,-2));
        root.setOnApplyWindowInsetsListener((view,insets) -> {
            if(Build.VERSION.SDK_INT>=30) {android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());view.setPadding(bars.left,bars.top,bars.right,bars.bottom);}
            else view.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());
            return insets;
        });setContentView(root);root.requestApplyInsets();
        LinearLayout header=new LinearLayout(this);header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand=text("home".equals(next)?"Spokojniej":title,28,INK);brand.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        header.addView(brand,new LinearLayout.LayoutParams(0,-2,1));
        Button pause=button("Pauza",true,v -> showPause(true));pause.setTextSize(14);header.addView(pause,new LinearLayout.LayoutParams(-2,-2));add(header,0);
    }
    private String format(long timestamp,String pattern) { return DateTimeFormatter.ofPattern(pattern,PL).format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault())); }
    private void pickDay(String initial,java.util.function.Consumer<String> chosen) {
        LocalDate date=LocalDate.parse(initial);
        DatePickerDialog picker=new DatePickerDialog(this,(view,y,m,d) -> chosen.accept(LocalDate.of(y,m+1,d).toString()),date.getYear(),date.getMonthValue()-1,date.getDayOfMonth());
        picker.getDatePicker().setMinDate(0);picker.getDatePicker().setMaxDate(System.currentTimeMillis());picker.show();
    }
    private void openDay(String day) {selectedDay=day;showHome(false);}
    private long conflictTimeForDay() {
        LocalDate day=LocalDate.parse(selectedDay);
        return day.equals(LocalDate.now())?System.currentTimeMillis():day.atTime(18,0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }
    private void refreshHome() {
        int offset=contentScroll==null?0:contentScroll.getScrollY();showHome(false);
        ScrollView refreshed=contentScroll;
        refreshed.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View view,int left,int top,int right,int bottom,int oldLeft,int oldTop,int oldRight,int oldBottom) {
                refreshed.removeOnLayoutChangeListener(this);refreshed.scrollTo(0,offset);
            }
        });
    }
    private void showHome(boolean animate) {
        if(LocalDate.parse(selectedDay).isAfter(LocalDate.now()))selectedDay=LocalDate.now().toString();
        setup("home","Spokojniej");
        add(text("Miejsce na to, co już się udało.",14,MUTED),6);
        LinearLayout dates=new LinearLayout(this);dates.setGravity(Gravity.CENTER_VERTICAL);
        Button prev=quiet("‹",v -> openDay(LocalDate.parse(selectedDay).minusDays(1).toString()));prev.setTextSize(26);prev.setContentDescription("Poprzedni dzień");dates.addView(prev,new LinearLayout.LayoutParams(dp(48),dp(56)));
        LocalDate date=LocalDate.parse(selectedDay),today=LocalDate.now();
        String relative=date.equals(today)?"Dzisiaj":date.equals(today.minusDays(1))?"Wczoraj":date.format(DateTimeFormatter.ofPattern("EEEE",PL));
        Button dateButton=quiet(relative+" · "+date.format(DateTimeFormatter.ofPattern(date.getYear()==today.getYear()?"d MMM":"d MMM yyyy",PL))+"  ▾",v -> pickDay(selectedDay,this::openDay));dateButton.setTextSize(18);dateButton.setContentDescription("Wybierz dzień: "+formatDay(selectedDay));dates.addView(dateButton,new LinearLayout.LayoutParams(0,-2,1));
        Button next=quiet("›",v -> openDay(LocalDate.parse(selectedDay).plusDays(1).toString()));next.setTextSize(26);next.setContentDescription("Następny dzień");next.setEnabled(date.isBefore(today));next.setAlpha(date.isBefore(today)?1f:.3f);dates.addView(next,new LinearLayout.LayoutParams(dp(48),dp(56)));add(dates,20);
        if(!date.equals(today))add(quiet("Wróć do dzisiaj",v -> openDay(LocalDate.now().toString())),0);
        int status=db.dayStatus(selectedDay);
        LinearLayout state=new LinearLayout(this);
        Button calm=button(status==1?"✓ Spokojny dzień":"Spokojny dzień",status==1,v -> checkIn());
        Button conversation=quiet("Zapisz kłótnię",v -> editConflict(-1,conflictTimeForDay()));conversation.setTextSize(14);calm.setTextSize(14);
        state.addView(calm,new LinearLayout.LayoutParams(0,-2,1));state.addView(conversation,new LinearLayout.LayoutParams(0,-2,1));add(state,10);
        add(text(status==2?"Trudna rozmowa jest zapisana. Dobre momenty też mają tu miejsce.":status==1?"Spokój zapisany. Możesz uzupełnić resztę, jeśli chcesz.":"Zapis dnia jest opcjonalny. Możesz wrócić później.",13,MUTED),8);
        if(Pause.active(this)) add(quiet("Trwa pauza do "+Pause.returnTime(this)+" · pokaż odliczanie",v -> showPause(false)),12);
        line();
        section("Zrobione","+ Dodaj",v -> editDone(null,selectedDay),14);
        java.util.List<Store.Done> done=db.done(selectedDay);
        if(done.isEmpty()) add(text("Prysznic, kawa, spacer, odpoczynek…\nDrobne rzeczy też się liczą. Nie musisz nic nadrabiać.",14,MUTED),4);
        else for(Store.Done item:done) {
            Button row=quiet("✓  "+item.note,v -> doneActions(item));row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.setTextColor(INK);row.setTextSize(16);add(row,2);
        }
        line();
        String gratitude=db.gratitude(selectedDay);
        section("Wdzięczność",gratitude.isEmpty()?"+ Dodaj":"Edytuj",v -> editGratitude(selectedDay),14);
        add(text(gratitude.isEmpty()?"Jedna dobra rzecz z tego dnia. Możesz pominąć.":gratitude,15,gratitude.isEmpty()?MUTED:INK),4);
        String summary=markSummary(selectedDay);
        section("Małe kroki",summary.isEmpty()?"+ Dodaj":"Edytuj",v -> editMarks(selectedDay),22);
        add(text(summary.isEmpty()?"Pauza albo spokojnie wyrażone zdanie. Bez ocen.":summary,14,MUTED),4);
        java.util.List<Store.Entry> conversations=new ArrayList<>();
        for(Store.Entry entry:db.history()) if("conflict".equals(entry.kind)&&Store.dayKey(entry.time).equals(selectedDay)) conversations.add(entry);
        if(!conversations.isEmpty()) {
            line();section("Trudne rozmowy",null,null,14);
            for(Store.Entry entry:conversations) {
                String contexts=contextSummary(entry.id);
                Button row=quiet(format(entry.time,"HH:mm")+" · Kłótnia"+(contexts.isEmpty()?"":"\n"+contexts)+"  ›",v -> entryActions(entry));row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.setTextSize(15);add(row,4);
            }
        }
        if(status==1) add(quiet("Cofnij zapis spokojnego dnia",v -> new AlertDialog.Builder(this).setTitle("Cofnąć zapis spokoju?").setMessage("Pozostałe wpisy tego dnia zostaną.").setNegativeButton("Zostaw",null).setPositiveButton("Cofnij",(d,w) -> {db.removeCalm(selectedDay);changed();}).show()),20);
        long now=System.currentTimeMillis(),days=Streaks.fullDays(db.anchor(),now);
        line();add(text(days+" "+Streaks.dayWord(days)+" bez kłótni",16,GREEN),20);
        add(text("Licznik pełnych 24 godzin. Twoje dobre momenty zostają po trudniejszej rozmowie.",12,MUTED),4);
    }
    private String contextSummary(long id) {
        boolean[] values=db.contexts(id);java.util.List<String> labels=new ArrayList<>();for(int i=0;i<values.length;i++)if(values[i])labels.add(Store.CONTEXT_LABELS[i]);return String.join(" · ",labels);
    }
    private void editDone(Store.Done item,String day) {
        LinearLayout box=column();box.setPadding(dp(24),dp(8),dp(24),dp(12));box.addView(text(formatDay(day)+"\nZapisz to, co już zrobiłeś. Może być drobne.",14,MUTED));
        EditText input=new EditText(this);input.setText(item==null?"":item.note);input.setHint("Np. zrobiłem kawę");input.setTextSize(16);input.setMinLines(2);input.setMaxLines(5);input.setGravity(Gravity.TOP);
        input.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);input.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1000)});input.setContentDescription("Treść zrobionej rzeczy");box.addView(input,new LinearLayout.LayoutParams(-1,-2));
        TextView error=text("Wpisz jedną zrobioną rzecz.",14,INK);error.setVisibility(View.GONE);box.addView(error);
        ScrollView scroll=new ScrollView(this);scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(item==null?"Już zrobione":"Popraw wpis").setView(scroll).setNegativeButton("Anuluj",null).setPositiveButton("Zapisz",null).create();dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {if(input.getText().toString().trim().isEmpty()){error.setVisibility(View.VISIBLE);input.requestFocus();input.setError("Wpisz jedną zrobioną rzecz.");return;}db.saveDone(item==null?null:item.id,day,input.getText().toString());dialog.dismiss();changed();});
    }
    private void doneActions(Store.Done item) {
        new AlertDialog.Builder(this).setTitle("Zrobione").setItems(new String[]{"Popraw wpis","Usuń wpis"},(d,w) -> {
            if(w==0)editDone(item,item.day);else new AlertDialog.Builder(this).setTitle("Usunąć ten wpis?").setMessage(item.note).setNegativeButton("Zostaw",null).setPositiveButton("Usuń",(dialog,which) -> {db.deleteDone(item.id);changed();}).show();
        }).show();
    }
    private void entryActions(Store.Entry entry) {
        new AlertDialog.Builder(this).setTitle(format(entry.time,"d MMM, HH:mm")).setItems(new String[]{"Popraw rozmowę","Usuń wpis"},(d,w) -> {
            if(w==0)editConflict(entry.id,entry.time);else new AlertDialog.Builder(this).setTitle("Usunąć ten wpis?").setMessage("Licznik zostanie przeliczony. Pozostałe wpisy zostają.").setNegativeButton("Zostaw",null).setPositiveButton("Usuń",(dialog,which) -> {db.delete(entry.id);changed();}).show();
        }).show();
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
                picker.getDatePicker().setMinDate(0);picker.getDatePicker().setMaxDate(System.currentTimeMillis()); picker.show();
            }).setPositiveButton("Zapisz",(d,w) -> { db.saveMarks(day,selected); changed(); toast("Zapis dnia zachowany."); }).show();
    }

    private void checkIn() {
        if(db.dayStatus(selectedDay)==2) {
            new AlertDialog.Builder(this).setTitle("W tym dniu jest wpis o kłótni").setMessage("Możesz zapisać dobre momenty tego dnia. Jeśli rozmowa została wpisana przez pomyłkę, popraw lub usuń ją niżej w dzienniku.").setPositiveButton("Rozumiem",null).show();return;
        }
        long id=db.checkInDay(selectedDay);if(id>=0)lastAdded=id;changed();toast("Spokojny dzień zapisany.");
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
        TextView detail = text("Data i godzina. Okoliczności są opcjonalne — możesz zaznaczyć kilka.", 15, MUTED); box.addView(detail);
        Button date = button(format(selected.getTimeInMillis(), "d MMMM yyyy"), false, null);
        Button time = button(format(selected.getTimeInMillis(), "HH:mm"), false, null);
        LinearLayout.LayoutParams gap = new LinearLayout.LayoutParams(-1, -2); gap.topMargin = dp(16); box.addView(date, gap);
        LinearLayout.LayoutParams smallGap = new LinearLayout.LayoutParams(-1, -2); smallGap.topMargin = dp(8); box.addView(time, smallGap);
        date.setOnClickListener(v -> {
            DatePickerDialog picker = new DatePickerDialog(this, (p,y,m,d) -> {
                selected.set(Calendar.YEAR,y); selected.set(Calendar.MONTH,m); selected.set(Calendar.DAY_OF_MONTH,d); date.setText(format(selected.getTimeInMillis(), "d MMMM yyyy"));
            }, selected.get(Calendar.YEAR), selected.get(Calendar.MONTH), selected.get(Calendar.DAY_OF_MONTH));
            picker.getDatePicker().setMinDate(0);picker.getDatePicker().setMaxDate(System.currentTimeMillis()); picker.show();
        });
        time.setOnClickListener(v -> new TimePickerDialog(this, (p,h,m) -> { selected.set(Calendar.HOUR_OF_DAY,h); selected.set(Calendar.MINUTE,m); time.setText(format(selected.getTimeInMillis(), "HH:mm")); }, selected.get(Calendar.HOUR_OF_DAY), selected.get(Calendar.MINUTE), true).show());
        boolean[] contexts=id<0?new boolean[Store.CONTEXT_KINDS.length]:db.contexts(id);
        TextView label=text("Co towarzyszyło tej rozmowie?",16,INK);LinearLayout.LayoutParams labelGap=new LinearLayout.LayoutParams(-1,-2);labelGap.topMargin=dp(16);box.addView(label,labelGap);
        for(int i=0;i<contexts.length;i++) {
            final int index=i;CheckBox choice=new CheckBox(this);choice.setText(Store.CONTEXT_LABELS[i]);choice.setTextSize(16);choice.setTextColor(INK);choice.setMinHeight(dp(48));choice.setChecked(contexts[i]);choice.setOnCheckedChangeListener((view,checked) -> contexts[index]=checked);box.addView(choice);
        }
        ScrollView editorScroll = new ScrollView(this); editorScroll.addView(box);
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(id < 0 ? "Zapisz kłótnię" : "Popraw rozmowę").setView(editorScroll)
            .setNegativeButton("Anuluj", null).setPositiveButton("Zapisz", null).create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            long chosen = selected.getTimeInMillis();
            if (chosen > System.currentTimeMillis()) { toast("Wybierz godzinę, która już minęła."); return; }
            if (id < 0) lastAdded = db.addConflict(chosen,contexts); else db.editConflict(id, chosen,contexts);
            selectedDay=Store.dayKey(chosen);dialog.dismiss(); changed(); toast("Rozmowa zapisana. Dobre momenty zostają.");
        })); dialog.show();
    }
    private void showHistory() {
        setup("history","Historia");
        add(text("Wybierz dzień, żeby obejrzeć lub uzupełnić wpisy.",14,MUTED),8);
        add(button("Wybierz datę",false,v -> pickDay(selectedDay,this::openDay)),18);
        TreeSet<String> days=new TreeSet<>(Collections.reverseOrder());
        days.add(LocalDate.now().toString());days.add(LocalDate.now().minusDays(1).toString());
        for(Store.Entry entry:db.history())days.add(entry.day==null?Store.dayKey(entry.time):entry.day);
        for(Store.Gratitude note:db.gratitudeHistory())days.add(note.day);
        for(Store.Mark mark:db.markHistory())days.add(mark.day);
        for(Store.Done item:db.doneHistory())days.add(item.day);
        int shown=0;
        for(String day:days) {
            if(shown++>=historyLimit)break;
            int status=db.dayStatus(day);String label=status==1?"Spokojny dzień":status==2?"Zapis rozmowy":"Bez zapisu spokoju";
            int count=db.done(day).size();if(count>0)label+=" · Zrobione: "+count;
            if(!db.gratitude(day).isEmpty())label+=" · Wdzięczność";
            if(!markSummary(day).isEmpty())label+=" · Małe kroki";
            Button row=quiet(formatDay(day)+"  ›\n"+label,v -> openDay(day));row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.setTextSize(15);add(row,14);
        }
        if(days.size()>historyLimit)add(button("Pokaż starsze dni",false,v -> {historyLimit+=40;showHistory();}),16);
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
        add(text("Przytrzymaj wolne miejsce na ekranie telefonu → Widgety → Spokojniej. Przycisk „Potrzebuję chwili” od razu uruchamia odliczanie. „Otwórz dziennik” prowadzi do wpisów. Zwykłe otwarcie aplikacji pokazuje dziennik.", 15, MUTED), 8);
        if (Build.VERSION.SDK_INT >= 26 && android.appwidget.AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported()) {
            add(button("Dodaj widget", false, v -> android.appwidget.AppWidgetManager.getInstance(this).requestPinAppWidget(new ComponentName(this, CalmWidget.class), null, null)), 14);
        }
        line();
        add(text("Przerwa i powrót do rozmowy",20,INK),22);
        add(text("W spokojnym momencie uzgodnij z bliskimi zdanie: „Potrzebuję chwili. Wrócę o…”. Powiedz, o której wrócisz, i wróć do rozmowy. Jeśli potrzebujesz więcej czasu, uprzedź o tym. Przycisk jest pomocą; możesz użyć tego zdania bez telefonu.",14,MUTED),8);
        line();
        add(text("Twoje dane", 20, INK), 22);
        add(text("Historia, lista Zrobione, małe kroki i wdzięczność zostają w Twoim telefonie. Przed zmianą telefonu zapisz kopię i wczytaj ją na nowym urządzeniu.", 14, MUTED), 8);
        add(button("Zapisz kopię",false,v -> createBackup()),14);
        add(button("Wczytaj kopię",false,v -> openBackup()),10);
        add(text("Sam wybierasz miejsce zapisu pliku. Wczytanie dodaje brakujące wpisy. Jeśli wdzięczność lub ten sam wpis Zrobione już istnieją, zachowa obecny tekst. Okoliczności rozmów uzupełni o brakujące oznaczenia. Na nowym telefonie odtworzy też ustawienia przypomnień; nadal trzeba zezwolić na powiadomienia i dodać widget.",14,MUTED),12);
        add(text("Odinstalowanie aplikacji usuwa dane z telefonu. Zachowaj kopię poza aplikacją. Nie ma automatycznego wysyłania ani synchronizacji.",14,MUTED),12);
        add(text("Licznik mierzy pełne 24 godziny od ostatniej kłótni, a przed pierwszym wpisem — od uruchomienia aplikacji. Brak wpisu nie jest potwierdzeniem spokojnego dnia. Widget odświeża się okresowo i po zapisaniu wpisu.", 14, MUTED), 16);
        add(text("Spokojniej 1.3.0 · Android 8 lub nowszy", 12, MUTED), 24);
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
                    backupDone(() -> new AlertDialog.Builder(this).setTitle("Kopia zapisana").setMessage("Plik zawiera historię z okolicznościami, listę Zrobione, małe kroki, wpisy wdzięczności i ustawienia przypomnień. Przenieś go na nowy telefon i wybierz Wczytaj kopię.").setPositiveButton("Gotowe",null).show());
                } else {
                    Backup.Data backup;
                    try (InputStream in=getContentResolver().openInputStream(uri)) { backup=Backup.read(in); }
                    backupDone(() -> new AlertDialog.Builder(this).setTitle("Wczytać tę kopię?")
                        .setMessage("Wpisy historii: "+backup.eventCount()+"\nDni z wdzięcznością: "+backup.gratitudeCount()+"\nLista Zrobione: "+backup.doneCount()+"\nZapisy małych kroków i trudności: "+backup.markCount()+"\n\nDoda brakujące wpisy i zachowa obecne. Wdzięczność na ten sam dzień zachowa z telefonu. Ustawienia przypomnień odtworzy na telefonie bez wcześniejszych wpisów.")
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
                    String message="Dodano wpisy historii: "+restored.events+"\nDodano dni z wdzięcznością: "+restored.gratitude+"\nDodano do listy Zrobione: "+restored.done+"\nDodano okoliczności rozmów: "+restored.contexts+"\nDodano zapisy małych kroków i trudności: "+restored.marks;
                    if(restored.keptDone>0)message+="\nZachowano obecne wpisy Zrobione: "+restored.keptDone;
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
        dialog.setOnDismissListener(d -> { handler.removeCallbacks(tick); if (pauseDialog==dialog) {pauseDialog=null;if(!isDestroyed() && !isFinishing() && "home".equals(screen))refreshHome();} });
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
