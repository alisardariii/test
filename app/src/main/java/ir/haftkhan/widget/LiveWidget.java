package ir.haftkhan.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.SystemClock;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LiveWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "ir.haftkhan.widget.REFRESH";
    static final String PREFS = "cfg";

    @Override
    public void onUpdate(Context ctx, AppWidgetManager mgr, int[] ids) {
        schedule(ctx);
        refreshAsync(ctx);
    }

    @Override
    public void onEnabled(Context ctx) { schedule(ctx); }

    @Override
    public void onDisabled(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(refreshIntent(ctx));
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        super.onReceive(ctx, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) refreshAsync(ctx);
    }

    static PendingIntent refreshIntent(Context ctx) {
        Intent i = new Intent(ctx, LiveWidget.class).setAction(ACTION_REFRESH);
        return PendingIntent.getBroadcast(ctx, 1, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void schedule(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME,
                SystemClock.elapsedRealtime() + AlarmManager.INTERVAL_FIFTEEN_MINUTES,
                AlarmManager.INTERVAL_FIFTEEN_MINUTES, refreshIntent(ctx));
    }

    static String jsonUrl(Context ctx) {
        String u = ctx.getSharedPreferences(PREFS, 0).getString("url", "").trim();
        if (u.isEmpty()) return "";
        if (u.endsWith("/mini")) u = u.substring(0, u.length() - 5);
        if (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        if (!u.endsWith(".json")) u = u + ".json";
        return u;
    }

    static String fetch(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(15000);
        c.setUseCaches(false);
        try {
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }

    static String fa(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toCharArray()) b.append(ch >= '0' && ch <= '9' ? (char) ('۰' + (ch - '0')) : ch);
        return b.toString();
    }

    void refreshAsync(final Context ctx) {
        final PendingResult pr = goAsync();
        final Context app = ctx.getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                try { refreshNow(app); } finally { if (pr != null) pr.finish(); }
            }
        }).start();
    }

    static void refreshNow(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
        String url = jsonUrl(ctx);
        String err = null;
        if (url.isEmpty()) {
            err = "برای تنظیم، اپ «شب زنده هفت‌خوان» را باز کنید";
        } else {
            try {
                String body = fetch(url);
                new JSONObject(body); // اطمینان از درست بودن داده
                p.edit().putString("last", body)
                        .putString("last_at", new SimpleDateFormat("HH:mm", Locale.US).format(new Date())).apply();
            } catch (Exception e) {
                err = "به سیستم وصل نشد";
            }
        }
        render(ctx, err);
    }

    static void render(Context ctx, String err) {
        SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.widget);
        String last = p.getString("last", null);
        if (last != null) {
            try {
                JSONObject x = new JSONObject(last);
                v.setTextViewText(R.id.title, "\u200Fهفت‌خوان\u200F · \u200F" + x.optString("now_t", ""));
                v.setTextViewText(R.id.amount, x.optString("today_t", "—"));
                v.setTextViewText(R.id.pct, "\u200F" + x.optString("pct_t", ""));
                try { v.setTextColor(R.id.pct, Color.parseColor(x.optString("pct_color", "#A9B8B1"))); } catch (Exception ignored) {}
                String R2 = "\u200F";
                v.setTextViewText(R.id.info, R2 + "فیش\u00A0" + x.optString("orders_t", "") + R2 + "  ·  " + R2 + "میز\u00A0باز\u00A0" + x.optString("open_t", "") + R2);
            } catch (Exception ignored) {}
        } else if (err != null) {
            v.setTextViewText(R.id.info, err);
        }
        String at = p.getString("last_at", "");
        String st = err != null && last != null ? err + " — آخرین داده " + fa(at) : (at.isEmpty() ? "" : "به‌روزرسانی " + fa(at));
        v.setTextViewText(R.id.status, st);

        String u = jsonUrl(ctx);
        Intent open = u.isEmpty() ? new Intent(ctx, MainActivity.class)
                : new Intent(Intent.ACTION_VIEW, Uri.parse(u.substring(0, u.length() - 5)));
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(ctx, 2, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        v.setOnClickPendingIntent(R.id.refresh, refreshIntent(ctx));

        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        m.updateAppWidget(new ComponentName(ctx, LiveWidget.class), v);
    }
}
