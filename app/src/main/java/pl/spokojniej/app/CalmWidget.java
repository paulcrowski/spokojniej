package pl.spokojniej.app;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.widget.RemoteViews;

public final class CalmWidget extends AppWidgetProvider {
    @Override public void onUpdate(Context c, AppWidgetManager manager, int[] ids) { updateAll(c); ReminderReceiver.scheduleWidget(c); }
    public static void updateAll(Context c) {
        AppWidgetManager manager = AppWidgetManager.getInstance(c);
        int[] ids = manager.getAppWidgetIds(new ComponentName(c, CalmWidget.class));
        if (ids.length == 0) return;
        try (Store db = new Store(c)) {
            for (int id : ids) {
                RemoteViews views = new RemoteViews(c.getPackageName(), R.layout.widget);
                views.setTextViewText(R.id.widget_pause, Pause.active(c) ? "Przerwa do " + Pause.returnTime(c) : "Potrzebuję chwili");
                views.setTextViewText(R.id.widget_status, "Moje małe kroki i wdzięczność  ›");
                views.setOnClickPendingIntent(R.id.widget_pause, PendingIntent.getActivity(c,51,new Intent(c,MainActivity.class).setAction("pause"),PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                views.setOnClickPendingIntent(R.id.widget_root, PendingIntent.getActivity(c, 50, new Intent(c, MainActivity.class).setAction("home"), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
                manager.updateAppWidget(id, views);
            }
        }
    }
}
