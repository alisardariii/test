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
import android.os.Bundle;
import android.os.SystemClock;
import android.util.TypedValue;
import android.widget.RemoteViews;

import org.json.JSONArray;
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
    public void onAppWidgetOptionsChanged(Context ctx, AppWidgetManager mgr, int id, Bundle opts) {
        render(ctx, null);
    }

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

    // رنگ ویجت بر اساس تعداد میزهای باز: آبی (خالی) ← سبز ← زرد ← قرمز (۱۵ میز و بیشتر)، آرام و پیوسته
    static int tableColor(int n) {
        float t = Math.max(0f, Math.min(1f, n / 15f));
        float hue = 210f * (1f - t);
        return Color.HSVToColor(new float[]{hue, 0.72f, 0.46f});
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
            err = "برای تنظیم، اپ «رادرس» را باز کنید";
        } else {
            try {
                String body = fetch(url);
                new JSONObject(body); // اطمینان از درست بودن داده
                p.edit().putString("last", body).putBoolean("offline", false)
                        .putString("last_at", new SimpleDateFormat("HH:mm", Locale.US).format(new Date())).apply();
            } catch (Exception e) {
                err = "به سیستم وصل نشد";
                p.edit().putBoolean("offline", true).apply();
            }
        }
        render(ctx, err);
    }

    static void render(Context ctx, String err) {
        AppWidgetManager m = AppWidgetManager.getInstance(ctx);
        int[] ids = m.getAppWidgetIds(new ComponentName(ctx, LiveWidget.class));
        for (int id : ids) {
            try { m.updateAppWidget(id, build(ctx, err, m.getAppWidgetOptions(id))); } catch (Exception ignored) {}
        }
    }

    static String sep(long n) {
        return fa(String.format(Locale.US, "%,d", n)).replace(',', '٬');
    }

    // شکل ویجت: ۰ = مربعی (فقط بلوک اصلی)، ۱ = مستطیل افقی، ۲ = مستطیل عمودی
    static RemoteViews build(Context ctx, String err, Bundle o) {
        int w = 0, h = 0;
        if (o != null) {
            w = Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0));
            h = Math.max(o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0), o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0));
        }
        if (w <= 0) w = 150;
        if (h <= 0) h = 150;
        int shape = w >= h * 1.45f ? 1 : (h >= w * 1.35f ? 2 : 0);
        RemoteViews v = new RemoteViews(ctx.getPackageName(), shape == 1 ? R.layout.widget_wide : R.layout.widget);
        v.setViewVisibility(R.id.blk_tbl, shape == 0 ? android.view.View.GONE : android.view.View.VISIBLE);

        SharedPreferences p = ctx.getSharedPreferences(PREFS, 0);
        String last = p.getString("last", null);
        boolean off = p.getBoolean("offline", false);
        v.setInt(R.id.bgfill, "setColorFilter", tableColor(0));
        if (last != null) {
            try {
                JSONObject x = new JSONObject(last);
                v.setTextViewText(R.id.title, "\u200Fهفت‌خوان\u200F · \u200F" + x.optString("now_t", ""));
                v.setTextViewText(R.id.amount, x.optString("today_t", "—"));
                v.setTextViewText(R.id.pct, "\u200F" + x.optString("pct_t", ""));
                if (!off) v.setInt(R.id.bgfill, "setColorFilter", tableColor(x.optInt("open_now", 0)));
                String R2 = "\u200F";
                v.setTextViewText(R.id.info, R2 + "فیش\u00A0" + x.optString("orders_t", "") + R2 + "  ·  " + R2 + "میز\u00A0باز\u00A0" + x.optString("open_t", "") + R2);
                // قدیمی‌ترین میز باز سالن (کمتر از ۶ ساعت)
                JSONArray open = x.optJSONArray("open");
                JSONObject best = null;
                if (open != null) for (int k = 0; k < open.length(); k++) {
                    JSONObject t = open.optJSONObject(k);
                    if (t == null || !t.optBoolean("indoor") || t.optInt("minutes") > 360) continue;
                    if (best == null || t.optInt("minutes") > best.optInt("minutes")) best = t;
                }
                if (best != null) {
                    v.setTextViewText(R.id.t_place, "\u200F" + fa(best.optString("place", "")));
                    v.setTextViewText(R.id.t_min, "\u200F⏱ " + fa(String.valueOf(best.optInt("minutes"))) + " دقیقه");
                    v.setTextViewText(R.id.t_amt, "\u200F" + sep(Math.round(best.optDouble("amount", 0) / 10)) + " تومان");
                } else {
                    v.setTextViewText(R.id.t_place, "میز بازی نیست");
                    v.setTextViewText(R.id.t_min, "");
                    v.setTextViewText(R.id.t_amt, "");
                }
            } catch (Exception ignored) {}
        } else if (err != null) {
            v.setTextViewText(R.id.info, err);
        }
        if (off) v.setInt(R.id.bgfill, "setColorFilter", 0x40FFFFFF); // حالت شیشه‌ای وقتی ارتباط قطع است
        v.setViewVisibility(R.id.offline, off ? android.view.View.VISIBLE : android.view.View.GONE);
        String at = p.getString("last_at", "");
        v.setTextViewText(R.id.status, off && last != null ? "قطع — آخرین داده " + fa(at) : (at.isEmpty() ? "" : "به‌روزرسانی " + fa(at)));

        String u = jsonUrl(ctx);
        Intent openI = u.isEmpty() ? new Intent(ctx, MainActivity.class)
                : new Intent(Intent.ACTION_VIEW, Uri.parse(u.substring(0, u.length() - 5)));
        openI.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean tapRefresh = "refresh".equals(p.getString("tap", "open")) && !u.isEmpty();
        v.setOnClickPendingIntent(R.id.root, tapRefresh ? refreshIntent(ctx) : PendingIntent.getActivity(ctx, 2, openI,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        v.setOnClickPendingIntent(R.id.refresh, refreshIntent(ctx));

        // اندازه‌ی متن‌ها بر اساس اندازه‌ی بلوک اصلی
        float mw = shape == 1 ? w * 0.55f : w, mh = shape == 2 ? h * 0.6f : h;
        // ارتفاع لازم برای ۶ خط متن + فاصله‌ها و کادرها حدود ۱۸۵ واحد است (در s=1)
        float s = Math.min(mw / 175f, mh / 185f);
        if (s < 0.8f) s = 0.8f;
        if (s > 5f) s = 5f;
        int U = TypedValue.COMPLEX_UNIT_DIP;
        v.setTextViewTextSize(R.id.refresh, U, 15f * s);
        v.setTextViewTextSize(R.id.title, U, 11f * s);
        v.setTextViewTextSize(R.id.amount, U, Math.min(24f * s, (mw - 36f * Math.min(s, 3f)) / 6.8f));
        v.setTextViewTextSize(R.id.unit, U, 10f * s);
        v.setTextViewTextSize(R.id.pct, U, 11f * s);
        v.setTextViewTextSize(R.id.info, U, 11f * s);
        v.setTextViewTextSize(R.id.status, U, 9f * s);
        v.setTextViewTextSize(R.id.offline, U, 10f * s);
        float tw = shape == 1 ? w * 0.42f : w, th = shape == 2 ? h * 0.38f : h;
        float ts = Math.max(0.8f, Math.min(5f, Math.min(tw / 150f, th / 140f)));
        v.setTextViewTextSize(R.id.t_title, U, 11f * ts);
        v.setTextViewTextSize(R.id.t_place, U, 20f * ts);
        v.setTextViewTextSize(R.id.t_min, U, 15f * ts);
        v.setTextViewTextSize(R.id.t_amt, U, 11f * ts);
        float d = ctx.getResources().getDisplayMetrics().density;
        int pad = (int) (7f * Math.min(s, 3f) * d);
        v.setViewPadding(R.id.content, pad, pad, pad, pad);
        int bp = (int) (9f * Math.min(s, 3f) * d);
        v.setViewPadding(R.id.blk_main, bp, bp, bp, bp);
        v.setViewPadding(R.id.blk_tbl, bp, bp, bp, bp);
        return v;
    }
}
